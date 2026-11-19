import { Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';

export interface Crumb {
  label: string;
  routerLink?: string;
}

@Component({
  selector: 'app-page-header',
  imports: [RouterLink],
  template: `
    <div class="page-header">
      <div class="titles">
        <nav class="crumbs" aria-label="Breadcrumb">
          <a [routerLink]="home()" aria-label="Home"
            ><span class="ms" aria-hidden="true">home</span></a
          >
          @for (crumb of crumbs(); track $index) {
            <span class="ms sep" aria-hidden="true">chevron_right</span>
            @if (crumb.routerLink) {
              <a [routerLink]="crumb.routerLink">{{ crumb.label }}</a>
            } @else {
              <span aria-current="page">{{ crumb.label }}</span>
            }
          }
        </nav>
        <h1>{{ title() }}</h1>
      </div>
      <div class="actions">
        <ng-content />
      </div>
    </div>
  `,
  styles: `
    .page-header {
      display: flex;
      align-items: flex-end;
      justify-content: space-between;
      gap: 16px;
      flex-wrap: wrap;
      margin-bottom: 18px;
    }
    .titles {
      min-width: 0;
    }
    .crumbs {
      display: flex;
      align-items: center;
      gap: 4px;
      flex-wrap: wrap;
      color: var(--app-muted);
      font-size: 13px;
    }
    .crumbs a {
      display: inline-flex;
      color: var(--app-muted);
    }
    .crumbs a:hover {
      color: var(--app-primary);
    }
    .crumbs .ms {
      font-size: 17px;
    }
    .crumbs .sep {
      font-size: 16px;
      opacity: 0.6;
    }
    [aria-current='page'] {
      color: var(--app-text);
      font-weight: 600;
    }
    h1 {
      margin-top: 6px;
      font-size: clamp(20px, 2.2vw, 26px);
      font-weight: 700;
      overflow-wrap: anywhere;
    }
    .actions {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
    }
    @media (max-width: 640px) {
      .page-header {
        align-items: stretch;
        flex-direction: column;
        margin-bottom: 18px;
      }
    }
  `,
})
export class PageHeaderComponent {
  private readonly auth = inject(AuthService);

  readonly title = input.required<string>();
  readonly crumbs = input<Crumb[]>([]);

  protected readonly home = computed(() => {
    const role = this.auth.user()?.role;
    return role ? this.auth.homeFor(role) : '/login';
  });
}
