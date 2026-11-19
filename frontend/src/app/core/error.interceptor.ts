import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { ProblemDetail } from './models';

export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const snackBar = inject(MatSnackBar);

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && auth.user()) {
        auth.logout();
        router.navigate(['/login']);
      } else {
        snackBar.open(messageOf(error), 'Dismiss', { duration: 6000 });
      }
      return throwError(() => error);
    }),
  );
};

function messageOf(error: HttpErrorResponse): string {
  const problem = error.error as ProblemDetail | null;
  if (problem?.errors) {
    return Object.entries(problem.errors)
      .map(([field, message]) => `${field}: ${message}`)
      .join('; ');
  }
  return problem?.detail ?? problem?.title ?? 'Something went wrong. Please try again.';
}
