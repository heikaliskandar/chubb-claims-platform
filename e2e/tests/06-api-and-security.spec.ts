import { expect, test } from '../support/fixtures';

test.describe('API contract and security', () => {
  test('submitting twice with the same Idempotency-Key creates one claim', async ({ apiAs }) => {
    const claimant = await apiAs('claimant1');
    const key = crypto.randomUUID();
    const first = await claimant.submit({ claimedAmount: 1_234 }, { 'Idempotency-Key': key });
    const second = await claimant.submit({ claimedAmount: 1_234 }, { 'Idempotency-Key': key });
    expect(second.id).toBe(first.id);
  });

  test('validation errors are returned as RFC 7807 Problem Details', async ({ apiAs }) => {
    const response = await (await apiAs('claimant1')).raw.post('/api/v1/claims', { data: { claimedAmount: -1 } });
    expect(response.status()).toBe(400);
    expect(response.headers()['content-type']).toContain('application/problem+json');
    const problem = await response.json();
    expect(problem.title).toBeTruthy();
    expect(JSON.stringify(problem)).not.toMatch(/Exception|at com\.|SQL/);
  });

  test('requests without credentials are rejected without a browser login prompt', async ({ request }) => {
    const response = await request.get('/api/v1/claims/mine');
    expect(response.status()).toBe(401);
    expect(response.headers()['www-authenticate']).toBeUndefined();
  });

  test('a role cannot call another role\'s endpoints', async ({ apiAs }) => {
    const claimant = await apiAs('claimant1');
    expect((await claimant.raw.get('/api/v1/claims')).status()).toBe(403);
    const officer = await apiAs('officer1');
    expect((await officer.raw.get('/api/v1/claims/mine')).status()).toBe(403);
  });

  test('the web app is served with hardened security and caching headers', async ({ request }) => {
    const page = await request.get('/');
    const headers = page.headers();
    expect(headers['content-security-policy']).toContain("default-src 'self'");
    expect(headers['content-security-policy']).toContain("frame-ancestors 'none'");
    expect(headers['x-frame-options']).toBe('DENY');
    expect(headers['x-content-type-options']).toBe('nosniff');
    expect(headers['referrer-policy']).toBe('strict-origin-when-cross-origin');
    expect(headers['cache-control']).toBe('no-cache');
    expect(headers['server']).toBe('nginx');

    const bundle = (await page.text()).match(/main-[A-Z0-9]{8}\.js/)![0];
    const asset = await request.get(`/${bundle}`);
    expect(asset.headers()['cache-control']).toContain('immutable');
  });
});
