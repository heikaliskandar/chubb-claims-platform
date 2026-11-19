import { expect, test } from '../support/fixtures';

const pdf = (name: string) => ({
  name,
  mimeType: 'application/pdf',
  buffer: Buffer.from(`%PDF-1.4\n% ${name}\n`),
});
const png = (name: string) => ({
  name,
  mimeType: 'image/png',
  buffer: Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0x0d]),
});

test.describe('Supporting documents', () => {
  test('a claimant attaches documents when reporting a claim', async ({ app, page }) => {
    await app.signIn('claimant1');
    await app.open('/claims/new');

    await page.getByLabel('Policy number').fill('POL-SG-552201');
    await page.getByLabel('Incident date').fill(new Date(Date.now() - 86_400_000).toISOString().slice(0, 10));
    await page.getByLabel('Claimed amount').fill('2400');
    await page.getByLabel('What happened').fill('Windscreen cracked by a stone on the PIE.');
    await page.locator('input[type=file][multiple]').setInputFiles([png('windscreen.png'), pdf('quote.pdf')]);
    await page.getByLabel('Type of document for quote.pdf').selectOption('REPAIR_QUOTE');
    await page.getByRole('button', { name: 'Submit claim' }).click();

    await expect(page).toHaveURL(/\/claims\/[0-9a-f-]{36}$/);
    const documents = page.getByRole('region', { name: 'Supporting documents' });
    await expect(documents).toContainText('windscreen.png');
    await expect(documents).toContainText('Photo of the damage');
    await expect(documents).toContainText('quote.pdf');
    await expect(documents).toContainText('Repair quote');
  });

  test('the assigned officer sees a document the claimant added, and can download it', async ({
    app,
    apiAs,
    page,
    newSession,
  }) => {
    const officer = await apiAs('officer1');
    const claim = await officer.underReview({ claimedAmount: 1_800 }, await apiAs('claimant1'));

    const claimant = await newSession();
    await claimant.signIn('claimant1');
    await claimant.openClaim(claim.id, claim.claimNumber);
    const claimantDocuments = claimant.page.getByRole('region', { name: 'Supporting documents' });
    await claimantDocuments.getByLabel('What are you adding?').selectOption('POLICE_REPORT');
    await claimantDocuments.locator('input[type=file]').setInputFiles(pdf('police-report.pdf'));
    await expect(claimantDocuments).toContainText('police-report.pdf');
    await expect(claimantDocuments).toContainText('Police report');

    await app.signIn('officer1');
    await app.openClaim(claim.id, claim.claimNumber);
    const documents = page.getByRole('region', { name: 'Supporting documents' });
    await expect(documents).toContainText('police-report.pdf');
    const download = page.waitForEvent('download');
    await documents.getByRole('button', { name: 'Download police-report.pdf' }).click();
    expect((await download).suggestedFilename()).toBe('police-report.pdf');
  });

  test('a file pretending to be an image is refused by the server', async ({ app, apiAs, page }) => {
    const claim = await (await apiAs('claimant1')).submit();
    await app.signIn('claimant1');
    await app.openClaim(claim.id, claim.claimNumber);

    const documents = page.getByRole('region', { name: 'Supporting documents' });
    await documents.locator('input[type=file]').setInputFiles({
      name: 'photo.png',
      mimeType: 'image/png',
      buffer: Buffer.from('<html><script>alert(1)</script></html>'),
    });

    await expect(page.getByRole('status').filter({ hasText: 'Unsupported file type' })).toBeVisible();
    await expect(documents).not.toContainText('photo.png');
  });

  test("documents follow the claim's visibility, and closed claims accept no more", async ({ app, apiAs, page }) => {
    const owner = await apiAs('claimant1');
    const claim = await owner.submit();
    const upload = await owner.raw.post(`/api/v1/claims/${claim.id}/documents`, {
      multipart: { file: pdf('receipt.pdf'), category: 'INVOICE' },
    });
    expect(upload.status()).toBe(201);
    const document = await upload.json();

    const stranger = await apiAs('claimant2');
    expect((await stranger.raw.get(`/api/v1/claims/${claim.id}/documents`)).status()).toBe(404);
    expect((await stranger.raw.get(`/api/v1/claims/${claim.id}/documents/${document.id}/content`)).status()).toBe(404);

    const officer = await apiAs('officer1');
    const closed = await officer.underReview({ claimedAmount: 900 }, owner);
    await officer.raw.post(`/api/v1/claims/${closed.id}/decision`, {
      data: { outcome: 'REJECTED', reason: 'Outside the policy period.' },
    });
    const late = await owner.raw.post(`/api/v1/claims/${closed.id}/documents`, {
      multipart: { file: pdf('late.pdf'), category: 'OTHER' },
    });
    expect(late.status()).toBe(403);

    await app.signIn('claimant1');
    await app.openClaim(closed.id, closed.claimNumber);
    const documents = page.getByRole('region', { name: 'Supporting documents' });
    await expect(documents).toContainText('No documents yet');
    await expect(documents.locator('input[type=file]')).toHaveCount(0);
  });
});
