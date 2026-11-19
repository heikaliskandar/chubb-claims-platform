import { expect, test } from '../support/fixtures';

const OFFICER_SGD_LIMIT = 10_000;

test.describe('R7 Approvals above an officer\'s authority need someone above them (four-eyes)', () => {
  test('a recommendation above the limit is referred, reviewed by the team lead and approved', async ({
    app,
    apiAs,
    page,
    newSession,
  }) => {
    const officer = await apiAs('officer1');
    const claim = await officer.underReview({ market: 'SG', claimedAmount: 40_000 }, await apiAs('claimant1'));
    await officer.assess(claim.id, 38_000);

    await app.signIn('officer1');
    await app.openClaim(claim.id, claim.claimNumber);
    await app.act('Approve', { amount: OFFICER_SGD_LIMIT + 28_000, text: 'Total loss, valuation attached.' });
    await app.expectStatus('Pending approval');
    await expect(page.getByRole('heading', { name: 'Referred for approval' })).toBeVisible();

    await app.open('/approvals');
    await expect(page.getByRole('tab')).toHaveCount(0);
    const mine = page.getByRole('row').filter({ hasText: claim.claimNumber });
    await expect(mine).toContainText('Pending');
    await expect(mine.getByRole('button', { name: 'Review' })).toHaveCount(0);

    const lead = await newSession();
    const leadPage = lead.page;
    await lead.signIn('lead1');
    await lead.open('/approvals');
    await expect(leadPage.getByRole('tab', { name: 'To review' })).toHaveAttribute('aria-selected', 'true');
    const row = leadPage.getByRole('row').filter({ hasText: claim.claimNumber });
    await expect(row).toContainText('officer1');
    await row.getByRole('button', { name: 'Review' }).click();
    await lead.act('Approve', { text: 'Agree with valuation.' });
    await lead.expectStatus('Approved');

    await lead.open('/approvals');
    await leadPage.getByRole('tab', { name: 'Decided' }).click();
    await expect(leadPage.getByRole('row').filter({ hasText: claim.claimNumber })).toContainText('Approved');
  });

  test('a lead outside the officer\'s reporting line cannot see or review the referral', async ({ apiAs }) => {
    const officer = await apiAs('officer1');
    const claim = await officer.underReview({ market: 'SG', claimedAmount: 30_000 }, await apiAs('claimant1'));
    await officer.assess(claim.id, 25_000);
    await officer.approve(claim.id, 25_000);

    const otherLead = await apiAs('lead2');
    const queue = await (await otherLead.raw.get('/api/v1/claims/approvals?view=TO_REVIEW&size=100')).json();
    expect(queue.content.map((item: { claimId: string }) => item.claimId)).not.toContain(claim.id);
    const review = await otherLead.raw.post(`/api/v1/claims/${claim.id}/approval`, {
      data: { outcome: 'APPROVE', comment: 'Trying to approve outside my line.' },
    });
    expect(review.status()).toBe(404);
  });

  test('an approval within the officer\'s own limit is final without referral', async ({ apiAs }) => {
    const officer = await apiAs('officer1');
    const claim = await officer.underReview({ market: 'SG', claimedAmount: 5_000 }, await apiAs('claimant1'));
    await officer.assess(claim.id, 5_000);
    const decided = await officer.approve(claim.id, OFFICER_SGD_LIMIT - 5_000);
    expect(decided.status).toBe('APPROVED');
  });
});
