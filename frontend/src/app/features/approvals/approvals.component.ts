import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { filter, switchMap } from 'rxjs';
import { ClaimsApiService } from '../../core/claims-api.service';
import { EmployeesApiService } from '../../core/employees-api.service';
import { Approval, ApprovalStatus, ApprovalView, PageResponse } from '../../core/models';
import { PeopleService } from '../../core/people.service';
import { PageHeaderComponent } from '../../layout/page-header/page-header.component';
import { durationSince } from '../../shared/duration';
import { LabelPipe } from '../../shared/label.pipe';
import { marketName } from '../../shared/markets';
import { PagerComponent } from '../../shared/pager/pager.component';
import { poll } from '../../shared/polling';
import { UserAvatarComponent } from '../../shared/user-avatar/user-avatar.component';

interface Tab {
  value: ApprovalView;
  label: string;
  icon: string;
  approverOnly: boolean;
}

const TABS: Tab[] = [
  { value: 'TO_REVIEW', label: 'Waiting for me', icon: 'how_to_reg', approverOnly: true },
  { value: 'DECIDED', label: 'Decided', icon: 'check_circle', approverOnly: true },
  { value: 'MY_REFERRALS', label: 'Sent by me', icon: 'send', approverOnly: false },
];

const STATUS: Record<ApprovalStatus, { label: string; tone: string; verb: string }> = {
  PENDING: { label: 'Waiting for decision', tone: 'tag-warn', verb: '' },
  APPROVED: { label: 'Approved', tone: 'tag-success', verb: 'Approved' },
  RETURNED: { label: 'Sent back to officer', tone: 'tag-info', verb: 'Sent back' },
  REJECTED: { label: 'Declined', tone: 'tag-danger', verb: 'Declined' },
};

const PAGE_SIZE = 10;

@Component({
  selector: 'app-approvals',
  imports: [
    CurrencyPipe,
    DatePipe,
    LabelPipe,
    RouterLink,
    PageHeaderComponent,
    PagerComponent,
    UserAvatarComponent,
  ],
  templateUrl: './approvals.component.html',
  styleUrl: './approvals.component.scss',
})
export class ApprovalsComponent {
  private readonly api = inject(ClaimsApiService);
  protected readonly people = inject(PeopleService);

  protected readonly pageSize = PAGE_SIZE;
  protected readonly marketName = marketName;
  protected readonly status = STATUS;
  protected readonly since = durationSince;
  protected readonly isApprover = signal<boolean | null>(null);
  protected readonly tabs = computed(() =>
    TABS.filter((tab) => this.isApprover() || !tab.approverOnly),
  );
  protected readonly view = signal<ApprovalView | null>(null);
  protected readonly pageIndex = signal(0);
  protected readonly result = signal<PageResponse<Approval> | null>(null);

  constructor() {
    this.people.load();
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

  private start(isApprover: boolean): void {
    this.isApprover.set(isApprover);
    this.view.set(isApprover ? 'TO_REVIEW' : 'MY_REFERRALS');
  }
}
