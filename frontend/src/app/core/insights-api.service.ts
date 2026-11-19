import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ExposureReport, PerformanceReport, WorkloadReport } from './models';

const BASE = '/api/v1/insights';

@Injectable({ providedIn: 'root' })
export class InsightsApiService {
  private readonly http = inject(HttpClient);

  exposure(): Observable<ExposureReport> {
    return this.http.get<ExposureReport>(`${BASE}/exposure`);
  }

  workload(): Observable<WorkloadReport> {
    return this.http.get<WorkloadReport>(`${BASE}/workload`);
  }

  performance(): Observable<PerformanceReport> {
    return this.http.get<PerformanceReport>(`${BASE}/performance`);
  }
}
