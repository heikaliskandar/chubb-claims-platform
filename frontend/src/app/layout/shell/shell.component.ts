import { Component, computed, ElementRef, inject, signal, viewChild } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MenuItem, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { MenuModule } from 'primeng/menu';
import { AuthService } from '../../core/auth.service';
import { AvatarService } from '../../core/avatar.service';
import { BrandingService } from '../../core/branding.service';
import { Role } from '../../core/models';
import { ThemeService } from '../../core/theme.service';
import { LabelPipe } from '../../shared/label.pipe';
import { LogoComponent } from '../../shared/logo/logo.component';
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
  OFFICER: [
    { label: 'Claims', icon: 'pi pi-inbox', link: '/officer' },
    { label: 'People', icon: 'pi pi-sitemap', link: '/people' },
    { label: 'My profile', icon: 'pi pi-id-card', link: '/people/me' },
  ],
  MANAGER: [
    { label: 'Overview', icon: 'pi pi-chart-bar', link: '/manager' },
    { label: 'Claims', icon: 'pi pi-inbox', link: '/officer' },
    { label: 'People', icon: 'pi pi-sitemap', link: '/people' },
    { label: 'My profile', icon: 'pi pi-id-card', link: '/people/me' },
  ],
};

const MOBILE_QUERY = '(max-width: 960px)';
const TABLET_QUERY = '(max-width: 1279px)';

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    ButtonModule,
    MenuModule,
    LabelPipe,
    LogoComponent,
    UserAvatarComponent,
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
  host: {
    '(document:keydown.escape)': 'mobileOpen.set(false)',
    '(window:resize)': 'onResize()',
  },
})
export class ShellComponent {
  private readonly auth = inject(AuthService);
  private readonly avatars = inject(AvatarService);
  private readonly branding = inject(BrandingService);
  private readonly theme = inject(ThemeService);
  private readonly messages = inject(MessageService);
  private readonly router = inject(Router);
  private readonly photoInput = viewChild.required<ElementRef<HTMLInputElement>>('photo');
  private readonly loginImageInput = viewChild.required<ElementRef<HTMLInputElement>>('loginImage');

  protected readonly user = this.auth.user;
  protected readonly dark = this.theme.dark;
  protected readonly collapsed = signal(matches(TABLET_QUERY) && !matches(MOBILE_QUERY));
  protected readonly mobileOpen = signal(false);
  protected readonly navItems = computed(() => {
    const role = this.user()?.role;
    return role ? NAV_BY_ROLE[role] : [];
  });
  protected readonly userMenu = computed<MenuItem[]>(() => [
    ...(this.user()?.role === 'CLAIMANT'
      ? []
      : [{ label: 'My profile', icon: 'pi pi-id-card', routerLink: '/people/me' }]),
    {
      label: 'Change photo',
      icon: 'pi pi-camera',
      command: () => this.photoInput().nativeElement.click(),
    },
    ...(this.user()?.role === 'MANAGER' ? this.brandingMenu() : []),
    { separator: true },
    { label: 'Sign out', icon: 'pi pi-sign-out', command: () => this.logout() },
  ]);

  protected toggleSidebar(): void {
    if (matches(MOBILE_QUERY)) {
      this.mobileOpen.update((open) => !open);
    } else {
      this.collapsed.update((collapsed) => !collapsed);
    }
  }

  protected toggleTheme(): void {
    this.theme.toggle();
  }

  protected onResize(): void {
    if (this.mobileOpen() && !matches(MOBILE_QUERY)) {
      this.mobileOpen.set(false);
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

  protected async onLoginImageSelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    try {
      await this.branding.setLoginImage(file);
      this.messages.add({ severity: 'success', summary: 'Sign-in image updated' });
    } catch {
      this.messages.add({
        severity: 'error',
        summary: 'Could not save image',
        detail: 'Try a smaller image file.',
      });
    }
  }

  protected logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }

  private brandingMenu(): MenuItem[] {
    return [
      { separator: true },
      {
        label: 'Change sign-in image',
        icon: 'pi pi-image',
        command: () => this.loginImageInput().nativeElement.click(),
      },
      {
        label: 'Reset sign-in image',
        icon: 'pi pi-refresh',
        disabled: !this.branding.loginImage(),
        command: () => {
          this.branding.resetLoginImage();
          this.messages.add({ severity: 'info', summary: 'Sign-in image reset to default' });
        },
      },
    ];
  }
}

function matches(query: string): boolean {
  return window.matchMedia(query).matches;
}
