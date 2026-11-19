import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ClaimsApiService } from '../../../core/claims-api.service';
import { ClaimType, Market } from '../../../core/models';
import { PageHeaderComponent } from '../../../layout/page-header/page-header.component';
import { MARKETS } from '../../../shared/markets';

@Component({
  selector: 'app-submit-claim',
  imports: [ReactiveFormsModule, RouterLink, PageHeaderComponent],
  templateUrl: './submit-claim.component.html',
  styles: `
    .type-options {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 12px;
      max-width: 420px;
    }
    .type-option {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 14px 16px;
      border: 1px solid var(--app-border);
      border-radius: 14px;
      background: var(--app-surface);
      color: var(--app-text);
      font-weight: 600;
      cursor: pointer;
    }
    .type-option .ms {
      font-size: 24px;
      color: var(--app-muted);
    }
    .type-option.selected {
      border-color: var(--app-primary);
      background: var(--app-primary-soft);
      color: var(--app-primary);
    }
    .type-option.selected .ms {
      color: var(--app-primary);
    }
  `,
})
export class SubmitClaimComponent {
  private readonly api = inject(ClaimsApiService);
  private readonly router = inject(Router);
  private readonly idempotencyKey = crypto.randomUUID();

  protected readonly markets = MARKETS;
  protected readonly claimTypes: { value: ClaimType; label: string; icon: string }[] = [
    { value: 'MOTOR', label: 'Motor', icon: 'directions_car' },
    { value: 'PROPERTY', label: 'Property', icon: 'home' },
  ];
  protected readonly today = toIsoDate(new Date());
  protected readonly busy = signal(false);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    claimType: ['MOTOR' as ClaimType, Validators.required],
    market: ['SG' as Market, Validators.required],
    policyNumber: ['', [Validators.required, Validators.maxLength(50)]],
    incidentDate: [this.today, Validators.required],
    claimedAmount: [null as number | null, [Validators.required, Validators.min(0.01)]],
    incidentDescription: ['', [Validators.required, Validators.maxLength(2000)]],
  });

  protected readonly claimType = toSignal(this.form.controls.claimType.valueChanges, {
    initialValue: this.form.controls.claimType.value,
  });
  private readonly market = toSignal(this.form.controls.market.valueChanges, {
    initialValue: this.form.controls.market.value,
  });
  protected readonly currency = computed(
    () => MARKETS.find((option) => option.code === this.market())?.currency ?? 'SGD',
  );

  protected submit(): void {
    const value = this.form.getRawValue();
    this.busy.set(true);
    this.api
      .submit({ ...value, claimedAmount: value.claimedAmount ?? 0 }, this.idempotencyKey)
      .subscribe({
        next: (claim) => this.router.navigate(['/claims', claim.id]),
        error: () => this.busy.set(false),
      });
  }
}

function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}
