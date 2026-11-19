import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { AuthService } from '../../core/auth.service';
import { BrandingService } from '../../core/branding.service';
import { ThemeService } from '../../core/theme.service';
import { LogoComponent } from '../../shared/logo/logo.component';

const DEMO_ACCOUNTS = [
  { label: 'Claimant', username: 'claimant1', icon: 'pi pi-user' },
  { label: 'Officer', username: 'officer1', icon: 'pi pi-briefcase' },
  { label: 'Team lead', username: 'lead1', icon: 'pi pi-users' },
  { label: 'Manager', username: 'manager1', icon: 'pi pi-chart-bar' },
];

const GREETINGS = ['Welcome', 'Selamat datang', 'Maligayang pagdating', 'ยินดีต้อนรับ', '歡迎'];
const TYPE_MS = 110;
const ERASE_MS = 55;
const HOLD_MS = 2200;

@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    ButtonModule,
    InputTextModule,
    IconFieldModule,
    InputIconModule,
    LogoComponent,
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly theme = inject(ThemeService);

  protected readonly loginImage = inject(BrandingService).loginImage;
  protected readonly dark = this.theme.dark;
  protected readonly demoAccounts = DEMO_ACCOUNTS;
  protected readonly year = new Date().getFullYear();
  protected readonly busy = signal(false);
  protected readonly showPassword = signal(false);
  protected readonly typed = signal(GREETINGS[0]);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  constructor() {
    if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      this.startTyping();
    }
  }

  protected toggleTheme(): void {
    this.theme.toggle();
  }

  protected useDemo(username: string): void {
    this.form.setValue({ username, password: 'password' });
  }

  protected submit(): void {
    const { username, password } = this.form.getRawValue();
    this.busy.set(true);
    this.auth.login(username, password).subscribe({
      next: (user) => this.router.navigateByUrl(this.auth.homeFor(user.role)),
      error: () => this.busy.set(false),
    });
  }

  private startTyping(): void {
    let phrase = 0;
    let length = 0;
    let erasing = false;
    let timer: ReturnType<typeof setTimeout>;

    const step = () => {
      const text = [...GREETINGS[phrase]];
      length += erasing ? -1 : 1;
      this.typed.set(text.slice(0, length).join(''));

      let delay = erasing ? ERASE_MS : TYPE_MS;
      if (!erasing && length === text.length) {
        erasing = true;
        delay = HOLD_MS;
      } else if (erasing && length === 0) {
        erasing = false;
        phrase = (phrase + 1) % GREETINGS.length;
        delay = TYPE_MS * 3;
      }
      timer = setTimeout(step, delay);
    };

    this.typed.set('');
    timer = setTimeout(step, TYPE_MS);
    inject(DestroyRef).onDestroy(() => clearTimeout(timer));
  }
}
