import { CurrencyPipe, DatePipe, DecimalPipe, KeyValuePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';
import { forkJoin } from 'rxjs';
import { InsightsApiService } from '../../../core/insights-api.service';
import { ExposureReport, PerformanceReport, WorkloadReport } from '../../../core/models';
import { LabelPipe } from '../../../shared/label.pipe';
import { poll } from '../../../shared/polling';

interface Dashboard {
  exposure: ExposureReport;
  workload: WorkloadReport;
  performance: PerformanceReport;
}

@Component({
  selector: 'app-dashboard',
  imports: [MatCardModule, CurrencyPipe, DatePipe, DecimalPipe, KeyValuePipe, LabelPipe],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  protected readonly dashboard = signal<Dashboard | null>(null);

  constructor() {
    const api = inject(InsightsApiService);
    poll(() =>
      forkJoin({
        exposure: api.exposure(),
        workload: api.workload(),
        performance: api.performance(),
      }),
    )
      .pipe(takeUntilDestroyed())
      .subscribe((dashboard) => this.dashboard.set(dashboard));
  }
}
