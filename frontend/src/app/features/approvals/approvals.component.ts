import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { filter, switchMap } from 'rxjs';
import { ClaimsApiService } from '../../core/claims-api.service';
import { EmployeesApiService } from '../../core/employees-api.service';
import { Approval, ApprovalStatus, ApprovalView, PageResponse } from '../../core/models';
import { PageHeaderComponent } from '../../layout/page-header/page-header.component';
import { LabelPipe } from '../../shared/label.pipe';
import { marketName } from '../../shared/markets';
import { PagerComponent } from '../../shared/pager/pager.component';
import { poll } from '../../shared/polling';
import { UserAvatarComponent } from '../../shared/user-avatar/user-avatar.component';

const TABS: { value: ApprovalView; label: string; icon: string; approverOnly: boolean }[] = [
  { value: 'TO_REVIEW', label: 'To review', icon: 'how_to_reg', approverOnly: true },
  { value: 'DECIDED', label: 'Decided', icon: 'check_circle', approverOnly: true },
  { value: 'MY_REFERRALS', label: 'My referrals', icon: 'send', approverOnly: false },
];

const STATUS_TONE: Record<ApprovalStatus, string> = {
  PENDING: 'tag-warn',
  APPROVED: 'tag-success',
  RETURNED: 'tag-info',
  REJECTED: 'tag-danger',
};

const PAGE_SIZE = 10;

@Component({
  selector: 'app-approvals',
  imports: [
    CurrencyPipe,
    DatePipe,
    LabelPipe,
    PageHeaderComponent,
    PagerComponent,
    UserAvatarComponent,
  ],
  templateUrl: './approvals.component.html',
  styleUrl: './approvals.component.scss',
})
export class ApprovalsComponent {
  private readonly api = inject(ClaimsApiService);
  private readonly router = inject(Router);

  protected readonly pageSize = PAGE_SIZE;
  protected readonly marketName = marketName;
  protected readonly statusTone = STATUS_TONE;
  protected readonly isApprover = signal<boolean | null>(null);
  protected readonly tabs = computed(() =>
    TABS.filter((tab) => this.isApprover() || !tab.approverOnly),
  );
  protected readonly view = signal<ApprovalView | null>(null);
  protected readonly pageIndex = signal(0);
  protected readonly result = signal<PageResponse<Approval> | null>(null);

  constructor() {
    inject(EmployeesApiService)
      .profile('me')
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (me) => this.start(me.totalReports > 0),
        error: () => this.start(false),
      });

    toObservable(computed(() => ({ view: this.view(), page: this.pageIndex() })))
      .pipe(
        filter((state): state is { view: ApprovalView; page: number } => state.view !== null),
        switchMap(({ view, page }) => poll(() => this.api.approvals(view, page, PAGE_SIZE))),
        takeUntilDestroyed(),
      )
      .subscribe((result) => this.result.set(result));
  }

  protected show(view: ApprovalView): void {
    if (view === this.view()) {
      return;
    }
    this.result.set(null);
    this.pageIndex.set(0);
    this.view.set(view);
  }

  protected open(approval: Approval): void {
    this.router.navigate(['/claims', approval.claimId]);
  }

  private start(isApprover: boolean): void {
    this.isApprover.set(isApprover);
    this.view.set(isApprover ? 'TO_REVIEW' : 'MY_REFERRALS');
  }
}
