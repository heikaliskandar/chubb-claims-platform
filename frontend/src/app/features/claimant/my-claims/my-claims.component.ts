import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { switchMap } from 'rxjs';
import { ClaimsApiService } from '../../../core/claims-api.service';
import { ClaimSummary, PageResponse } from '../../../core/models';
import { PageHeaderComponent } from '../../../layout/page-header/page-header.component';
import { ClaimTableComponent } from '../../../shared/claim-table/claim-table.component';
import { poll } from '../../../shared/polling';

const PAGE_SIZE = 10;

@Component({
  selector: 'app-my-claims',
  imports: [ButtonModule, PageHeaderComponent, ClaimTableComponent],
  template: `
    <app-page-header title="My claims" [crumbs]="[{ label: 'My claims' }]">
      <p-button
        label="New claim"
        icon="pi pi-plus"
        (onClick)="router.navigate(['/claimant/new'])"
      />
    </app-page-header>

    <div class="surface-card">
      <app-claim-table [page]="result()" [rows]="pageSize" (pageChange)="pageIndex.set($event)" />
    </div>
  `,
})
export class MyClaimsComponent {
  protected readonly router = inject(Router);
  protected readonly pageSize = PAGE_SIZE;
  protected readonly pageIndex = signal(0);
  protected readonly result = signal<PageResponse<ClaimSummary> | null>(null);

  constructor() {
    const api = inject(ClaimsApiService);
    toObservable(this.pageIndex)
      .pipe(
        switchMap((page) => poll(() => api.mine(page, PAGE_SIZE))),
        takeUntilDestroyed(),
      )
      .subscribe((page) => this.result.set(page));
  }
}
