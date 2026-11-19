import { expect, test } from '../support/fixtures';

test.describe('R9 Managers see outstanding claims and liability exposure', () => {
  test('dashboard shows exposure per currency without mixing currencies @responsive', async ({ app, page }) => {
    await app.signIn('manager1');
    await expect(page.getByRole('heading', { name: /Outstanding reserve/ })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Exposure by market' })).toBeVisible();
    await expect(page.getByText(/Updated \d{2}:\d{2}:\d{2}/)).toBeVisible();
  });

  test('a new assessment reaches the exposure read model asynchronously via Kafka', async ({ apiAs }) => {
    const manager = await apiAs('manager1');
    const reserveFor = async () =>
      (await manager.exposure()).lines
        .filter((line) => line.market === 'TH')
        .reduce((sum, line) => sum + Number(line.outstandingReserve), 0);
    const before = await reserveFor();

    const officer = await apiAs('officer1');
    const claim = await officer.underReview({ market: 'TH', claimType: 'MOTOR', claimedAmount: 80_000 }, await apiAs('claimant1'));
    await officer.assess(claim.id, 77_777);

    await expect.poll(reserveFor, { timeout: 20_000 }).toBeCloseTo(before + 77_777, 2);
  });

  test('claimants and officers cannot read company-wide exposure', async ({ apiAs }) => {
    for (const user of ['claimant1', 'officer1']) {
      const response = await (await apiAs(user)).raw.get('/api/v1/insights/exposure');
      expect(response.status(), user).toBe(403);
    }
  });
});

test.describe('R4 Staff see team workload and performance', () => {
  test('team performance page shows workload by officer', async ({ app, page }) => {
    await app.signIn('lead1');
    await app.open('/team');
    await expect(page.getByRole('heading', { level: 1, name: 'Team performance' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Workload by officer' })).toBeVisible();
    await expect(page.getByRole('row').filter({ hasText: 'officer1' })).toBeVisible();
  });
});
