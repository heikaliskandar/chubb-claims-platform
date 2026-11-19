import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { ClaimsApiService } from '../../../core/claims-api.service';
import { ClaimSummary } from '../../../core/models';
import { ClaimTableComponent } from '../../../shared/claim-table/claim-table.component';
import { poll } from '../../../shared/polling';

@Component({
  selector: 'app-my-claims',
  imports: [RouterLink, MatButtonModule, ClaimTableComponent],
  template: `
    <div class="header">
      <h1>My claims</h1>
      <a mat-flat-button routerLink="/claimant/new">Report an incident</a>
    </div>
    @if (claims(); as claims) {
      <app-claim-table [claims]="claims" />
    }
  `,
  styles: `
    .header {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
  `,
})
export class MyClaimsComponent {
  protected readonly claims = signal<ClaimSummary[] | null>(null);

  constructor() {
    const api = inject(ClaimsApiService);
    poll(() => api.mine(0, 50))
      .pipe(takeUntilDestroyed())
      .subscribe((page) => this.claims.set(page.content));
  }
}
