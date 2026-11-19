import { Component, computed, inject, input } from '@angular/core';
import { MenuItem } from 'primeng/api';
import { BreadcrumbModule } from 'primeng/breadcrumb';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-page-header',
  imports: [BreadcrumbModule],
  template: `
    <div class="page-header">
      <div>
        <p-breadcrumb [model]="crumbs()" [home]="home()" />
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
      margin-bottom: 24px;
    }
    h1 {
      font-size: 24px;
      font-weight: 700;
      margin-top: 6px;
    }
    .actions {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
    }
  `,
})
export class PageHeaderComponent {
  private readonly auth = inject(AuthService);

  readonly title = input.required<string>();
  readonly crumbs = input<MenuItem[]>([]);

  protected readonly home = computed<MenuItem>(() => {
    const role = this.auth.user()?.role;
    return { icon: 'pi pi-home', routerLink: role ? this.auth.homeFor(role) : '/login' };
  });
}
