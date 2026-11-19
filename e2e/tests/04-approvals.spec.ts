import { expect, test } from '../support/fixtures';

const OFFICER_SGD_LIMIT = 10_000;

test.describe("R7 Approvals above an officer's authority need someone above them (four-eyes)", () => {
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
    await app.act('Approve', {
      amount: OFFICER_SGD_LIMIT + 28_000,
      text: 'Total loss, valuation attached.',
    });
    await app.expectStatus('Pending approval');
    const panel = page.getByRole('region', { name: 'Approval needed' });
    await expect(panel).toContainText('You recommended paying SGD');
    await expect(panel).toContainText('above your approval limit of SGD');
    await expect(panel.getByRole('button', { name: 'Approve payment' })).toHaveCount(0);

    await app.open('/approvals');
    await expect(page.getByRole('tab')).toHaveCount(0);
    const mine = page.getByRole('listitem').filter({ hasText: claim.claimNumber });
    await expect(mine).toContainText('Waiting for decision');
    await expect(mine.getByRole('link', { name: 'View claim' })).toBeVisible();
    await expect(mine.getByRole('link', { name: 'Review and decide' })).toHaveCount(0);

    const lead = await newSession();
    const leadPage = lead.page;
    await lead.signIn('lead1');
    await lead.open('/approvals');
    await expect(leadPage.getByRole('tab', { name: 'Waiting for me' })).toHaveAttribute('aria-selected', 'true');
    const card = leadPage.getByRole('listitem').filter({ hasText: claim.claimNumber });
    await expect(card).toContainText('Recommended by Daniel Lim');
    await card.getByRole('link', { name: 'Review and decide' }).click();

    const leadPanel = leadPage.getByRole('region', { name: 'Approval needed' });
    await expect(leadPanel).toContainText('Daniel Lim recommends paying SGD');
    await expect(leadPanel).toContainText("above Daniel's approval limit of SGD");
    await expect(leadPage.locator('app-page-header').getByRole('button', { name: /Approve/ })).toHaveCount(0);
    await lead.decide('Approve payment', 'Agree with valuation.');
    await lead.expectStatus('Approved');
    await expect(leadPage.getByText(/Decided by Arjun Mehta/)).toBeVisible();

    await lead.open('/approvals');
    await leadPage.getByRole('tab', { name: 'Decided' }).click();
    const decided = leadPage.getByRole('listitem').filter({ hasText: claim.claimNumber });
    await expect(decided).toContainText('Approved by Arjun Mehta');
  });

  test('a team lead can send a referral back, and the officer sees why', async ({ app, apiAs, page, newSession }) => {
    const officer = await apiAs('officer1');
    const claim = await officer.underReview({ market: 'SG', claimedAmount: 30_000 }, await apiAs('claimant1'));
    await officer.assess(claim.id, 26_000);
    await officer.approve(claim.id, 26_000);

    const lead = await newSession();
    await lead.signIn('lead1');
    await lead.openClaim(claim.id, claim.claimNumber);
    await lead.decide('Send back for changes', 'Please attach the second repair quote.');
    await lead.expectStatus('Under review');

    await app.signIn('officer1');
    await app.open('/approvals');
    const card = page.getByRole('listitem').filter({ hasText: claim.claimNumber });
    await expect(card).toContainText('Sent back to officer');
    await expect(card).toContainText('Sent back by Arjun Mehta');
    await card.getByRole('link', { name: 'View claim' }).click();
    await expect(page.getByText('Please attach the second repair quote.').first()).toBeVisible();
  });

  test("a lead outside the officer's reporting line cannot see or review the referral", async ({ apiAs }) => {
    const officer = await apiAs('officer1');
    const claim = await officer.underReview({ market: 'SG', claimedAmount: 30_000 }, await apiAs('claimant1'));
    await officer.assess(claim.id, 25_000);
    await officer.approve(claim.id, 25_000);

    const otherLead = await apiAs('lead2');
    const queue = await (await otherLead.raw.get('/api/v1/claims/approvals?view=TO_REVIEW&size=100')).json();
    expect(queue.content.map((item: { claimId: string }) => item.claimId)).not.toContain(claim.id);
    const review = await otherLead.raw.post(`/api/v1/claims/${claim.id}/approval`, {
      data: {
        outcome: 'APPROVE',
        comment: 'Trying to approve outside my line.',
      },
    });
    expect(review.status()).toBe(404);
  });

  test("an approval within the officer's own limit is final without referral", async ({ apiAs }) => {
    const officer = await apiAs('officer1');
    const claim = await officer.underReview({ market: 'SG', claimedAmount: 5_000 }, await apiAs('claimant1'));
    await officer.assess(claim.id, 5_000);
    const decided = await officer.approve(claim.id, OFFICER_SGD_LIMIT - 5_000);
    expect(decided.status).toBe('APPROVED');
  });
});
