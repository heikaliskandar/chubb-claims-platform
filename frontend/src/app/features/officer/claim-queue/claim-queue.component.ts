import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { switchMap } from 'rxjs';
import { AuthService } from '../../../core/auth.service';
import { ClaimsApiService, QueueFilter } from '../../../core/claims-api.service';
import { ClaimSummary, PageResponse } from '../../../core/models';
import { ClaimTableComponent } from '../../../shared/claim-table/claim-table.component';
import { poll } from '../../../shared/polling';

type View = 'unassigned' | 'mine' | 'approvals' | 'all';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-claim-queue',
  imports: [MatButtonToggleModule, MatButtonModule, ClaimTableComponent],
  template: `
    <h1>Claims</h1>
    <mat-button-toggle-group [value]="view()" (change)="show($event.value)" aria-label="Queue">
      @if (isOfficer()) {
        <mat-button-toggle value="unassigned">Unassigned</mat-button-toggle>
        <mat-button-toggle value="mine">My claims</mat-button-toggle>
      }
      <mat-button-toggle value="approvals">Awaiting approval</mat-button-toggle>
      <mat-button-toggle value="all">All claims</mat-button-toggle>
    </mat-button-toggle-group>

    @if (result(); as page) {
      <app-claim-table [claims]="page.content" />
      <div class="pager">
        <span
          >{{ page.totalElements }} claims · page {{ page.page + 1 }} of
          {{ page.totalPages || 1 }}</span
        >
        <button mat-button [disabled]="page.page === 0" (click)="pageIndex.set(page.page - 1)">
          Previous
        </button>
        <button
          mat-button
          [disabled]="page.page + 1 >= page.totalPages"
          (click)="pageIndex.set(page.page + 1)"
        >
          Next
        </button>
      </div>
    }
  `,
  styles: `
    mat-button-toggle-group {
      margin-bottom: 16px;
    }
    .pager {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 8px;
      margin-top: 8px;
    }
  `,
})
export class ClaimQueueComponent {
  private readonly api = inject(ClaimsApiService);
  private readonly auth = inject(AuthService);

  protected readonly isOfficer = computed(() => this.auth.user()?.role === 'OFFICER');
  protected readonly view = signal<View>(this.isOfficer() ? 'unassigned' : 'approvals');
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
