import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TextareaModule } from 'primeng/textarea';
import { ClaimsApiService } from '../../../core/claims-api.service';
import { ClaimType, Market } from '../../../core/models';
import { PageHeaderComponent } from '../../../layout/page-header/page-header.component';
import { MARKETS } from '../../../shared/markets';

@Component({
  selector: 'app-submit-claim',
  imports: [
    ReactiveFormsModule,
    ButtonModule,
    DatePickerModule,
    InputNumberModule,
    InputTextModule,
    SelectModule,
    SelectButtonModule,
    TextareaModule,
    PageHeaderComponent,
  ],
  templateUrl: './submit-claim.component.html',
})
export class SubmitClaimComponent {
  private readonly api = inject(ClaimsApiService);
  protected readonly router = inject(Router);
  private readonly idempotencyKey = crypto.randomUUID();

  protected readonly markets = MARKETS;
  protected readonly claimTypes = [
    { label: 'Motor', value: 'MOTOR' as ClaimType, icon: 'pi pi-car' },
    { label: 'Property', value: 'PROPERTY' as ClaimType, icon: 'pi pi-home' },
  ];
  protected readonly today = new Date();
  protected readonly busy = signal(false);
  protected readonly form = inject(FormBuilder).group({
    claimType: ['MOTOR' as ClaimType, Validators.required],
    market: ['SG' as Market, Validators.required],
    policyNumber: ['', [Validators.required, Validators.maxLength(50)]],
    incidentDate: [this.today, Validators.required],
    claimedAmount: [null as number | null, [Validators.required, Validators.min(0.01)]],
    incidentDescription: ['', [Validators.required, Validators.maxLength(2000)]],
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
      .submit(
        {
          policyNumber: value.policyNumber!,
          market: value.market!,
          claimType: value.claimType!,
          incidentDate: toIsoDate(value.incidentDate!),
          incidentDescription: value.incidentDescription!,
          claimedAmount: value.claimedAmount!,
        },
        this.idempotencyKey,
      )
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
