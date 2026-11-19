import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/auth.guards';
import { AuthService } from './core/auth.service';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell/shell.component').then((m) => m.ShellComponent),
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: () => {
          const auth = inject(AuthService);
          const user = auth.user();
          return user ? auth.homeFor(user.role) : '/login';
        },
      },
      {
        path: 'claimant',
        canActivate: [roleGuard('CLAIMANT')],
        loadComponent: () =>
          import('./features/claimant/my-claims/my-claims.component').then(
            (m) => m.MyClaimsComponent,
          ),
      },
      {
        path: 'claimant/new',
        canActivate: [roleGuard('CLAIMANT')],
        loadComponent: () =>
          import('./features/claimant/submit-claim/submit-claim.component').then(
            (m) => m.SubmitClaimComponent,
          ),
      },
      {
        path: 'officer',
        canActivate: [roleGuard('OFFICER', 'MANAGER')],
        loadComponent: () =>
          import('./features/officer/claim-queue/claim-queue.component').then(
            (m) => m.ClaimQueueComponent,
          ),
      },
      {
        path: 'manager',
        canActivate: [roleGuard('MANAGER')],
        loadComponent: () =>
          import('./features/manager/dashboard/dashboard.component').then(
            (m) => m.DashboardComponent,
          ),
      },
      {
        path: 'claims/:id',
        loadComponent: () =>
          import('./features/claims/claim-detail/claim-detail.component').then(
            (m) => m.ClaimDetailComponent,
          ),
      },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
