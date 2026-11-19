import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ChartModule } from 'primeng/chart';
import { TableModule } from 'primeng/table';
import { forkJoin } from 'rxjs';
import { InsightsApiService } from '../../../core/insights-api.service';
import {
  ClaimStatus,
  ExposureReport,
  PerformanceReport,
  WorkloadReport,
} from '../../../core/models';
import { PageHeaderComponent } from '../../../layout/page-header/page-header.component';
import { LabelPipe } from '../../../shared/label.pipe';
import { marketName } from '../../../shared/markets';
import { poll } from '../../../shared/polling';

interface Dashboard {
  exposure: ExposureReport;
  workload: WorkloadReport;
  performance: PerformanceReport;
}

const CHART_STATUSES: { status: ClaimStatus; color: string }[] = [
  { status: 'ASSIGNED', color: '#bfd3fe' },
  { status: 'UNDER_REVIEW', color: '#4f7df3' },
  { status: 'INFO_REQUESTED', color: '#f5a524' },
  { status: 'PENDING_APPROVAL', color: '#f2706b' },
  { status: 'APPROVED', color: '#12b886' },
];

@Component({
  selector: 'app-dashboard',
  imports: [
    ChartModule,
    TableModule,
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    LabelPipe,
    PageHeaderComponent,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  protected readonly marketName = marketName;
  protected readonly dashboard = signal<Dashboard | null>(null);

  protected readonly openClaims = computed(
    () => this.dashboard()?.workload.teams.reduce((sum, team) => sum + team.openClaims, 0) ?? 0,
  );
  protected readonly pendingApproval = computed(() => this.countByStatus('PENDING_APPROVAL'));
  protected readonly averageHours = computed(() => {
    const teams = this.dashboard()?.performance.teams ?? [];
    const decided = teams.reduce((sum, team) => sum + team.decided, 0);
    const hours = teams.reduce(
      (sum, team) => sum + (team.averageHoursToDecision ?? 0) * team.decided,
      0,
    );
    return decided === 0 ? null : hours / decided;
  });

  protected readonly chartData = computed(() => {
    const officers = this.dashboard()?.workload.teams.flatMap((team) => team.officers) ?? [];
    return {
      labels: officers.map((officer) => officer.officerId),
      datasets: CHART_STATUSES.map(({ status, color }) => ({
        label: status.charAt(0) + status.slice(1).toLowerCase().replaceAll('_', ' '),
        backgroundColor: color,
        borderRadius: 6,
        barThickness: 22,
        data: officers.map((officer) => officer.claimsByStatus[status] ?? 0),
      })),
    };
  });

  protected readonly chartOptions = {
    maintainAspectRatio: false,
    plugins: {
      legend: { position: 'bottom' as const, labels: { usePointStyle: true, boxWidth: 8 } },
    },
    scales: {
      x: { stacked: true, grid: { display: false } },
      y: { stacked: true, beginAtZero: true, ticks: { precision: 0 }, grid: { color: '#eef0f5' } },
    },
  };

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

  private countByStatus(status: ClaimStatus): number {
    return (
      this.dashboard()
        ?.workload.teams.flatMap((team) => team.officers)
        .reduce((sum, officer) => sum + (officer.claimsByStatus[status] ?? 0), 0) ?? 0
    );
  }
}
