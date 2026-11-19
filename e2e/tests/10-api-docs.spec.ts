import { expect, test } from '../support/fixtures';

test.describe('Live API documentation', () => {
  test('one portal documents both services from the running code', async ({ page }) => {
    await page.goto('/api/docs');
    await expect(page).toHaveURL(/\/swagger-ui\/index\.html/);
    await expect(page.getByRole('heading', { name: /Claims Service API/ })).toBeVisible();
    for (const tag of ['Approvals', 'Claims', 'Documents', 'Employees']) {
      await expect(page.locator('.opblock-tag', { hasText: tag }).first()).toBeVisible();
    }

    await page.locator('select').first().selectOption({ label: 'Insights service' });
    await expect(page.getByRole('heading', { name: /Insights Service API/ })).toBeVisible();
  });

  test('the specs are served from the same origin, with every operation documented', async ({ request }) => {
    for (const group of ['claims', 'insights']) {
      const response = await request.get(`/v3/api-docs/${group}`);
      expect(response.status(), group).toBe(200);
      const spec = await response.json();
      expect(spec.servers).toEqual([{ url: '/', description: 'Same origin as the web app' }]);
      for (const [path, operations] of Object.entries<Record<string, { summary?: string; responses: object }>>(
        spec.paths,
      )) {
        for (const [method, operation] of Object.entries(operations)) {
          expect(operation.summary, `${method} ${path}`).toBeTruthy();
          expect(Object.keys(operation.responses), `${method} ${path}`).toContain('401');
        }
      }
    }
  });

  test('try-it-out calls go through the same proxy and authentication as the app', async ({ request }) => {
    const anonymous = await request.get('/api/v1/me');
    expect(anonymous.status()).toBe(401);
    const signedIn = await request.get('/api/v1/me', {
      headers: { Authorization: `Basic ${Buffer.from('officer1:password').toString('base64')}` },
    });
    expect(await signedIn.json()).toMatchObject({ username: 'officer1', role: 'OFFICER' });
  });
});
