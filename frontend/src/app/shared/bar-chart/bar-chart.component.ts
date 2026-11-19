import { Component, DestroyRef, effect, ElementRef, inject, input, viewChild } from '@angular/core';
import { Chart, ChartData } from 'chart.js/auto';
import { ThemeService } from '../../core/theme.service';

@Component({
  selector: 'app-bar-chart',
  template: `<canvas #canvas></canvas>`,
  styles: `
    :host {
      position: relative;
      display: block;
      height: 300px;
    }
  `,
})
export class BarChartComponent {
  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private readonly theme = inject(ThemeService);
  private chart?: Chart;

  readonly data = input.required<ChartData<'bar'>>();

  constructor() {
    effect(() => {
      const data = this.data();
      this.theme.dark();
      this.chart?.destroy();
      this.chart = new Chart(this.canvas().nativeElement, {
        type: 'bar',
        data,
        options: chartOptions(),
      });
    });
    inject(DestroyRef).onDestroy(() => this.chart?.destroy());
  }
}

function chartOptions() {
  const styles = getComputedStyle(document.documentElement);
  const muted = styles.getPropertyValue('--app-muted').trim();
  const grid = styles.getPropertyValue('--app-border').trim();
  const font = { family: "'Plus Jakarta Sans', system-ui, sans-serif", size: 12 };
  return {
    maintainAspectRatio: false,
    animation: false as const,
    plugins: {
      legend: {
        position: 'bottom' as const,
        labels: { usePointStyle: true, boxWidth: 8, color: muted, font },
      },
    },
    scales: {
      x: { stacked: true, grid: { display: false }, ticks: { color: muted, font } },
      y: {
        stacked: true,
        beginAtZero: true,
        ticks: { precision: 0, color: muted, font },
        grid: { color: grid },
      },
    },
  };
}
