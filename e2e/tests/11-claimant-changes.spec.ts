import { expect, test } from '../support/fixtures';

const pdf = (name: string) => ({ name, mimeType: 'application/pdf', buffer: Buffer.from(`%PDF-1.4\n% ${name}\n`) });

test.describe('Claimants can change a claim until an officer picks it up', () => {
  test('the claimant edits the details; the market stays fixed and the change is recorded', async ({
    app,
    apiAs,
    page,
  }) => {
    const claim = await (await apiAs('claimant1')).submit({ market: 'MY', claimedAmount: 2_000 });
    await app.signIn('claimant1');
    await app.openClaim(claim.id, claim.claimNumber);
    await expect(page.getByRole('note')).toContainText('You can still change this claim');

    await app.act('Edit claim');
    await expect(page).toHaveURL(new RegExp(`/claims/${claim.id}/edit$`));
    await expect(page.getByLabel('Market')).toBeDisabled();
    await expect(page.getByLabel('Claimed amount')).toHaveValue('2000');
    await page.getByLabel('Claimed amount').fill('2350.50');
    await page
      .getByLabel('What happened')
      .fill('Motorcycle hit at a junction in Sentul; front fork and lights damaged.');
    await page.getByRole('button', { name: 'Save changes' }).click();

    await expect(page).toHaveURL(new RegExp(`/claims/${claim.id}$`));
    await expect(page.getByText(/MYR\s?2,350\.50/).first()).toBeVisible();
    await expect(page.getByText('front fork and lights damaged')).toBeVisible();
    await expect(page.getByRole('listitem').filter({ hasText: 'Details updated' })).toBeVisible();
  });

  test('the claimant removes a duplicate document', async ({ app, apiAs, page }) => {
    const owner = await apiAs('claimant1');
    const claim = await owner.submit();
    for (const name of ['photo.pdf', 'photo-copy.pdf']) {
      await owner.raw.post(`/api/v1/claims/${claim.id}/documents`, {
        multipart: { file: pdf(name), category: 'PHOTO' },
      });
    }

    await app.signIn('claimant1');
    await app.openClaim(claim.id, claim.claimNumber);
    const documents = page.getByRole('region', { name: 'Supporting documents' });
    await documents.getByRole('button', { name: 'Remove photo-copy.pdf' }).click();
    await page
      .getByRole('dialog', { name: 'Remove document' })
      .getByRole('button', { name: 'Remove document' })
      .click();

    await expect(documents).not.toContainText('photo-copy.pdf');
    await expect(documents).toContainText('photo.pdf');
  });

  test('the claimant withdraws the claim, which then appears in History', async ({ app, apiAs, page }) => {
    const claim = await (await apiAs('claimant1')).submit();
    await app.signIn('claimant1');
    await app.openClaim(claim.id, claim.claimNumber);

    await app.act('Withdraw claim', { text: 'Settled privately with the other driver.' });
    await app.expectStatus('Withdrawn');
    await expect(page.locator('app-page-header').getByRole('button')).toHaveCount(0);
    await expect(page.getByRole('region', { name: 'Supporting documents' }).locator('input[type=file]')).toHaveCount(0);

    await app.open('/claims/history');
    await expect(page.getByRole('row').filter({ hasText: claim.claimNumber })).toContainText('Withdrawn');
  });

  test('once an officer picks it up, the claim is locked for the claimant', async ({ app, apiAs, page }) => {
    const owner = await apiAs('claimant1');
    const claim = await owner.submit({ claimedAmount: 1_500 });
    const upload = await owner.raw.post(`/api/v1/claims/${claim.id}/documents`, {
      multipart: { file: pdf('receipt.pdf'), category: 'INVOICE' },
    });
    const document = await upload.json();
    await (await apiAs('officer1')).assign(claim.id);

    await app.signIn('claimant1');
    await app.openClaim(claim.id, claim.claimNumber);
    await expect(page.locator('app-page-header').getByRole('button', { name: 'Edit claim' })).toHaveCount(0);
    await expect(page.locator('app-page-header').getByRole('button', { name: 'Withdraw claim' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Remove receipt.pdf' })).toHaveCount(0);

    await app.open(`/claims/${claim.id}/edit`);
    await expect(page.getByRole('alert')).toContainText('can no longer be edited');

    const edit = await owner.raw.put(`/api/v1/claims/${claim.id}`, {
      data: {
        policyNumber: 'POL-1',
        market: 'SG',
        claimType: 'MOTOR',
        incidentDate: '2026-11-01',
        incidentDescription: 'Too late',
        claimedAmount: 1,
      },
    });
    expect(edit.status()).toBe(409);
    expect((await owner.raw.post(`/api/v1/claims/${claim.id}/withdrawal`, { data: {} })).status()).toBe(409);
    expect((await owner.raw.delete(`/api/v1/claims/${claim.id}/documents/${document.id}`)).status()).toBe(403);
  });
});
