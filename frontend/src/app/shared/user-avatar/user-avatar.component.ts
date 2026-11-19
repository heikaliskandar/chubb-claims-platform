import { Component, computed, inject, input } from '@angular/core';
import { AvatarService } from '../../core/avatar.service';

const SIZES = { sm: 32, md: 40, lg: 48, xl: 88 };

@Component({
  selector: 'app-user-avatar',
  host: {
    '[style.--size.px]': 'pixels()',
    '[attr.aria-hidden]': 'true',
  },
  template: `
    @if (image(); as image) {
      <img [src]="image" alt="" />
    } @else {
      <span>{{ initials() }}</span>
    }
  `,
  styles: `
    :host {
      display: inline-grid;
      flex: none;
      place-items: center;
      width: var(--size);
      height: var(--size);
      overflow: hidden;
      border-radius: 50%;
      background: var(--app-primary-soft);
      color: var(--app-primary-strong);
      font-size: calc(var(--size) * 0.36);
      font-weight: 700;
    }
    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  `,
})
export class UserAvatarComponent {
  private readonly avatars = inject(AvatarService);

  readonly username = input.required<string>();
  readonly size = input<keyof typeof SIZES>('md');

  protected readonly pixels = computed(() => SIZES[this.size()]);
  protected readonly image = computed(() => this.avatars.avatarOf(this.username()));
  protected readonly initials = computed(() => this.username().slice(0, 2).toUpperCase());
}
