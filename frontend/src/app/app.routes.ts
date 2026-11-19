import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/auth.guards';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'claimant',
    canActivate: [roleGuard('CLAIMANT')],
    loadComponent: () =>
      import('./features/claimant/my-claims/my-claims.component').then((m) => m.MyClaimsComponent),
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
      import('./features/manager/dashboard/dashboard.component').then((m) => m.DashboardComponent),
  },
  {
    path: 'claims/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/claims/claim-detail/claim-detail.component').then(
        (m) => m.ClaimDetailComponent,
      ),
  },
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: '**', redirectTo: 'login' },
];
