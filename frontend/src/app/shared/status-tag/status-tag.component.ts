import { Component, computed, input } from '@angular/core';
import { TagModule } from 'primeng/tag';
import { ClaimStatus } from '../../core/models';
import { LabelPipe } from '../label.pipe';

type Severity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

const SEVERITY: Record<ClaimStatus, Severity> = {
  SUBMITTED: 'info',
  ASSIGNED: 'secondary',
  UNDER_REVIEW: 'contrast',
  INFO_REQUESTED: 'warn',
  PENDING_APPROVAL: 'warn',
  APPROVED: 'success',
  REJECTED: 'danger',
  SETTLED: 'success',
};

@Component({
  selector: 'app-status-tag',
  imports: [TagModule, LabelPipe],
  template: `<p-tag [value]="status() | label" [severity]="severity()" [rounded]="true" />`,
})
export class StatusTagComponent {
  readonly status = input.required<ClaimStatus>();

  protected readonly severity = computed(() => SEVERITY[this.status()]);
}
