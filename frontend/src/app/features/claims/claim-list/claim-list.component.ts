import { Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Observable, switchMap } from 'rxjs';
import { AuthService } from '../../../core/auth.service';
import { ClaimsApiService } from '../../../core/claims-api.service';
import { ClaimSummary, PageResponse } from '../../../core/models';
import { PageHeaderComponent } from '../../../layout/page-header/page-header.component';
import { ClaimTableComponent } from '../../../shared/claim-table/claim-table.component';
import { poll } from '../../../shared/polling';

export type ClaimListView = 'queue' | 'assigned' | 'open' | 'history' | 'my-active' | 'my-history';

const TITLES: Record<ClaimListView, string> = {
  queue: 'Claim queue',
  assigned: 'Assigned to me',
  open: 'Open claims',
  history: 'Claim history',
  'my-active': 'My claims',
  'my-history': 'Claim history',
};

const PAGE_SIZE = 10;

@Component({
  selector: 'app-claim-list',
  imports: [RouterLink, PageHeaderComponent, ClaimTableComponent],
  template: `
    <app-page-header [title]="title()" [crumbs]="[{ label: 'Claims' }, { label: title() }]">
      @if (isClaimantView()) {
        <a class="btn btn-primary" routerLink="/claims/new">
          <span class="ms" aria-hidden="true">add</span>
          New claim
        </a>
      }
    </app-page-header>

    @if (actionNeeded().length > 0) {
      <section class="surface-card callout">
        <span class="icon-badge amber"><span class="ms" aria-hidden="true">help</span></span>
        <div>
          <strong>We need more information</strong>
          <span class="muted">Please answer the open question on:</span>
          <span class="links">
            @for (claim of actionNeeded(); track claim.id) {
              <a [routerLink]="['/claims', claim.id]">{{ claim.claimNumber }}</a>
            }
          </span>
        </div>
      </section>
    }

    <section class="surface-card">
      <app-claim-table [page]="result()" [rows]="pageSize" (pageChange)="pageIndex.set($event)" />
    </section>
  `,
  styles: `
    .callout {
      display: flex;
      align-items: center;
      gap: 16px;
      margin-bottom: 20px;
      box-shadow:
        inset 0 0 0 1px color-mix(in srgb, var(--app-warning) 40%, transparent),
        var(--app-shadow);
    }
    .callout > div {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .links {
      display: flex;
      flex-wrap: wrap;
      gap: 4px 14px;
      font-weight: 600;
    }
  `,
})
export class ClaimListComponent {
  private readonly api = inject(ClaimsApiService);
  private readonly auth = inject(AuthService);

  readonly view = input.required<ClaimListView>();

  protected readonly pageSize = PAGE_SIZE;
  protected readonly pageIndex = signal(0);
  protected readonly result = signal<PageResponse<ClaimSummary> | null>(null);
  protected readonly title = computed(() => TITLES[this.view()]);
  protected readonly isClaimantView = computed(() => this.view().startsWith('my-'));
  protected readonly actionNeeded = computed(() =>
    this.view() === 'my-active'
      ? (this.result()?.content ?? []).filter((claim) => claim.waitingOn === 'CLAIMANT')
      : [],
  );

  constructor() {
    toObservable(computed(() => ({ view: this.view(), page: this.pageIndex() })))
      .pipe(
        switchMap(({ view, page }) => poll(() => this.load(view, page))),
        takeUntilDestroyed(),
      )
      .subscribe((result) => this.result.set(result));
  }

  private load(view: ClaimListView, page: number): Observable<PageResponse<ClaimSummary>> {
    const size = PAGE_SIZE;
    switch (view) {
      case 'queue':
        return this.api.queue({ status: 'SUBMITTED', page, size });
      case 'assigned':
        return this.api.queue({
          assignedTo: this.auth.user()?.username,
          closed: false,
          page,
          size,
        });
      case 'open':
        return this.api.queue({ closed: false, page, size });
      case 'history':
        return this.api.queue({ closed: true, page, size });
      case 'my-active':
        return this.api.mine(false, page, size);
      case 'my-history':
        return this.api.mine(true, page, size);
    }
  }
}
