import { Component, input } from '@angular/core';

@Component({
  selector: 'app-logo',
  template: `
    <svg viewBox="0 0 132 28" role="img" aria-label="Chubb">
      <text x="0" y="23.5" textLength="132" lengthAdjust="spacingAndGlyphs">CHUBB</text>
    </svg>
    @if (product()) {
      <span class="product">{{ product() }}</span>
    }
  `,
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      gap: 0.6em;
      color: inherit;
      line-height: 1;
    }
    svg {
      height: 1em;
      width: auto;
      overflow: visible;
    }
    text {
      fill: currentColor;
      font-family: 'Plus Jakarta Sans', system-ui, sans-serif;
      font-size: 31px;
      font-weight: 800;
    }
    .product {
      padding-left: 0.6em;
      border-left: 1px solid currentColor;
      font-size: 0.55em;
      font-weight: 600;
      letter-spacing: 0.04em;
      opacity: 0.75;
    }
  `,
})
export class LogoComponent {
  readonly product = input<string>();
}
