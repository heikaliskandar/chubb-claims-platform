import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let http: HttpTestingController;

  function create(): AuthService {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    return TestBed.inject(AuthService);
  }

  beforeEach(() => sessionStorage.clear());
  afterEach(() => http.verify());

  it('signs in with HTTP Basic and keeps the session for the tab', () => {
    const auth = create();
    auth.login('officer1', 'password').subscribe();

    const request = http.expectOne('/api/v1/me');
    expect(request.request.headers.get('Authorization')).toBe(`Basic ${btoa('officer1:password')}`);
    request.flush({ username: 'officer1', role: 'OFFICER', position: 'CLAIMS_OFFICER' });

    expect(auth.user()?.username).toBe('officer1');
    expect(auth.authorizationHeader()).toBe(`Basic ${btoa('officer1:password')}`);
    expect(sessionStorage.getItem('claims.session')).toContain('officer1');
    expect(localStorage.getItem('claims.session')).toBeNull();
  });

  it('does not keep a session when sign-in fails', () => {
    const auth = create();
    auth.login('officer1', 'wrong').subscribe({ error: () => undefined });
    http.expectOne('/api/v1/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.user()).toBeNull();
    expect(sessionStorage.getItem('claims.session')).toBeNull();
  });

  it('clears the session on sign-out', () => {
    const auth = create();
    auth.login('claimant1', 'password').subscribe();
    http.expectOne('/api/v1/me').flush({ username: 'claimant1', role: 'CLAIMANT', position: null });

    auth.logout();

    expect(auth.user()).toBeNull();
    expect(auth.authorizationHeader()).toBeNull();
    expect(sessionStorage.getItem('claims.session')).toBeNull();
  });

  it('sends each role to its own home page', () => {
    const auth = create();
    expect(auth.homeFor('CLAIMANT')).toBe('/claims/active');
    expect(auth.homeFor('OFFICER')).toBe('/claims/queue');
    expect(auth.homeFor('MANAGER')).toBe('/manager');
  });
});
