import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { EmployeesApiService } from './employees-api.service';
import { PeopleService } from './people.service';

describe('PeopleService', () => {
  const api = {
    directory: vi.fn(),
    profile: vi.fn(),
  };

  function create(): PeopleService {
    TestBed.configureTestingModule({
      providers: [{ provide: EmployeesApiService, useValue: api }],
    });
    return TestBed.inject(PeopleService);
  }

  beforeEach(() => vi.resetAllMocks());

  it('names people below you and your own manager, and falls back to the username', () => {
    api.directory.mockReturnValue(
      of({ content: [{ username: 'officer1', fullName: 'Daniel Lim' }], totalElements: 1 }),
    );
    api.profile.mockReturnValue(of({ manager: { username: 'lead1', fullName: 'Arjun Mehta' } }));
    const people = create();

    people.load();

    expect(people.nameOf('officer1')).toBe('Daniel Lim');
    expect(people.firstNameOf('officer1')).toBe('Daniel');
    expect(people.nameOf('lead1')).toBe('Arjun Mehta');
    expect(people.nameOf('manager1')).toBe('manager1');
    expect(people.nameOf(null)).toBe('');
  });

  it('loads once, and can retry after a failure', () => {
    api.directory.mockReturnValue(throwError(() => new Error('offline')));
    api.profile.mockReturnValue(of({ manager: null }));
    const people = create();

    people.load();
    people.load();
    expect(api.directory).toHaveBeenCalledTimes(2);

    api.directory.mockReturnValue(of({ content: [], totalElements: 0 }));
    people.load();
    people.load();
    expect(api.directory).toHaveBeenCalledTimes(3);
  });
});
