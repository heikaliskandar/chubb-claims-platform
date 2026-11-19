import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { ProblemDetail } from './models';

export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const messages = inject(MessageService);

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && auth.user()) {
        auth.logout();
        router.navigate(['/login']);
      } else {
        const problem = error.error as ProblemDetail | null;
        messages.add({
          severity: error.status >= 500 || error.status === 0 ? 'error' : 'warn',
          summary: problem?.title ?? 'Request failed',
          detail: detailOf(problem),
          life: 6000,
        });
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
