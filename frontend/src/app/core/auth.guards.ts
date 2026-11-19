import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Role } from './models';

export const authGuard: CanActivateFn = () =>
  inject(AuthService).user() ? true : inject(Router).createUrlTree(['/login']);

export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => {
    const user = inject(AuthService).user();
    return user && roles.includes(user.role) ? true : inject(Router).createUrlTree(['/login']);
  };
}
