import { Component, input, output } from '@angular/core';

@Component({
  selector: 'app-modal',
  host: { '(document:keydown.escape)': 'open() && closed.emit()' },
  template: `
    @if (open()) {
      <div class="backdrop" (click)="closed.emit()"></div>
      <section class="dialog" role="dialog" aria-modal="true" [attr.aria-label]="title()">
        <header>
          <h2>{{ title() }}</h2>
          <button
            type="button"
            class="btn btn-ghost btn-icon"
            aria-label="Close"
            (click)="closed.emit()"
          >
            <span class="ms" aria-hidden="true">close</span>
          </button>
        </header>
        <ng-content />
      </section>
    }
  `,
  styles: `
    .backdrop {
      position: fixed;
      inset: 0;
      z-index: 50;
      background: rgb(8 12 20 / 50%);
      backdrop-filter: blur(2px);
      animation: fade 0.15s ease-out;
    }
    .dialog {
      position: fixed;
      top: 50%;
      left: 50%;
      z-index: 51;
      width: min(460px, calc(100vw - 32px));
      max-height: calc(100dvh - 48px);
      overflow-y: auto;
      padding: 20px 24px 24px;
      border-radius: 18px;
      background: var(--app-surface);
      box-shadow: var(--app-shadow-pop);
      transform: translate(-50%, -50%);
      animation: pop 0.18s ease-out;
    }
    header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      margin-bottom: 16px;
    }
    h2 {
      font-size: 18px;
    }
    @keyframes fade {
      from {
        opacity: 0;
      }
    }
    @keyframes pop {
      from {
        opacity: 0;
        transform: translate(-50%, -46%);
      }
    }
    @media (max-width: 640px) {
      .dialog {
        top: auto;
        bottom: 0;
        width: 100%;
        border-radius: 20px 20px 0 0;
        transform: translateX(-50%);
        animation: none;
      }
    }
  `,
})
export class ModalComponent {
  readonly open = input(false);
  readonly title = input('');
  readonly closed = output<void>();
}
