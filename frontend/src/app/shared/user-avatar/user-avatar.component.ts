import { Component, computed, inject, input } from '@angular/core';
import { AvatarModule } from 'primeng/avatar';
import { AvatarService } from '../../core/avatar.service';

@Component({
  selector: 'app-user-avatar',
  imports: [AvatarModule],
  template: `
    @if (image(); as image) {
      <p-avatar [image]="image" shape="circle" [size]="size()" />
    } @else {
      <p-avatar [label]="initials()" shape="circle" [size]="size()" styleClass="initials" />
    }
  `,
  styles: `
    :host {
      display: inline-flex;
    }
  `,
})
export class UserAvatarComponent {
  private readonly avatars = inject(AvatarService);

  readonly username = input.required<string>();
  readonly size = input<'normal' | 'large' | 'xlarge'>('normal');

  protected readonly image = computed(() => this.avatars.avatarOf(this.username()));
  protected readonly initials = computed(() => this.username().slice(0, 2).toUpperCase());
}
