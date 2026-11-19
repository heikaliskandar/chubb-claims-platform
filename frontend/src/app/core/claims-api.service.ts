import { HttpClient, HttpEvent, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Approval,
  ApprovalView,
  ClaimDetail,
  ClaimDocument,
  DocumentCategory,
  ClaimStatus,
  ClaimSummary,
  PageResponse,
  SubmitClaimRequest,
} from './models';

const BASE = '/api/v1/claims';

export interface QueueFilter {
  status?: ClaimStatus;
  assignedTo?: string;
  closed?: boolean;
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
    if (filter.closed !== undefined) {
      params = params.set('closed', filter.closed);
    }
    return this.http.get<PageResponse<ClaimSummary>>(BASE, { params });
  }

  mine(closed: boolean, page: number, size: number): Observable<PageResponse<ClaimSummary>> {
    return this.http.get<PageResponse<ClaimSummary>>(`${BASE}/mine`, {
      params: { closed, page, size },
    });
  }

  approvals(view: ApprovalView, page: number, size: number): Observable<PageResponse<Approval>> {
    return this.http.get<PageResponse<Approval>>(`${BASE}/approvals`, {
      params: { view, page, size },
    });
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

  update(id: string, request: SubmitClaimRequest): Observable<ClaimDetail> {
    return this.http.put<ClaimDetail>(`${BASE}/${id}`, request);
  }

  withdraw(id: string, reason: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${BASE}/${id}/withdrawal`, { reason: reason || null });
  }

  removeDocument(claimId: string, documentId: string): Observable<void> {
    return this.http.delete<void>(`${BASE}/${claimId}/documents/${documentId}`);
  }

  documents(claimId: string): Observable<ClaimDocument[]> {
    return this.http.get<ClaimDocument[]>(`${BASE}/${claimId}/documents`);
  }

  uploadDocument(
    claimId: string,
    file: File,
    category: DocumentCategory,
  ): Observable<HttpEvent<ClaimDocument>> {
    const form = new FormData();
    form.append('file', file, file.name);
    form.append('category', category);
    return this.http.post<ClaimDocument>(`${BASE}/${claimId}/documents`, form, {
      reportProgress: true,
      observe: 'events',
    });
  }

  documentContent(claimId: string, documentId: string): Observable<Blob> {
    return this.http.get(`${BASE}/${claimId}/documents/${documentId}/content`, {
      responseType: 'blob',
    });
  }
}
