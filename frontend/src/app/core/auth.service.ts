import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { CurrentUser, Role } from './models';

interface Session {
  user: CurrentUser;
  authorization: string;
}

const SESSION_KEY = 'claims.session';

function readStoredSession(): Session | null {
  try {
    const stored = sessionStorage.getItem(SESSION_KEY);
    return stored ? (JSON.parse(stored) as Session) : null;
  } catch {
    return null;
  }
}

const HOME_BY_ROLE: Record<Role, string> = {
  CLAIMANT: '/claims/active',
  OFFICER: '/claims/queue',
  MANAGER: '/manager',
};

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly session = signal<Session | null>(readStoredSession());

  readonly user = computed(() => this.session()?.user ?? null);

  login(username: string, password: string): Observable<CurrentUser> {
    const authorization = `Basic ${btoa(`${username}:${password}`)}`;
    return this.http
      .get<CurrentUser>('/api/v1/me', { headers: { Authorization: authorization } })
      .pipe(tap((user) => this.setSession({ user, authorization })));
  }

  logout(): void {
    this.setSession(null);
  }

  private setSession(session: Session | null): void {
    this.session.set(session);
    try {
      if (session) {
        sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
      } else {
        sessionStorage.removeItem(SESSION_KEY);
      }
    } catch {}
  }

  authorizationHeader(): string | null {
    return this.session()?.authorization ?? null;
  }

  homeFor(role: Role): string {
    return HOME_BY_ROLE[role];
  }
}
