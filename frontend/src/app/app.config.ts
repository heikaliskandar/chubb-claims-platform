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
