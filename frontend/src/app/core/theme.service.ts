import { effect, Injectable, signal } from '@angular/core';

const STORAGE_KEY = 'claims.theme';
const DARK_CLASS = 'app-dark';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly dark = signal(readInitial());

  constructor() {
    effect(() => {
      const dark = this.dark();
      document.documentElement.classList.toggle(DARK_CLASS, dark);
      try {
        localStorage.setItem(STORAGE_KEY, dark ? 'dark' : 'light');
      } catch {}
    });
  }

  toggle(): void {
    this.dark.update((dark) => !dark);
  }
}

function readInitial(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'dark';
  } catch {
    return false;
  }
}
