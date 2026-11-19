import { expect, test } from '../support/fixtures';

test.describe('R5-R7 Staff pick up, review, assess and settle claims', () => {
  test('officer takes a claim from the queue through to settlement, and the claimant sees the decision', async ({
    app,
    apiAs,
    page,
    newSession,
  }) => {
    const claimant = await apiAs('claimant1');
    const claim = await claimant.submit({ market: 'SG', claimedAmount: 6_400 });

    await app.signIn('officer1');
    await expect(page.getByRole('heading', { level: 1, name: 'Claim queue' })).toBeVisible();
    await expect(page.getByRole('row').first()).toContainText('Submitted');
    await app.openClaim(claim.id, claim.claimNumber);

    await app.act('Assign to me');
    await app.expectStatus('Assigned');
    await app.act('Start review');
    await app.expectStatus('Under review');
    await app.act('Assess', {
      amount: 6_000,
      text: 'Workshop quote verified.',
    });
    await expect(page.getByRole('heading', { name: 'Assessments' })).toBeVisible();
    await app.act('Approve', {
      amount: 6_000,
      text: 'Covered under comprehensive motor policy.',
    });
    await app.expectStatus('Approved');
    await app.act('Mark settled');
    await app.expectStatus('Settled');

    const claimantApp = await newSession();
    const claimantPage = claimantApp.page;
    await claimantApp.signIn('claimant1');
    await claimantApp.open('/claims/history');
    await claimantPage.getByRole('row').filter({ hasText: claim.claimNumber }).click();
    await expect(claimantPage.getByRole('heading', { name: 'Decision' })).toBeVisible();
    await expect(
      claimantPage.getByRole('paragraph').filter({ hasText: 'Covered under comprehensive motor policy.' }),
    ).toBeVisible();
  });

  test('an officer can reject a claim with a reason', async ({ app, apiAs }) => {
    const officer = await apiAs('officer1');
    const claim = await officer.underReview({ claimedAmount: 2_000 }, await apiAs('claimant1'));

    await app.signIn('officer1');
    await app.openClaim(claim.id, claim.claimNumber);
    await app.act('Reject', {
      text: 'Incident falls outside the policy period.',
    });
    await app.expectStatus('Rejected');
  });

  test('only one officer can pick up a claim when two try at once (optimistic locking)', async ({ apiAs }) => {
    const claim = await (await apiAs('claimant1')).submit();
    const [first, second] = await Promise.all([apiAs('officer1'), apiAs('officer3')]);
    const responses = await Promise.all([
      first.raw.post(`/api/v1/claims/${claim.id}/assignment`),
      second.raw.post(`/api/v1/claims/${claim.id}/assignment`),
    ]);
    const statuses = responses.map((response) => response.status()).sort();
    expect(statuses.filter((status) => status === 200)).toHaveLength(1);
    expect([404, 409]).toContain(statuses.find((status) => status !== 200));
  });

  test('actions are only offered when the rules allow them', async ({ app, apiAs, page }) => {
    const claim = await (await apiAs('claimant1')).submit();
    await app.signIn('officer1');
    await app.openClaim(claim.id, claim.claimNumber);
    const header = page.locator('app-page-header');
    await expect(header.getByRole('button', { name: 'Assign to me' })).toBeVisible();
    await expect(header.getByRole('button', { name: 'Approve' })).toHaveCount(0);
    await expect(header.getByRole('button', { name: 'Mark settled' })).toHaveCount(0);
  });
});
