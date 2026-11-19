import { Injectable, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { EmployeesApiService } from './employees-api.service';

const DIRECTORY_SIZE = 100;

@Injectable({ providedIn: 'root' })
export class PeopleService {
  private readonly api = inject(EmployeesApiService);
  private readonly names = signal<Record<string, string>>({});
  private loaded = false;

  load(): void {
    if (this.loaded) {
      return;
    }
    this.loaded = true;
    forkJoin({
      directory: this.api.directory('', 0, DIRECTORY_SIZE),
      me: this.api.profile('me'),
    }).subscribe({
      next: ({ directory, me }) => {
        const names = Object.fromEntries(
          directory.content.map((person) => [person.username, person.fullName]),
        );
        if (me.manager) {
          names[me.manager.username] = me.manager.fullName;
        }
        this.names.set(names);
      },
      error: () => (this.loaded = false),
    });
  }

  nameOf(username: string | null | undefined): string {
    return username ? (this.names()[username] ?? username) : '';
  }

  firstNameOf(username: string | null | undefined): string {
    return this.nameOf(username).split(' ')[0];
  }
}
