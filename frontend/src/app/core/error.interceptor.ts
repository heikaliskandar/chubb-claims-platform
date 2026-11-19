import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { ProblemDetail } from './models';
import { ToastService } from './toast.service';

export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const toasts = inject(ToastService);

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        if (auth.user()) {
          auth.logout();
          router.navigate(['/login']);
        }
      } else if (error.status === 0 || error.status >= 502) {
        toasts.show(
          'error',
          'Service unavailable',
          'The server is not responding. Please try again shortly.',
        );
      } else {
        const problem = error.error as ProblemDetail | null;
        toasts.show(
          error.status >= 500 ? 'error' : 'warn',
          problem?.title ?? 'Request failed',
          detailOf(problem),
        );
      }
      return throwError(() => error);
    }),
  );
};

function detailOf(problem: ProblemDetail | null): string {
  if (problem?.errors) {
    return Object.entries(problem.errors)
      .map(([field, message]) => `${field}: ${message}`)
      .join(' · ');
  }
  return problem?.detail ?? 'Please try again.';
}
