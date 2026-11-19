import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';
import { errorInterceptor } from './core/error.interceptor';

const ClaimsTheme = definePreset(Aura, {
  semantic: {
    primary: {
      50: '#eef4ff',
      100: '#dce7fe',
      200: '#bfd3fe',
      300: '#93b4fd',
      400: '#6f97f8',
      500: '#4f7df3',
      600: '#3a63e0',
      700: '#2f4fc0',
      800: '#2a439b',
      900: '#283c7a',
      950: '#1b254b',
    },
    colorScheme: {
      dark: {
        surface: {
          0: '#ffffff',
          50: '#f4f6fa',
          100: '#e3e7ef',
          200: '#c9cfdb',
          300: '#a3acbd',
          400: '#8d96a8',
          500: '#6b7385',
          600: '#4a5266',
          700: '#323a4b',
          800: '#232b3a',
          900: '#141a25',
          950: '#0b0f17',
        },
      },
    },
  },
  components: {
    breadcrumb: {
      root: { background: 'transparent', padding: '0' },
    },
  },
});

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
    providePrimeNG({ theme: { preset: ClaimsTheme, options: { darkModeSelector: '.app-dark' } } }),
    MessageService,
  ],
};
