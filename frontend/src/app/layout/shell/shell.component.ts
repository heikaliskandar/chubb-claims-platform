import { Component, computed, ElementRef, inject, signal, viewChild } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { MenuModule } from 'primeng/menu';
import { AuthService } from '../../core/auth.service';
import { AvatarService } from '../../core/avatar.service';
import { Role } from '../../core/models';
import { LabelPipe } from '../../shared/label.pipe';
import { UserAvatarComponent } from '../../shared/user-avatar/user-avatar.component';

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
    ButtonModule,
    MenuModule,
    LabelPipe,
    UserAvatarComponent,
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  private readonly auth = inject(AuthService);
  private readonly avatars = inject(AvatarService);
  private readonly router = inject(Router);
  private readonly photoInput = viewChild.required<ElementRef<HTMLInputElement>>('photo');

  protected readonly user = this.auth.user;
  protected readonly collapsed = signal(false);
  protected readonly mobileOpen = signal(false);
  protected readonly navItems = computed(() => {
    const role = this.user()?.role;
    return role ? NAV_BY_ROLE[role] : [];
  });
  protected readonly userMenu: MenuItem[] = [
    {
      label: 'Change photo',
      icon: 'pi pi-camera',
      command: () => this.photoInput().nativeElement.click(),
    },
    { separator: true },
    { label: 'Sign out', icon: 'pi pi-sign-out', command: () => this.logout() },
  ];

  protected toggleSidebar(): void {
    if (window.matchMedia('(max-width: 960px)').matches) {
      this.mobileOpen.update((open) => !open);
    } else {
      this.collapsed.update((collapsed) => !collapsed);
    }
  }

  protected async onPhotoSelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    const username = this.user()?.username;
    if (file && username) {
      await this.avatars.upload(username, file);
    }
    input.value = '';
  }

  protected logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
