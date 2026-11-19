import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ClaimDetail, ClaimStatus, ClaimSummary, PageResponse, SubmitClaimRequest } from './models';

const BASE = '/api/v1/claims';

export interface QueueFilter {
  status?: ClaimStatus;
  assignedTo?: string;
  page: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class ClaimsApiService {
  private readonly http = inject(HttpClient);

  submit(request: SubmitClaimRequest, idempotencyKey: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(BASE, request, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  queue(filter: QueueFilter): Observable<PageResponse<ClaimSummary>> {
    let params = new HttpParams().set('page', filter.page).set('size', filter.size);
    if (filter.status) {
      params = params.set('status', filter.status);
    }
    if (filter.assignedTo) {
      params = params.set('assignedTo', filter.assignedTo);
    }
    return this.http.get<PageResponse<ClaimSummary>>(BASE, { params });
  }

  mine(page: number, size: number): Observable<PageResponse<ClaimSummary>> {
    return this.http.get<PageResponse<ClaimSummary>>(`${BASE}/mine`, { params: { page, size } });
  }

  get(id: string): Observable<ClaimDetail> {
    return this.http.get<ClaimDetail>(`${BASE}/${id}`);
  }

  assign(id: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${BASE}/${id}/assignment`, null);
  }

  startReview(id: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${BASE}/${id}/review`, null);
  }

  requestInformation(id: string, question: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${BASE}/${id}/information-requests`, { question });
  }

  respond(id: string, requestId: string, response: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${BASE}/${id}/information-requests/${requestId}/response`, {
      response,
    });
  }

  assess(id: string, assessedAmount: number, notes: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${BASE}/${id}/assessment`, { assessedAmount, notes });
  }

  approve(id: string, approvedAmount: number, reason: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${BASE}/${id}/decision`, {
      outcome: 'APPROVED',
      approvedAmount,
      reason,
    });
  }

  reject(id: string, reason: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${BASE}/${id}/decision`, { outcome: 'REJECTED', reason });
  }

  reviewReferral(
    id: string,
    outcome: 'APPROVE' | 'RETURN' | 'REJECT',
    comment: string,
  ): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${BASE}/${id}/approval`, { outcome, comment });
  }

  settle(id: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${BASE}/${id}/settlement`, null);
  }
}
