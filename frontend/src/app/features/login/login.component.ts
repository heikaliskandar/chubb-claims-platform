import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
  ],
  template: `
    <mat-card class="login">
      <mat-card-header>
        <mat-card-title>Sign in</mat-card-title>
        <mat-card-subtitle
          >Demo users are listed in the README (password: password)</mat-card-subtitle
        >
      </mat-card-header>
      <mat-card-content>
        <form [formGroup]="form" (ngSubmit)="submit()">
          <mat-form-field appearance="outline">
            <mat-label>Username</mat-label>
            <input matInput formControlName="username" autocomplete="username" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Password</mat-label>
            <input
              matInput
              type="password"
              formControlName="password"
              autocomplete="current-password"
            />
          </mat-form-field>
          <button mat-flat-button type="submit" [disabled]="form.invalid || busy()">Sign in</button>
        </form>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .login {
      max-width: 400px;
      margin: 64px auto;
    }
    form {
      display: flex;
      flex-direction: column;
      gap: 8px;
      margin-top: 16px;
    }
  `,
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly busy = signal(false);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  protected submit(): void {
    const { username, password } = this.form.getRawValue();
    this.busy.set(true);
    this.auth.login(username, password).subscribe({
      next: (user) => this.router.navigateByUrl(this.auth.homeFor(user.role)),
      error: () => this.busy.set(false),
    });
  }
}
