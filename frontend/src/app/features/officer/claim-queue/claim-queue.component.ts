import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { SelectButtonModule } from 'primeng/selectbutton';
import { switchMap } from 'rxjs';
import { AuthService } from '../../../core/auth.service';
import { ClaimsApiService, QueueFilter } from '../../../core/claims-api.service';
import { ClaimSummary, PageResponse } from '../../../core/models';
import { PageHeaderComponent } from '../../../layout/page-header/page-header.component';
import { ClaimTableComponent } from '../../../shared/claim-table/claim-table.component';
import { poll } from '../../../shared/polling';

type View = 'unassigned' | 'mine' | 'approvals' | 'all';

const PAGE_SIZE = 10;

@Component({
  selector: 'app-claim-queue',
  imports: [FormsModule, SelectButtonModule, PageHeaderComponent, ClaimTableComponent],
  template: `
    <app-page-header title="Claims" [crumbs]="[{ label: 'Claims' }]" />

    <div class="surface-card">
      <div class="toolbar">
        <p-selectbutton
          [options]="views()"
          [ngModel]="view()"
          (ngModelChange)="show($event)"
          optionLabel="label"
          optionValue="value"
          [allowEmpty]="false"
        />
        <span class="muted small">{{ result()?.totalElements ?? 0 }} claims</span>
      </div>
      <app-claim-table [page]="result()" [rows]="pageSize" (pageChange)="pageIndex.set($event)" />
    </div>
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
  protected readonly views = signal(
    [
      { label: 'Unassigned', value: 'unassigned' as View, officerOnly: true },
      { label: 'Mine', value: 'mine' as View, officerOnly: true },
      { label: 'Awaiting approval', value: 'approvals' as View, officerOnly: false },
      { label: 'All', value: 'all' as View, officerOnly: false },
    ].filter((view) => this.isOfficer || !view.officerOnly),
  );
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
