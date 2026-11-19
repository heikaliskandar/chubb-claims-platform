import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '../support/fixtures';

const pages = [
  { user: 'claimant1', paths: ['/claims/active', '/claims/new'] },
  { user: 'officer1', paths: ['/claims/queue', '/approvals', '/team'] },
  { user: 'manager1', paths: ['/manager', '/people'] },
];

test.describe('Accessibility (WCAG 2.1 AA, axe-core)', () => {
  test('sign-in page has no violations', async ({ page }) => {
    await page.goto('/login');
    const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
    expect(results.violations.map((violation) => violation.id)).toEqual([]);
  });

  for (const { user, paths } of pages) {
    test(`${user} pages have no violations`, async ({ app, page }) => {
      await app.signIn(user);
      for (const path of paths) {
        await app.open(path);
        await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
        await page.waitForLoadState('networkidle');
        const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
        expect(
          results.violations.map((violation) => `${violation.id}: ${violation.nodes[0]?.target}`),
          path,
        ).toEqual([]);
      }
    });
  }
});
