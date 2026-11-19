import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router } from '@angular/router';
import { ClaimsApiService } from '../../../core/claims-api.service';
import { ClaimType, Market } from '../../../core/models';
import { LabelPipe } from '../../../shared/label.pipe';

const MARKETS: { code: Market; currency: string }[] = [
  { code: 'SG', currency: 'SGD' },
  { code: 'MY', currency: 'MYR' },
  { code: 'HK', currency: 'HKD' },
  { code: 'TH', currency: 'THB' },
  { code: 'ID', currency: 'IDR' },
  { code: 'PH', currency: 'PHP' },
];

@Component({
  selector: 'app-submit-claim',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    LabelPipe,
  ],
  template: `
    <mat-card>
      <mat-card-header>
        <mat-card-title>Report an incident</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <form [formGroup]="form" (ngSubmit)="submit()">
          <mat-form-field appearance="outline">
            <mat-label>Policy number</mat-label>
            <input matInput formControlName="policyNumber" maxlength="50" />
          </mat-form-field>
          <div class="row">
            <mat-form-field appearance="outline">
              <mat-label>Market</mat-label>
              <mat-select formControlName="market">
                @for (market of markets; track market.code) {
                  <mat-option [value]="market.code"
                    >{{ market.code }} ({{ market.currency }})</mat-option
                  >
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Claim type</mat-label>
              <mat-select formControlName="claimType">
                @for (type of claimTypes; track type) {
                  <mat-option [value]="type">{{ type | label }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          </div>
          <div class="row">
            <mat-form-field appearance="outline">
              <mat-label>Incident date</mat-label>
              <input matInput type="date" formControlName="incidentDate" [max]="today" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Claimed amount ({{ currency() }})</mat-label>
              <input
                matInput
                type="number"
                min="0.01"
                step="0.01"
                formControlName="claimedAmount"
              />
            </mat-form-field>
          </div>
          <mat-form-field appearance="outline">
            <mat-label>What happened?</mat-label>
            <textarea
              matInput
              rows="4"
              formControlName="incidentDescription"
              maxlength="2000"
            ></textarea>
          </mat-form-field>
          <button mat-flat-button type="submit" [disabled]="form.invalid || busy()">
            Submit claim
          </button>
        </form>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    form {
      display: flex;
      flex-direction: column;
      gap: 4px;
      margin-top: 16px;
    }
    .row {
      display: flex;
      gap: 16px;
    }
    .row mat-form-field {
      flex: 1;
    }
  `,
})
export class SubmitClaimComponent {
  private readonly api = inject(ClaimsApiService);
  private readonly router = inject(Router);
  private readonly idempotencyKey = crypto.randomUUID();

  protected readonly markets = MARKETS;
  protected readonly claimTypes: ClaimType[] = ['MOTOR', 'PROPERTY'];
  protected readonly today = new Date().toISOString().slice(0, 10);
  protected readonly busy = signal(false);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    policyNumber: ['', [Validators.required, Validators.maxLength(50)]],
    market: ['SG' as Market, Validators.required],
    claimType: ['MOTOR' as ClaimType, Validators.required],
    incidentDate: [this.today, Validators.required],
    incidentDescription: ['', [Validators.required, Validators.maxLength(2000)]],
    claimedAmount: [0, [Validators.required, Validators.min(0.01)]],
  });
  private readonly market = toSignal(this.form.controls.market.valueChanges, {
    initialValue: this.form.controls.market.value,
  });
  protected readonly currency = computed(
    () => MARKETS.find((m) => m.code === this.market())?.currency ?? '',
  );

  protected submit(): void {
    this.busy.set(true);
    this.api.submit(this.form.getRawValue(), this.idempotencyKey).subscribe({
      next: (claim) => this.router.navigate(['/claims', claim.id]),
      error: () => this.busy.set(false),
    });
  }
}
