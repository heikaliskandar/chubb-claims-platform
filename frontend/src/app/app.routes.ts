import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { authGuard, roleGuard, roleMatch } from './core/auth.guards';
import { AuthService } from './core/auth.service';

const claimList = () =>
  import('./features/claims/claim-list/claim-list.component').then((m) => m.ClaimListComponent);

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
      { path: 'claimant', redirectTo: 'claims/active' },
      { path: 'officer', redirectTo: 'claims/queue' },
      {
        path: 'claims/new',
        canActivate: [roleGuard('CLAIMANT')],
        loadComponent: () =>
          import('./features/claimant/submit-claim/submit-claim.component').then(
            (m) => m.SubmitClaimComponent,
          ),
      },
      {
        path: 'claims/active',
        canActivate: [roleGuard('CLAIMANT')],
        loadComponent: claimList,
        data: { view: 'my-active' },
      },
      {
        path: 'claims/history',
        canMatch: [roleMatch('CLAIMANT')],
        loadComponent: claimList,
        data: { view: 'my-history' },
      },
      {
        path: 'claims/history',
        canMatch: [roleMatch('OFFICER', 'MANAGER')],
        loadComponent: claimList,
        data: { view: 'history' },
      },
      {
        path: 'claims/queue',
        canActivate: [roleGuard('OFFICER')],
        loadComponent: claimList,
        data: { view: 'queue' },
      },
      {
        path: 'claims/assigned',
        canActivate: [roleGuard('OFFICER')],
        loadComponent: claimList,
        data: { view: 'assigned' },
      },
      {
        path: 'claims/open',
        canActivate: [roleGuard('OFFICER', 'MANAGER')],
        loadComponent: claimList,
        data: { view: 'open' },
      },
      {
        path: 'claims/:id',
        loadComponent: () =>
          import('./features/claims/claim-detail/claim-detail.component').then(
            (m) => m.ClaimDetailComponent,
          ),
      },
      {
        path: 'approvals',
        canActivate: [roleGuard('OFFICER', 'MANAGER')],
        loadComponent: () =>
          import('./features/approvals/approvals.component').then((m) => m.ApprovalsComponent),
      },
      {
        path: 'team',
        canActivate: [roleGuard('OFFICER', 'MANAGER')],
        loadComponent: () => import('./features/team/team.component').then((m) => m.TeamComponent),
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
        path: 'people',
        canActivate: [roleGuard('OFFICER', 'MANAGER')],
        loadComponent: () =>
          import('./features/people/directory/directory.component').then(
            (m) => m.DirectoryComponent,
          ),
      },
      {
        path: 'people/:username',
        canActivate: [roleGuard('OFFICER', 'MANAGER')],
        loadComponent: () =>
          import('./features/people/profile/profile.component').then((m) => m.ProfileComponent),
      },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
