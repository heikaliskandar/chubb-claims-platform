import { expect, test } from '../support/fixtures';

test.describe('R1 Claimants report an incident', () => {
  test('claimant submits a claim and lands on its tracking page @responsive', async ({ app, page }) => {
    await app.signIn('claimant1');
    await app.open('/claims/new');

    await page.getByRole('radio', { name: 'Property' }).click();
    await page.getByLabel('Market').selectOption('MY');
    await page.getByLabel('Policy number').fill('POL-MY-778812');
    await page.getByLabel('Incident date').fill(new Date(Date.now() - 2 * 86_400_000).toISOString().slice(0, 10));
    await page.getByLabel('Claimed amount').fill('12500');
    await page.getByLabel('What happened').fill('Burst pipe flooded the kitchen and damaged the cabinets.');
    await page.getByRole('button', { name: 'Submit claim' }).click();

    await expect(page).toHaveURL(/\/claims\/[0-9a-f-]{36}$/);
    await expect(page.getByRole('heading', { level: 1, name: /^CLM-MY-\d{4}-\d{6}$/ })).toBeVisible();
    await app.expectStatus('Submitted');
    await expect(page.getByText(/MYR\s?12,500\.00/).first()).toBeVisible();
  });

  test('invalid input is blocked before it reaches the server', async ({ app, page }) => {
    await app.signIn('claimant1');
    await app.open('/claims/new');
    await page.getByLabel('Claimed amount').fill('-5');
    await page.getByRole('button', { name: 'Submit claim' }).click();

    const summary = page.getByRole('alert');
    await expect(summary).toBeFocused();
    const policyLink = summary.getByRole('link', {
      name: 'Enter your policy number.',
    });
    await expect(policyLink).toBeVisible();
    await expect(summary.getByRole('link', { name: /Enter an amount like 1250/ })).toBeVisible();
    await policyLink.click();
    await expect(page.getByLabel('Policy number')).toBeFocused();
    await expect(page).toHaveURL(/\/claims\/new$/);
  });
});

test.describe('R2 Claimants track their claim', () => {
  test('a submitted claim appears in My claims and shows its activity', async ({ app, apiAs, page }) => {
    const claimant = await apiAs('claimant2');
    const claim = await claimant.submit({ market: 'HK', claimedAmount: 8_800 });

    await app.signIn('claimant2');
    const row = page.getByRole('row').filter({ hasText: claim.claimNumber });
    await expect(row).toBeVisible();
    await row.click();

    await expect(page.getByRole('heading', { level: 1, name: claim.claimNumber })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Activity' })).toBeVisible();
  });

  test('claims of other claimants are not visible', async ({ apiAs }) => {
    const owner = await apiAs('claimant1');
    const other = await apiAs('claimant2');
    const claim = await owner.submit();
    const response = await other.raw.get(`/api/v1/claims/${claim.id}`);
    expect(response.status()).toBe(404);
  });
});

test.describe('R3 Claimants provide information when asked', () => {
  test('claimant sees the request, answers it, and the claim returns to the officer', async ({ app, apiAs, page }) => {
    const claimant = await apiAs('claimant2');
    const officer = await apiAs('officer1');
    const claim = await officer.underReview({ claimedAmount: 3_200 }, claimant);
    await officer.requestInformation(claim.id, 'Please upload the police report number.');

    await app.signIn('claimant2');
    const banner = page.getByText('We need more information');
    await expect(banner).toBeVisible();
    await page.getByRole('link', { name: claim.claimNumber }).click();

    await expect(page.getByText('Please upload the police report number.')).toBeVisible();
    await app.act('Respond', { text: 'Police report SPF-2026-118833.' });
    await app.expectStatus('Under review');

    const updated = await officer.get(claim.id);
    expect(updated.waitingOn).toBe('ASSIGNED_OFFICER');
    expect(updated.informationRequests[0].response).toBe('Police report SPF-2026-118833.');
  });
});
