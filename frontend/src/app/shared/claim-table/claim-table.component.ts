import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, input, output } from '@angular/core';
import { Router } from '@angular/router';
import { ClaimSummary, PageResponse } from '../../core/models';
import { LabelPipe } from '../label.pipe';
import { marketName } from '../markets';
import { PagerComponent } from '../pager/pager.component';
import { StatusTagComponent } from '../status-tag/status-tag.component';
import { UserAvatarComponent } from '../user-avatar/user-avatar.component';

@Component({
  selector: 'app-claim-table',
  imports: [
    CurrencyPipe,
    DatePipe,
    LabelPipe,
    PagerComponent,
    StatusTagComponent,
    UserAvatarComponent,
  ],
  templateUrl: './claim-table.component.html',
  styleUrl: './claim-table.component.scss',
})
export class ClaimTableComponent {
  private readonly router = inject(Router);

  readonly page = input.required<PageResponse<ClaimSummary> | null>();
  readonly rows = input(10);
  readonly pageChange = output<number>();

  protected readonly marketName = marketName;

  protected open(claim: ClaimSummary): void {
    this.router.navigate(['/claims', claim.id]);
  }
}
