import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { CurrentUser, Role } from './models';

interface Session {
  user: CurrentUser;
  authorization: string;
}

const HOME_BY_ROLE: Record<Role, string> = {
  CLAIMANT: '/claimant',
  OFFICER: '/officer',
  MANAGER: '/manager',
};

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly session = signal<Session | null>(null);

  readonly user = computed(() => this.session()?.user ?? null);

  login(username: string, password: string): Observable<CurrentUser> {
    const authorization = `Basic ${btoa(`${username}:${password}`)}`;
    return this.http
      .get<CurrentUser>('/api/v1/me', { headers: { Authorization: authorization } })
      .pipe(tap((user) => this.session.set({ user, authorization })));
  }

  logout(): void {
    this.session.set(null);
  }

  authorizationHeader(): string | null {
    return this.session()?.authorization ?? null;
  }

  homeFor(role: Role): string {
    return HOME_BY_ROLE[role];
  }
}
