import { Component, computed, ElementRef, inject, signal, viewChild } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { AvatarService } from '../../core/avatar.service';
import { BrandingService } from '../../core/branding.service';
import { Role } from '../../core/models';
import { ThemeService } from '../../core/theme.service';
import { ToastService } from '../../core/toast.service';
import { LabelPipe } from '../../shared/label.pipe';
import { LogoComponent } from '../../shared/logo/logo.component';
import { UserAvatarComponent } from '../../shared/user-avatar/user-avatar.component';

interface MenuEntry {
  label: string;
  icon: string;
  run: () => void;
  disabled?: boolean;
  separatorBefore?: boolean;
}

interface NavItem {
  label: string;
  icon: string;
  link: string;
}

interface NavGroup {
  title: string;
  items: NavItem[];
}

const TEAM: NavGroup = {
  title: 'Team',
  items: [
    { label: 'Performance', icon: 'leaderboard', link: '/team' },
    { label: 'People', icon: 'account_tree', link: '/people' },
  ],
};

const ACCOUNT: NavGroup = {
  title: 'Account',
  items: [{ label: 'My profile', icon: 'badge', link: '/people/me' }],
};

const APPROVALS: NavGroup = {
  title: 'Approvals',
  items: [{ label: 'Approvals', icon: 'how_to_reg', link: '/approvals' }],
};

const NAV_BY_ROLE: Record<Role, NavGroup[]> = {
  CLAIMANT: [
    {
      title: 'Claims',
      items: [
        { label: 'New claim', icon: 'add_circle', link: '/claims/new' },
        { label: 'My claims', icon: 'folder', link: '/claims/active' },
        { label: 'History', icon: 'history', link: '/claims/history' },
      ],
    },
  ],
  OFFICER: [
    {
      title: 'Claims',
      items: [
        { label: 'Queue', icon: 'inbox', link: '/claims/queue' },
        { label: 'Assigned to me', icon: 'person', link: '/claims/assigned' },
        { label: 'All open', icon: 'folder_open', link: '/claims/open' },
        { label: 'History', icon: 'history', link: '/claims/history' },
      ],
    },
    APPROVALS,
    TEAM,
    ACCOUNT,
  ],
  MANAGER: [
    { title: 'Overview', items: [{ label: 'Dashboard', icon: 'bar_chart', link: '/manager' }] },
    {
      title: 'Claims',
      items: [
        { label: 'All open', icon: 'folder_open', link: '/claims/open' },
        { label: 'History', icon: 'history', link: '/claims/history' },
      ],
    },
    APPROVALS,
    TEAM,
    ACCOUNT,
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
    LabelPipe,
    LogoComponent,
    UserAvatarComponent,
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
  host: {
    '(document:keydown.escape)': 'mobileOpen.set(false); menuOpen.set(false)',
    '(document:click)': 'closeMenuOnOutsideClick($event)',
    '(window:resize)': 'onResize()',
  },
})
export class ShellComponent {
  private readonly auth = inject(AuthService);
  private readonly avatars = inject(AvatarService);
  private readonly branding = inject(BrandingService);
  private readonly theme = inject(ThemeService);
  private readonly toasts = inject(ToastService);
  private readonly router = inject(Router);
  private readonly photoInput = viewChild.required<ElementRef<HTMLInputElement>>('photo');
  private readonly loginImageInput = viewChild.required<ElementRef<HTMLInputElement>>('loginImage');
  private readonly profileArea = viewChild<ElementRef<HTMLElement>>('profileArea');

  protected readonly user = this.auth.user;
  protected readonly dark = this.theme.dark;
  protected readonly collapsed = signal(matches(TABLET_QUERY) && !matches(MOBILE_QUERY));
  protected readonly mobileOpen = signal(false);
  protected readonly menuOpen = signal(false);
  protected readonly navGroups = computed(() => {
    const role = this.user()?.role;
    return role ? NAV_BY_ROLE[role] : [];
  });
  protected readonly homeLink = computed(() => {
    const role = this.user()?.role;
    return role ? this.auth.homeFor(role) : '/login';
  });
  protected readonly userMenu = computed<MenuEntry[]>(() => [
    ...(this.user()?.role === 'CLAIMANT'
      ? []
      : [{ label: 'My profile', icon: 'badge', run: () => this.router.navigate(['/people/me']) }]),
    {
      label: 'Change photo',
      icon: 'photo_camera',
      run: () => this.photoInput().nativeElement.click(),
    },
    ...(this.user()?.role === 'MANAGER' ? this.brandingMenu() : []),
    { label: 'Sign out', icon: 'logout', run: () => this.logout(), separatorBefore: true },
  ]);

  protected choose(entry: MenuEntry): void {
    this.menuOpen.set(false);
    entry.run();
  }

  protected closeMenuOnOutsideClick(event: MouseEvent): void {
    const area = this.profileArea()?.nativeElement;
    if (this.menuOpen() && area && !area.contains(event.target as Node)) {
      this.menuOpen.set(false);
    }
  }

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
      this.toasts.show('success', 'Sign-in image updated');
    } catch {
      this.toasts.show('error', 'Could not save image', 'Try a smaller image file.');
    }
  }

  protected logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }

  private brandingMenu(): MenuEntry[] {
    return [
      {
        label: 'Change sign-in image',
        icon: 'image',
        run: () => this.loginImageInput().nativeElement.click(),
        separatorBefore: true,
      },
      {
        label: 'Reset sign-in image',
        icon: 'refresh',
        disabled: !this.branding.customLoginImage(),
        run: () => {
          this.branding.resetLoginImage();
          this.toasts.show('info', 'Sign-in image reset to default');
        },
      },
    ];
  }
}

function matches(query: string): boolean {
  return window.matchMedia(query).matches;
}
