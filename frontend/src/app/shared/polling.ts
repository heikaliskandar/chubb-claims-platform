import { catchError, EMPTY, Observable, switchMap, timer } from 'rxjs';

export const POLL_INTERVAL_MS = 10_000;

export function poll<T>(request: () => Observable<T>): Observable<T> {
  return timer(0, POLL_INTERVAL_MS).pipe(switchMap(() => request().pipe(catchError(() => EMPTY))));
}
