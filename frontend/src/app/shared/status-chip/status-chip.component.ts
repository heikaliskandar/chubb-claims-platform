import { Component, input } from '@angular/core';
import { ClaimStatus } from '../../core/models';
import { LabelPipe } from '../label.pipe';

@Component({
  selector: 'app-status-chip',
  imports: [LabelPipe],
  template: `<span [class]="'chip status-' + status().toLowerCase()">{{ status() | label }}</span>`,
  styles: `
    .chip {
      display: inline-block;
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 12px;
      font-weight: 500;
      background: var(--mat-sys-surface-container-high);
    }
    .status-info_requested,
    .status-pending_approval {
      background: #fff3cd;
      color: #7a5b00;
    }
    .status-approved,
    .status-settled {
      background: #d4edda;
      color: #1e5631;
    }
    .status-rejected {
      background: #f8d7da;
      color: #842029;
    }
  `,
})
export class StatusChipComponent {
  readonly status = input.required<ClaimStatus>();
}
