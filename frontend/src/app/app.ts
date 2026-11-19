import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';
import { LabelPipe } from './shared/label.pipe';

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatButtonModule,
    LabelPipe,
  ],
  template: `
    <mat-toolbar color="primary" class="toolbar">
      <span class="brand">APAC Claims</span>
      @if (auth.user(); as user) {
        <nav>
          @switch (user.role) {
            @case ('CLAIMANT') {
              <a
                mat-button
                routerLink="/claimant"
                routerLinkActive="active"
                [routerLinkActiveOptions]="{ exact: true }"
              >
                My claims
              </a>
              <a mat-button routerLink="/claimant/new" routerLinkActive="active"
                >Report an incident</a
              >
            }
            @case ('OFFICER') {
              <a mat-button routerLink="/officer" routerLinkActive="active">Claims queue</a>
            }
            @case ('MANAGER') {
              <a mat-button routerLink="/manager" routerLinkActive="active">Dashboard</a>
              <a mat-button routerLink="/officer" routerLinkActive="active">Claims</a>
            }
          }
        </nav>
        <span class="spacer"></span>
        <span class="user">{{ user.username }} · {{ user.position ?? user.role | label }}</span>
        <button mat-stroked-button (click)="logout()">Log out</button>
      }
    </mat-toolbar>
    <main class="content">
      <router-outlet />
    </main>
  `,
  styles: `
    .toolbar {
      gap: 8px;
    }
    .brand {
      font-weight: 600;
      margin-right: 16px;
    }
    .spacer {
      flex: 1;
    }
    .user {
      font-size: 14px;
      margin-right: 8px;
    }
    .active {
      text-decoration: underline;
    }
    .content {
      max-width: 1200px;
      margin: 24px auto;
      padding: 0 16px;
    }
  `,
})
export class App {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
