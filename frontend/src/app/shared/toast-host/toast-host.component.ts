import { Component, inject } from '@angular/core';
import { ToastService, ToastSeverity } from '../../core/toast.service';

const ICONS: Record<ToastSeverity, string> = {
  success: 'check_circle',
  info: 'info',
  warn: 'warning',
  error: 'error',
};

@Component({
  selector: 'app-toast-host',
  template: `
    <div class="toasts" aria-live="polite">
      @for (toast of toasts.toasts(); track toast.id) {
        <div class="toast" [class]="'toast ' + toast.severity" role="status">
          <span class="ms" aria-hidden="true">{{ icons[toast.severity] }}</span>
          <div class="text">
            <strong>{{ toast.summary }}</strong>
            @if (toast.detail) {
              <span>{{ toast.detail }}</span>
            }
          </div>
          <button
            type="button"
            class="btn btn-ghost btn-icon"
            aria-label="Dismiss"
            (click)="toasts.dismiss(toast.id)"
          >
            <span class="ms" aria-hidden="true">close</span>
          </button>
        </div>
      }
    </div>
  `,
  styles: `
    .toasts {
      position: fixed;
      top: 20px;
      right: 20px;
      z-index: 100;
      display: flex;
      flex-direction: column;
      gap: 10px;
      width: min(380px, calc(100vw - 32px));
    }
    .toast {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      padding: 12px 8px 12px 14px;
      border-left: 4px solid var(--accent);
      border-radius: 12px;
      background: var(--app-surface);
      box-shadow: var(--app-shadow-pop);
      animation: slide-in 0.2s ease-out;
    }
    .toast > .ms {
      margin-top: 2px;
      color: var(--accent);
    }
    .text {
      display: flex;
      flex: 1;
      flex-direction: column;
      gap: 2px;
      min-width: 0;
      padding-top: 2px;
    }
    .text span {
      color: var(--app-muted);
      font-size: 13px;
      overflow-wrap: anywhere;
    }
    .btn-icon {
      width: 32px;
      height: 32px;
    }
    .success {
      --accent: var(--app-success);
    }
    .info {
      --accent: var(--app-primary);
    }
    .warn {
      --accent: var(--app-warning);
    }
    .error {
      --accent: var(--app-danger);
    }
    @keyframes slide-in {
      from {
        opacity: 0;
        transform: translateY(-8px);
      }
    }
    @media (max-width: 640px) {
      .toasts {
        top: auto;
        right: 16px;
        bottom: 16px;
      }
    }
  `,
})
export class ToastHostComponent {
  protected readonly toasts = inject(ToastService);
  protected readonly icons = ICONS;
}
