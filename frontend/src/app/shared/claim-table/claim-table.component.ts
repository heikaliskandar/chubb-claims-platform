import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ClaimSummary } from '../../core/models';
import { LabelPipe } from '../label.pipe';
import { StatusChipComponent } from '../status-chip/status-chip.component';

@Component({
  selector: 'app-claim-table',
  imports: [RouterLink, CurrencyPipe, DatePipe, LabelPipe, StatusChipComponent],
  template: `
    @if (claims().length === 0) {
      <p class="empty">No claims to show.</p>
    } @else {
      <table>
        <thead>
          <tr>
            <th>Claim</th>
            <th>Market</th>
            <th>Type</th>
            <th>Status</th>
            <th>Waiting on</th>
            <th class="amount">Claimed</th>
            <th>Officer</th>
            <th>Submitted</th>
          </tr>
        </thead>
        <tbody>
          @for (claim of claims(); track claim.id) {
            <tr [routerLink]="['/claims', claim.id]">
              <td>
                <a [routerLink]="['/claims', claim.id]">{{ claim.claimNumber }}</a>
              </td>
              <td>{{ claim.market }}</td>
              <td>{{ claim.claimType | label }}</td>
              <td><app-status-chip [status]="claim.status" /></td>
              <td>{{ claim.waitingOn | label }}</td>
              <td class="amount">{{ claim.claimedAmount | currency: claim.currency : 'code' }}</td>
              <td>{{ claim.assignedOfficerId ?? '—' }}</td>
              <td>{{ claim.createdAt | date: 'mediumDate' }}</td>
            </tr>
          }
        </tbody>
      </table>
    }
  `,
  styles: `
    table {
      width: 100%;
      border-collapse: collapse;
      background: var(--mat-sys-surface-container-lowest);
    }
    th,
    td {
      text-align: left;
      padding: 10px 12px;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    tbody tr {
      cursor: pointer;
    }
    tbody tr:hover {
      background: var(--mat-sys-surface-container);
    }
    .amount {
      text-align: right;
    }
    .empty {
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class ClaimTableComponent {
  readonly claims = input.required<ClaimSummary[]>();
}
