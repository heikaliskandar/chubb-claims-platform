import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, switchMap } from 'rxjs';
import { ClaimsApiService } from '../../../core/claims-api.service';
import { ClaimAction, ClaimDetail } from '../../../core/models';
import { LabelPipe } from '../../../shared/label.pipe';
import { poll } from '../../../shared/polling';
import { StatusChipComponent } from '../../../shared/status-chip/status-chip.component';

interface ActionForm {
  label: string;
  text?: { label: string; optional?: boolean };
  amount?: { label: string; allowZero?: boolean };
}

const ACTIONS: Record<ClaimAction, ActionForm> = {
  ASSIGN_TO_ME: { label: 'Assign to me' },
  START_REVIEW: { label: 'Start review' },
  REQUEST_INFORMATION: {
    label: 'Request information',
    text: { label: 'Question for the claimant' },
  },
  RESPOND_TO_INFORMATION_REQUEST: { label: 'Respond', text: { label: 'Your answer' } },
  ASSESS: {
    label: 'Assess',
    amount: { label: 'Reserve amount', allowZero: true },
    text: { label: 'Notes', optional: true },
  },
  APPROVE: { label: 'Approve', amount: { label: 'Approved amount' }, text: { label: 'Reason' } },
  REJECT: { label: 'Reject', text: { label: 'Reason for rejection' } },
  APPROVE_REFERRAL: { label: 'Approve referral', text: { label: 'Comment', optional: true } },
  RETURN_REFERRAL: { label: 'Return to officer', text: { label: 'What needs to change?' } },
  REJECT_REFERRAL: { label: 'Reject claim', text: { label: 'Reason for rejection' } },
  SETTLE: { label: 'Mark as settled' },
};

@Component({
  selector: 'app-claim-detail',
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    DatePipe,
    MatCardModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    LabelPipe,
    StatusChipComponent,
  ],
  templateUrl: './claim-detail.component.html',
  styleUrl: './claim-detail.component.scss',
})
export class ClaimDetailComponent {
  private readonly api = inject(ClaimsApiService);
  private readonly snackBar = inject(MatSnackBar);

  readonly id = input.required<string>();

  protected readonly actions = ACTIONS;
  protected readonly claim = signal<ClaimDetail | null>(null);
  protected readonly activeAction = signal<ClaimAction | null>(null);
  protected readonly busy = signal(false);
  protected readonly text = new FormControl('', { nonNullable: true });
  protected readonly amount = new FormControl<number | null>(null);
  protected readonly openRequest = computed(() =>
    this.claim()?.informationRequests.find((request) => request.status === 'OPEN'),
  );

  constructor() {
    toObservable(this.id)
      .pipe(
        switchMap((id) => poll(() => this.api.get(id))),
        takeUntilDestroyed(),
      )
      .subscribe((claim) => this.claim.set(claim));
  }

  protected choose(action: ClaimAction): void {
    const form = ACTIONS[action];
    if (!form.text && !form.amount) {
      this.run(action);
      return;
    }
    this.text.reset('');
    this.amount.reset(this.suggestedAmount(action));
    this.activeAction.set(action);
  }

  protected canSubmit(action: ClaimAction): boolean {
    const form = ACTIONS[action];
    const textOk = !form.text || form.text.optional || this.text.value.trim().length > 0;
    const value = this.amount.value;
    const amountOk =
      !form.amount || (value !== null && (form.amount.allowZero ? value >= 0 : value > 0));
    return textOk && amountOk && !this.busy();
  }

  protected run(action: ClaimAction): void {
    const claim = this.claim();
    if (!claim) {
      return;
    }
    this.busy.set(true);
    this.request(action, claim).subscribe({
      next: (updated) => {
        this.claim.set(updated);
        this.activeAction.set(null);
        this.busy.set(false);
        this.snackBar.open(updated.nextStep, 'OK', { duration: 4000 });
      },
      error: () => this.busy.set(false),
    });
  }

  private request(action: ClaimAction, claim: ClaimDetail): Observable<ClaimDetail> {
    const text = this.text.value.trim();
    const amount = this.amount.value ?? 0;
    switch (action) {
      case 'ASSIGN_TO_ME':
        return this.api.assign(claim.id);
      case 'START_REVIEW':
        return this.api.startReview(claim.id);
      case 'REQUEST_INFORMATION':
        return this.api.requestInformation(claim.id, text);
      case 'RESPOND_TO_INFORMATION_REQUEST':
        return this.api.respond(claim.id, this.openRequest()!.id, text);
      case 'ASSESS':
        return this.api.assess(claim.id, amount, text);
      case 'APPROVE':
        return this.api.approve(claim.id, amount, text);
      case 'REJECT':
        return this.api.reject(claim.id, text);
      case 'APPROVE_REFERRAL':
        return this.api.reviewReferral(claim.id, 'APPROVE', text);
      case 'RETURN_REFERRAL':
        return this.api.reviewReferral(claim.id, 'RETURN', text);
      case 'REJECT_REFERRAL':
        return this.api.reviewReferral(claim.id, 'REJECT', text);
      case 'SETTLE':
        return this.api.settle(claim.id);
    }
  }

  private suggestedAmount(action: ClaimAction): number | null {
    const claim = this.claim();
    if (action === 'ASSESS') {
      return claim?.claimedAmount ?? null;
    }
    if (action === 'APPROVE') {
      return claim?.assessments?.at(-1)?.assessedAmount ?? null;
    }
    return null;
  }
}
