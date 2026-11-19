import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs';
import { AuthService } from '../../../core/auth.service';
import { ClaimsApiService, QueueFilter } from '../../../core/claims-api.service';
import { ClaimSummary, PageResponse } from '../../../core/models';
import { PageHeaderComponent } from '../../../layout/page-header/page-header.component';
import { ClaimTableComponent } from '../../../shared/claim-table/claim-table.component';
import { poll } from '../../../shared/polling';

type View = 'unassigned' | 'mine' | 'approvals' | 'all';

const PAGE_SIZE = 10;

const VIEWS: { value: View; label: string; icon: string; officerOnly: boolean }[] = [
  { value: 'unassigned', label: 'Unassigned', icon: 'inbox', officerOnly: true },
  { value: 'mine', label: 'Mine', icon: 'person', officerOnly: true },
  { value: 'approvals', label: 'Awaiting approval', icon: 'how_to_reg', officerOnly: false },
  { value: 'all', label: 'All', icon: 'list', officerOnly: false },
];

@Component({
  selector: 'app-claim-queue',
  imports: [PageHeaderComponent, ClaimTableComponent],
  template: `
    <app-page-header title="Claims" [crumbs]="[{ label: 'Claims' }]" />

    <section class="surface-card">
      <div class="toolbar">
        <div class="segmented" role="tablist" aria-label="Queue">
          @for (option of views; track option.value) {
            <button
              type="button"
              role="tab"
              [class.active]="view() === option.value"
              [attr.aria-selected]="view() === option.value"
              (click)="show(option.value)"
            >
              <span class="ms">{{ option.icon }}</span>
              {{ option.label }}
            </button>
          }
        </div>
        <span class="muted small">{{ result()?.totalElements ?? 0 }} claims</span>
      </div>
      <app-claim-table [page]="result()" [rows]="pageSize" (pageChange)="pageIndex.set($event)" />
    </section>
  `,
  styles: `
    .toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      flex-wrap: wrap;
      margin-bottom: 16px;
    }
  `,
})
export class ClaimQueueComponent {
  private readonly api = inject(ClaimsApiService);
  private readonly auth = inject(AuthService);
  private readonly isOfficer = this.auth.user()?.role === 'OFFICER';

  protected readonly pageSize = PAGE_SIZE;
  protected readonly views = VIEWS.filter((view) => this.isOfficer || !view.officerOnly);
  protected readonly view = signal<View>(this.isOfficer ? 'unassigned' : 'approvals');
  protected readonly pageIndex = signal(0);
  protected readonly result = signal<PageResponse<ClaimSummary> | null>(null);

  constructor() {
    toObservable(computed(() => this.filterFor(this.view(), this.pageIndex())))
      .pipe(
        switchMap((filter) => poll(() => this.api.queue(filter))),
        takeUntilDestroyed(),
      )
      .subscribe((page) => this.result.set(page));
  }

  protected show(view: View): void {
    this.result.set(null);
    this.view.set(view);
    this.pageIndex.set(0);
  }

  private filterFor(view: View, page: number): QueueFilter {
    switch (view) {
      case 'unassigned':
        return { status: 'SUBMITTED', page, size: PAGE_SIZE };
      case 'mine':
        return { assignedTo: this.auth.user()?.username, page, size: PAGE_SIZE };
      case 'approvals':
        return { status: 'PENDING_APPROVAL', page, size: PAGE_SIZE };
      case 'all':
        return { page, size: PAGE_SIZE };
    }
  }
}
