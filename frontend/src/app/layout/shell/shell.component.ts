import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { AvatarModule } from 'primeng/avatar';
import { ButtonModule } from 'primeng/button';
import { MenuModule } from 'primeng/menu';
import { AuthService } from '../../core/auth.service';
import { Role } from '../../core/models';
import { LabelPipe } from '../../shared/label.pipe';

interface NavItem {
  label: string;
  icon: string;
  link: string;
}

const NAV_BY_ROLE: Record<Role, NavItem[]> = {
  CLAIMANT: [
    { label: 'My claims', icon: 'pi pi-folder', link: '/claimant' },
    { label: 'New claim', icon: 'pi pi-plus-circle', link: '/claimant/new' },
  ],
  OFFICER: [{ label: 'Claims', icon: 'pi pi-inbox', link: '/officer' }],
  MANAGER: [
    { label: 'Overview', icon: 'pi pi-chart-bar', link: '/manager' },
    { label: 'Claims', icon: 'pi pi-inbox', link: '/officer' },
  ],
};

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    AvatarModule,
    ButtonModule,
    MenuModule,
    LabelPipe,
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly user = this.auth.user;
  protected readonly sidebarOpen = signal(false);
  protected readonly navItems = computed(() => {
    const role = this.user()?.role;
    return role ? NAV_BY_ROLE[role] : [];
  });
  protected readonly initials = computed(() =>
    (this.user()?.username ?? '?').slice(0, 2).toUpperCase(),
  );
  protected readonly userMenu: MenuItem[] = [
    { label: 'Sign out', icon: 'pi pi-sign-out', command: () => this.logout() },
  ];

  private logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
