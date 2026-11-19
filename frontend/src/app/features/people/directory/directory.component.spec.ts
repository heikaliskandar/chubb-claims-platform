import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../../core/auth.service';
import { EmployeesApiService } from '../../../core/employees-api.service';
import { DirectoryComponent } from './directory.component';

describe('DirectoryComponent', () => {
  const page = { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 };
  const api = { directory: vi.fn(() => of(page)) };

  beforeEach(async () => {
    vi.useFakeTimers();
    api.directory.mockClear();
    await TestBed.configureTestingModule({
      imports: [DirectoryComponent],
      providers: [
        provideRouter([]),
        { provide: EmployeesApiService, useValue: api },
        { provide: AuthService, useValue: { user: () => ({ username: 'lead1' }) } },
      ],
    }).compileComponents();
  });

  afterEach(() => vi.useRealTimers());

  it('keeps the list when the current view tab is selected again', async () => {
    const fixture = TestBed.createComponent(DirectoryComponent);
    fixture.detectChanges();
    await vi.advanceTimersByTimeAsync(300);
    fixture.detectChanges();
    const component = fixture.componentInstance as unknown as {
      result: () => unknown;
      showView: (view: 'list' | 'chart') => void;
    };
    expect(component.result()).toEqual(page);

    component.showView('list');
    await vi.advanceTimersByTimeAsync(300);

    expect(component.result()).toEqual(page);
  });
});
