import { HttpErrorResponse, HttpEventType } from '@angular/common/http';
import { Component, computed, ElementRef, inject, input, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { concatMap, filter, from, switchMap, tap, toArray } from 'rxjs';
import { ClaimsApiService } from '../../../core/claims-api.service';
import {
  ClaimDetail,
  ClaimType,
  DocumentCategory,
  Market,
  ProblemDetail,
} from '../../../core/models';
import { ToastService } from '../../../core/toast.service';
import {
  ACCEPT_ATTRIBUTE,
  CATEGORIES,
  fileProblem,
  formatBytes,
  isImage,
} from '../../../shared/documents';
import { PageHeaderComponent } from '../../../layout/page-header/page-header.component';
import { MARKETS } from '../../../shared/markets';

type FieldName = 'policyNumber' | 'incidentDate' | 'claimedAmount' | 'incidentDescription';

const MAX_POLICY = 50;
const MAX_ATTACHMENTS = 20;
const MAX_DESCRIPTION = 2000;
const AMOUNT_PATTERN = /^\d{1,17}(\.\d{1,2})?$/;

const FIELD_ORDER: FieldName[] = [
  'policyNumber',
  'incidentDate',
  'claimedAmount',
  'incidentDescription',
];

const MESSAGES: Record<FieldName, Record<string, string>> = {
  policyNumber: {
    required: 'Enter your policy number.',
    maxlength: `Policy number must be ${MAX_POLICY} characters or fewer.`,
  },
  incidentDate: {
    required: 'Enter the incident date.',
    future: "Incident date can't be in the future.",
  },
  claimedAmount: {
    required: "Enter the amount you're claiming.",
    format: 'Enter an amount like 1250 or 1250.50, using up to 2 decimal places.',
    positive: 'Amount must be more than 0.',
  },
  incidentDescription: {
    required: 'Describe what happened.',
    maxlength: `Description must be ${MAX_DESCRIPTION} characters or fewer.`,
  },
};

@Component({
  selector: 'app-submit-claim',
  imports: [ReactiveFormsModule, RouterLink, PageHeaderComponent],
  templateUrl: './submit-claim.component.html',
  styleUrl: './submit-claim.component.scss',
})
export class SubmitClaimComponent {
  private readonly api = inject(ClaimsApiService);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly summary = viewChild<ElementRef<HTMLElement>>('summary');
  private readonly idempotencyKey = crypto.randomUUID();

  readonly id = input<string>();
  protected readonly editing = computed(() => !!this.id());
  protected readonly claimNumber = signal<string | null>(null);
  protected readonly locked = signal(false);
  protected readonly title = computed(() =>
    this.editing() ? `Edit ${this.claimNumber() ?? 'claim'}` : 'New claim',
  );

  protected readonly markets = MARKETS;
  protected readonly maxDescription = MAX_DESCRIPTION;
  protected readonly claimTypes: { value: ClaimType; label: string; description: string }[] = [
    { value: 'MOTOR', label: 'Motor', description: 'Vehicle accident, damage or theft' },
    { value: 'PROPERTY', label: 'Property', description: 'Home, contents or business premises' },
  ];
  protected readonly today = toIsoDate(new Date());
  protected readonly busy = signal(false);
  protected readonly submitted = signal(false);
  protected readonly showSummary = signal(false);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    claimType: ['MOTOR' as ClaimType, Validators.required],
    market: ['SG' as Market, Validators.required],
    policyNumber: ['', [notBlank, Validators.maxLength(MAX_POLICY)]],
    incidentDate: [this.today, [Validators.required, notAfter(this.today)]],
    claimedAmount: ['', [notBlank, amount]],
    incidentDescription: ['', [notBlank, Validators.maxLength(MAX_DESCRIPTION)]],
  });

  private readonly status = toSignal(this.form.statusChanges, { initialValue: this.form.status });
  private readonly values = toSignal(this.form.valueChanges, { initialValue: this.form.value });

  protected readonly claimType = computed(() => this.values().claimType);
  protected readonly currency = computed(
    () => MARKETS.find((option) => option.code === this.values().market)?.currency ?? 'SGD',
  );
  protected readonly descriptionLength = computed(
    () => this.values().incidentDescription?.length ?? 0,
  );
  protected readonly problems = computed(() => {
    this.status();
    this.values();
    return FIELD_ORDER.flatMap((field) => {
      const message = this.messageFor(field);
      return message ? [{ id: field, message }] : [];
    });
  });

  protected error(field: FieldName): string | null {
    const control = this.form.controls[field];
    return control.touched || this.submitted() ? this.messageFor(field) : null;
  }

  protected normalisePolicyNumber(): void {
    const control = this.form.controls.policyNumber;
    control.setValue(control.value.trim().toUpperCase());
  }

  protected normaliseAmount(): void {
    const control = this.form.controls.claimedAmount;
    control.setValue(control.value.replace(/[,\s]/g, ''));
  }

  protected focusField(event: Event, id: string): void {
    event.preventDefault();
    this.host.nativeElement.querySelector<HTMLElement>(`#${id}`)?.focus();
  }

  protected readonly accept = ACCEPT_ATTRIBUTE;
  protected readonly documentCategories = CATEGORIES.filter((option) => !option.staffOnly);
  protected readonly formatBytes = formatBytes;
  protected readonly attachments = signal<{ file: File; category: DocumentCategory }[]>([]);
  protected readonly attachmentProblems = signal<string[]>([]);
  protected readonly uploading = signal<{ done: number; total: number } | null>(null);
  private readonly toasts = inject(ToastService);

  protected addFiles(files: FileList | null): void {
    const problems: string[] = [];
    const accepted = Array.from(files ?? []).filter((file) => {
      const problem = fileProblem(file);
      if (problem) {
        problems.push(problem);
      }
      return !problem;
    });
    const room = MAX_ATTACHMENTS - this.attachments().length;
    if (accepted.length > room) {
      problems.push(`You can attach up to ${MAX_ATTACHMENTS} files.`);
    }
    this.attachments.update((list) => [
      ...list,
      ...accepted.slice(0, Math.max(0, room)).map((file) => ({
        file,
        category: (isImage(file.type) ? 'PHOTO' : 'OTHER') as DocumentCategory,
      })),
    ]);
    this.attachmentProblems.set(problems);
  }

  protected setAttachmentCategory(index: number, category: DocumentCategory): void {
    this.attachments.update((list) =>
      list.map((item, position) => (position === index ? { ...item, category } : item)),
    );
  }

  protected removeAttachment(index: number): void {
    this.attachments.update((list) => list.filter((_, position) => position !== index));
  }

  private uploadAttachments(claim: ClaimDetail): void {
    const attachments = this.attachments();
    if (attachments.length === 0) {
      this.router.navigate(['/claims', claim.id]);
      return;
    }
    this.uploading.set({ done: 0, total: attachments.length });
    from(attachments)
      .pipe(
        concatMap(({ file, category }) =>
          this.api.uploadDocument(claim.id, file, category).pipe(
            filter((event) => event.type === HttpEventType.Response),
            tap(() =>
              this.uploading.update((state) => state && { ...state, done: state.done + 1 }),
            ),
          ),
        ),
        toArray(),
      )
      .subscribe({
        next: () => this.router.navigate(['/claims', claim.id]),
        error: () => {
          this.toasts.show(
            'warn',
            'Claim submitted',
            'Some documents could not be uploaded. Add them from the claim page.',
          );
          this.router.navigate(['/claims', claim.id]);
        },
      });
  }

  constructor() {
    toObservable(this.id)
      .pipe(
        filter((id): id is string => !!id),
        switchMap((id) => this.api.get(id)),
        takeUntilDestroyed(),
      )
      .subscribe((claim) => {
        this.claimNumber.set(claim.claimNumber);
        this.locked.set(!claim.allowedActions.includes('EDIT_DETAILS'));
        this.form.patchValue({
          claimType: claim.claimType,
          market: claim.market,
          policyNumber: claim.policyNumber,
          incidentDate: claim.incidentDate,
          claimedAmount: String(claim.claimedAmount),
          incidentDescription: claim.incidentDescription,
        });
        this.form.controls.market.disable();
      });
  }

  protected submit(): void {
    this.normalisePolicyNumber();
    this.normaliseAmount();
    this.submitted.set(true);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.showSummary.set(true);
      setTimeout(() => this.summary()?.nativeElement.focus());
      return;
    }

    const value = this.form.getRawValue();
    const request = {
      ...value,
      policyNumber: value.policyNumber.trim(),
      incidentDescription: value.incidentDescription.trim(),
      claimedAmount: Number(value.claimedAmount),
    };
    this.busy.set(true);
    const id = this.id();
    if (id) {
      this.api.update(id, request).subscribe({
        next: (claim) => {
          this.toasts.show('success', 'Claim updated', claim.claimNumber);
          this.router.navigate(['/claims', id]);
        },
        error: (failure: HttpErrorResponse) => {
          this.busy.set(false);
          this.applyServerErrors(failure.error as ProblemDetail | null);
          if (failure.status === 409) {
            this.locked.set(true);
          }
        },
      });
      return;
    }
    this.api.submit(request, this.idempotencyKey).subscribe({
      next: (claim) => this.uploadAttachments(claim),
      error: (failure: HttpErrorResponse) => {
        this.busy.set(false);
        this.applyServerErrors(failure.error as ProblemDetail | null);
      },
    });
  }

  private messageFor(field: FieldName): string | null {
    const control = this.form.controls[field];
    if (!control.errors) {
      return null;
    }
    const [key] = Object.keys(control.errors);
    return control.errors['server'] ?? MESSAGES[field][key] ?? 'Check this field.';
  }

  private applyServerErrors(problem: ProblemDetail | null): void {
    const errors = problem?.errors;
    if (!errors) {
      return;
    }
    for (const [field, message] of Object.entries(errors)) {
      if ((FIELD_ORDER as string[]).includes(field)) {
        this.form.controls[field as FieldName].setErrors({ server: message });
      }
    }
    this.showSummary.set(true);
  }
}

function notBlank(control: AbstractControl<string>): ValidationErrors | null {
  return control.value?.trim() ? null : { required: true };
}

function amount(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value?.trim();
  if (!value) {
    return null;
  }
  if (!AMOUNT_PATTERN.test(value)) {
    return { format: true };
  }
  return Number(value) > 0 ? null : { positive: true };
}

function notAfter(maxIsoDate: string): ValidatorFn {
  return (control) => (control.value && control.value > maxIsoDate ? { future: true } : null);
}

function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}
