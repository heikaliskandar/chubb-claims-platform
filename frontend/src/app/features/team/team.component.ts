import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ChartData } from 'chart.js';
import { forkJoin } from 'rxjs';
import { InsightsApiService } from '../../core/insights-api.service';
import { ClaimStatus, PerformanceReport, WorkloadReport } from '../../core/models';
import { PageHeaderComponent } from '../../layout/page-header/page-header.component';
import { BarChartComponent } from '../../shared/bar-chart/bar-chart.component';
import { LabelPipe } from '../../shared/label.pipe';
import { poll } from '../../shared/polling';
import { UserAvatarComponent } from '../../shared/user-avatar/user-avatar.component';

const CHART_STATUSES: { status: ClaimStatus; color: string }[] = [
  { status: 'ASSIGNED', color: '#bfd3fe' },
  { status: 'UNDER_REVIEW', color: '#4f7df3' },
  { status: 'INFO_REQUESTED', color: '#f5a524' },
  { status: 'PENDING_APPROVAL', color: '#f2706b' },
  { status: 'APPROVED', color: '#12b886' },
];

interface TeamData {
  workload: WorkloadReport;
  performance: PerformanceReport;
}

@Component({
  selector: 'app-team',
  imports: [
    DatePipe,
    DecimalPipe,
    LabelPipe,
    PageHeaderComponent,
    BarChartComponent,
    UserAvatarComponent,
  ],
  templateUrl: './team.component.html',
  styles: `
    .stats {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 20px;
      margin-bottom: 20px;
    }
    .stat {
      display: flex;
      align-items: center;
      gap: 14px;
    }
    .stat div {
      display: flex;
      flex-direction: column;
    }
    .stat strong {
      font-size: 22px;
      color: var(--app-heading);
    }
    .updated {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      color: var(--app-muted);
      font-size: 12px;
    }
    .updated .ms {
      font-size: 16px;
    }
    .layout-main-side {
      margin-bottom: 20px;
    }
    .team + .team {
      margin-top: 14px;
      padding-top: 14px;
      border-top: 1px solid var(--app-border);
    }
    .team-head {
      display: flex;
      justify-content: space-between;
      gap: 12px;
      margin-bottom: 6px;
    }
    .person {
      display: inline-flex;
      align-items: center;
      gap: 10px;
    }
    .statuses {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
    }
  `,
})
export class TeamComponent {
  protected readonly data = signal<TeamData | null>(null);

  protected readonly officers = computed(() =>
    (this.data()?.workload.teams ?? []).flatMap((team) =>
      team.officers.map((officer) => ({ ...officer, team: team.team })),
    ),
  );
  protected readonly openClaims = computed(() =>
    (this.data()?.workload.teams ?? []).reduce((sum, team) => sum + team.openClaims, 0),
  );
  protected readonly decided = computed(() =>
    (this.data()?.performance.teams ?? []).reduce((sum, team) => sum + team.decided, 0),
  );
  protected readonly averageHours = computed(() => {
    const teams = this.data()?.performance.teams ?? [];
    const decided = teams.reduce((sum, team) => sum + team.decided, 0);
    const hours = teams.reduce(
      (sum, team) => sum + (team.averageHoursToDecision ?? 0) * team.decided,
      0,
    );
    return decided === 0 ? null : hours / decided;
  });
  protected readonly chartData = computed<ChartData<'bar'>>(() => {
    const officers = this.officers();
    return {
      labels: officers.map((officer) => officer.officerId),
      datasets: CHART_STATUSES.map(({ status, color }) => ({
        label: status.charAt(0) + status.slice(1).toLowerCase().replaceAll('_', ' '),
        backgroundColor: color,
        borderRadius: 6,
        maxBarThickness: 28,
        data: officers.map((officer) => officer.claimsByStatus[status] ?? 0),
      })),
    };
  });

  constructor() {
    const api = inject(InsightsApiService);
    poll(() => forkJoin({ workload: api.workload(), performance: api.performance() }))
      .pipe(takeUntilDestroyed())
      .subscribe((data) => this.data.set(data));
  }

  protected statusesOf(
    counts: Partial<Record<ClaimStatus, number>>,
  ): { status: string; count: number }[] {
    return Object.entries(counts).map(([status, count]) => ({ status, count: count ?? 0 }));
  }
}
