import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { errorInterceptor } from './error.interceptor';
import { ToastService } from './toast.service';

describe('errorInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let toasts: ToastService;
  const auth = { user: vi.fn(), logout: vi.fn() };
  const router = { navigate: vi.fn() };

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    toasts = TestBed.inject(ToastService);
  });

  afterEach(() => backend.verify());

  it('signs a signed-in user out when the server rejects their credentials', () => {
    auth.user.mockReturnValue({ username: 'officer1' });
    http.get('/api/v1/claims').subscribe({ error: () => undefined });
    backend.expectOne('/api/v1/claims').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.logout).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
    expect(toasts.toasts()).toEqual([]);
  });

  it('leaves a failed sign-in to the login page, without a toast', () => {
    auth.user.mockReturnValue(null);
    http.get('/api/v1/me').subscribe({ error: () => undefined });
    backend.expectOne('/api/v1/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.logout).not.toHaveBeenCalled();
    expect(toasts.toasts()).toEqual([]);
  });

  it('shows Problem Details field errors as a warning', () => {
    http.post('/api/v1/claims', {}).subscribe({ error: () => undefined });
    backend
      .expectOne('/api/v1/claims')
      .flush(
        { title: 'Validation failed', errors: { claimedAmount: 'must be greater than 0' } },
        { status: 400, statusText: 'Bad Request' },
      );

    expect(toasts.toasts()).toEqual([
      expect.objectContaining({
        severity: 'warn',
        summary: 'Validation failed',
        detail: 'claimedAmount: must be greater than 0',
      }),
    ]);
  });

  it('reports an unreachable server as unavailable', () => {
    http.get('/api/v1/claims').subscribe({ error: () => undefined });
    backend.expectOne('/api/v1/claims').flush(null, { status: 503, statusText: 'Unavailable' });

    expect(toasts.toasts()[0]).toEqual(
      expect.objectContaining({ severity: 'error', summary: 'Service unavailable' }),
    );
  });
});
