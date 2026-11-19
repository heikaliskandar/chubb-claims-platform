import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { BrandingService } from '../../core/branding.service';
import { ThemeService } from '../../core/theme.service';
import { LogoComponent } from '../../shared/logo/logo.component';

const DEMO_ACCOUNTS = [
  { label: 'Claimant', username: 'claimant1' },
  { label: 'Claims officer', username: 'officer1' },
  { label: 'Team lead', username: 'lead1' },
  { label: 'Claims manager', username: 'manager1' },
];

const GREETINGS = ['Welcome', 'Selamat datang', 'Maligayang pagdating', 'ยินดีต้อนรับ', '歡迎'];
const TYPE_MS = 110;
const ERASE_MS = 55;
const HOLD_MS = 2200;

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, LogoComponent],
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
  protected readonly capsLock = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly submitted = signal(false);
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

  protected passwordHintId(): string | null {
    if (this.invalid('password')) {
      return 'password-error';
    }
    return this.capsLock() ? 'password-caps' : null;
  }

  protected invalid(name: 'username' | 'password'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.submitted());
  }

  protected checkCapsLock(event: KeyboardEvent): void {
    this.capsLock.set(event.getModifierState?.('CapsLock') ?? false);
  }

  protected useDemo(username: string): void {
    this.form.setValue({ username, password: 'password' });
    this.error.set(null);
  }

  protected submit(): void {
    this.submitted.set(true);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { username, password } = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    this.auth.login(username, password).subscribe({
      next: (user) => this.router.navigateByUrl(this.auth.homeFor(user.role)),
      error: (failure: HttpErrorResponse) => {
        this.busy.set(false);
        if (failure.status === 401) {
          this.error.set("That username and password don't match. Check them and try again.");
          this.form.controls.password.reset();
          this.submitted.set(false);
          document.getElementById('password')?.focus();
        }
      },
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
