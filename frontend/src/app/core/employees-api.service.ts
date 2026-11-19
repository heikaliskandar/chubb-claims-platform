import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { EmployeeDetail, EmployeeSummary, PageResponse } from './models';

const BASE = '/api/v1/employees';

@Injectable({ providedIn: 'root' })
export class EmployeesApiService {
  private readonly http = inject(HttpClient);

  directory(query: string, page: number, size: number): Observable<PageResponse<EmployeeSummary>> {
    const params = new HttpParams().set('q', query).set('page', page).set('size', size);
    return this.http.get<PageResponse<EmployeeSummary>>(BASE, { params });
  }

  profile(username: string): Observable<EmployeeDetail> {
    return this.http.get<EmployeeDetail>(`${BASE}/${username}`);
  }

  updateMyContact(phone: string, officeLocation: string): Observable<EmployeeDetail> {
    return this.http.patch<EmployeeDetail>(`${BASE}/me`, { phone, officeLocation });
  }
}
