import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { AuthService } from '../../core/auth.service';

const DEMO_ACCOUNTS = [
  { label: 'Claimant', username: 'claimant1', icon: 'pi pi-user' },
  { label: 'Officer', username: 'officer1', icon: 'pi pi-briefcase' },
  { label: 'Team lead', username: 'lead1', icon: 'pi pi-users' },
  { label: 'Manager', username: 'manager1', icon: 'pi pi-chart-bar' },
];

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, ButtonModule, InputTextModule, PasswordModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly demoAccounts = DEMO_ACCOUNTS;
  protected readonly busy = signal(false);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

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
}
