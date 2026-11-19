import { Injectable, signal } from '@angular/core';

export type ToastSeverity = 'success' | 'info' | 'warn' | 'error';

export interface Toast {
  id: number;
  severity: ToastSeverity;
  summary: string;
  detail?: string;
}

const DEFAULT_LIFE_MS = 4500;

@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly toasts = signal<Toast[]>([]);
  private nextId = 0;

  show(severity: ToastSeverity, summary: string, detail?: string, lifeMs = DEFAULT_LIFE_MS): void {
    const id = ++this.nextId;
    this.toasts.update((toasts) => [...toasts, { id, severity, summary, detail }]);
    setTimeout(() => this.dismiss(id), lifeMs);
  }

  dismiss(id: number): void {
    this.toasts.update((toasts) => toasts.filter((toast) => toast.id !== id));
  }
}
