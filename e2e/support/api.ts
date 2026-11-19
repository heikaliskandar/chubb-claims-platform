import { APIRequestContext, APIResponse, expect, request } from '@playwright/test';

export const PASSWORD = process.env.E2E_PASSWORD ?? 'password';

export type Market = 'SG' | 'MY' | 'HK' | 'TH' | 'ID' | 'PH';
export type ClaimType = 'MOTOR' | 'PROPERTY';

export interface Claim {
  id: string;
  claimNumber: string;
  status: string;
  waitingOn: string;
  currency: string;
  claimedAmount: number;
  allowedActions: string[];
  informationRequests: {
    id: string;
    question: string;
    response: string | null;
  }[];
  assignedOfficerId: string | null;
}

export interface NewClaim {
  market?: Market;
  claimType?: ClaimType;
  claimedAmount?: number;
  description?: string;
}

export class ClaimsApi {
  private constructor(
    readonly username: string,
    private readonly context: APIRequestContext,
  ) {}

  static async as(username: string, baseURL: string): Promise<ClaimsApi> {
    const context = await request.newContext({
      baseURL,
      httpCredentials: { username, password: PASSWORD, send: 'always' },
    });
    return new ClaimsApi(username, context);
  }

  get raw(): APIRequestContext {
    return this.context;
  }

  async submit(claim: NewClaim = {}, headers: Record<string, string> = {}): Promise<Claim> {
    return this.json(
      await this.context.post('/api/v1/claims', {
        headers,
        data: {
          policyNumber: `POL-E2E-${Date.now()}`,
          market: claim.market ?? 'SG',
          claimType: claim.claimType ?? 'MOTOR',
          incidentDate: new Date(Date.now() - 86_400_000).toISOString().slice(0, 10),
          incidentDescription: claim.description ?? 'Rear-ended at a junction; bumper and tail light damaged.',
          claimedAmount: claim.claimedAmount ?? 4_500,
        },
      }),
    );
  }

  async get(id: string): Promise<Claim> {
    return this.json(await this.context.get(`/api/v1/claims/${id}`));
  }

  async assign(id: string): Promise<Claim> {
    return this.json(await this.context.post(`/api/v1/claims/${id}/assignment`));
  }

  async startReview(id: string): Promise<Claim> {
    return this.json(await this.context.post(`/api/v1/claims/${id}/review`));
  }

  async requestInformation(id: string, question: string): Promise<Claim> {
    return this.json(
      await this.context.post(`/api/v1/claims/${id}/information-requests`, {
        data: { question },
      }),
    );
  }

  async assess(id: string, assessedAmount: number): Promise<Claim> {
    return this.json(
      await this.context.post(`/api/v1/claims/${id}/assessment`, {
        data: { assessedAmount, notes: 'Assessed from repair quote.' },
      }),
    );
  }

  async approve(id: string, approvedAmount: number): Promise<Claim> {
    return this.json(
      await this.context.post(`/api/v1/claims/${id}/decision`, {
        data: {
          outcome: 'APPROVED',
          approvedAmount,
          reason: 'Covered under policy terms.',
        },
      }),
    );
  }

  async exposure(): Promise<{
    lines: {
      market: string;
      currency: string;
      openClaims: number;
      outstandingReserve: number;
    }[];
  }> {
    return this.json(await this.context.get('/api/v1/insights/exposure'));
  }

  async underReview(claim: NewClaim, claimant: ClaimsApi): Promise<Claim> {
    const submitted = await claimant.submit(claim);
    await this.assign(submitted.id);
    return this.startReview(submitted.id);
  }

  async dispose(): Promise<void> {
    await this.context.dispose();
  }

  private async json<T>(response: APIResponse): Promise<T> {
    expect(response.ok(), `${response.url()} -> ${response.status()} ${await response.text()}`).toBe(true);
    return response.json() as Promise<T>;
  }
}
