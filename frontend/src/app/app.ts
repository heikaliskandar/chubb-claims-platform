import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastModule } from 'primeng/toast';
import { ThemeService } from './core/theme.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ToastModule],
  template: `
    <router-outlet />
    <p-toast position="top-right" />
  `,
})
export class App {
  constructor() {
    inject(ThemeService);
  }
}
