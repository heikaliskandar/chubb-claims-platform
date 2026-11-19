import { Component, computed, input, output } from '@angular/core';

@Component({
  selector: 'app-pager',
  template: `
    @if (total() > 0) {
      <div class="pager">
        <span>{{ from() }}–{{ to() }} of {{ total() }}</span>
        @if (pages() > 1) {
          <span class="buttons">
            <button
              type="button"
              class="btn btn-outline btn-icon"
              aria-label="Previous page"
              [disabled]="page() === 0"
              (click)="pageChange.emit(page() - 1)"
            >
              <span class="ms" aria-hidden="true">chevron_left</span>
            </button>
            <button
              type="button"
              class="btn btn-outline btn-icon"
              aria-label="Next page"
              [disabled]="page() + 1 >= pages()"
              (click)="pageChange.emit(page() + 1)"
            >
              <span class="ms" aria-hidden="true">chevron_right</span>
            </button>
          </span>
        }
      </div>
    }
  `,
  styles: `
    .pager {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      padding-top: 14px;
      color: var(--app-muted);
      font-size: 13px;
    }
    .buttons {
      display: inline-flex;
      gap: 8px;
    }
    .btn-icon {
      width: 36px;
      height: 36px;
    }
  `,
})
export class PagerComponent {
  readonly page = input.required<number>();
  readonly size = input.required<number>();
  readonly total = input.required<number>();
  readonly pageChange = output<number>();

  protected readonly pages = computed(() => Math.ceil(this.total() / this.size()));
  protected readonly from = computed(() => this.page() * this.size() + 1);
  protected readonly to = computed(() => Math.min(this.total(), (this.page() + 1) * this.size()));
}
