import { Component, computed, input } from '@angular/core';
import { ClaimStatus } from '../../core/models';
import { LabelPipe } from '../label.pipe';

const TONE: Record<ClaimStatus, string> = {
  SUBMITTED: 'tag-info',
  ASSIGNED: '',
  UNDER_REVIEW: 'tag-strong',
  INFO_REQUESTED: 'tag-warn',
  PENDING_APPROVAL: 'tag-warn',
  APPROVED: 'tag-success',
  REJECTED: 'tag-danger',
  SETTLED: 'tag-success',
};

@Component({
  selector: 'app-status-tag',
  imports: [LabelPipe],
  template: `<span class="tag" [class]="'tag ' + tone()">{{ status() | label }}</span>`,
})
export class StatusTagComponent {
  readonly status = input.required<ClaimStatus>();

  protected readonly tone = computed(() => TONE[this.status()]);
}
