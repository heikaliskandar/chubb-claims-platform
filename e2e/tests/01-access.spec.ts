import { expect, test } from '../support/fixtures';

test.describe('R8 One application for every user type, adapted to role', () => {
  const homes = [
    {
      user: 'claimant1',
      url: /\/claims\/active$/,
      menu: ['New claim', 'My claims'],
      hidden: ['Approvals', 'Queue'],
    },
    {
      user: 'officer1',
      url: /\/claims\/queue$/,
      menu: ['Queue', 'Assigned to me', 'Approvals', 'Performance'],
      hidden: ['New claim'],
    },
    {
      user: 'manager1',
      url: /\/manager$/,
      menu: ['Dashboard', 'All open', 'Approvals', 'People'],
      hidden: ['New claim', 'Queue'],
    },
  ];

  for (const { user, url, menu, hidden } of homes) {
    test(`${user} lands on their home page with a role-specific menu @responsive`, async ({ app, page }) => {
      await app.signIn(user);
      await expect(page).toHaveURL(url);
      const nav = await app.nav();
      for (const item of menu) {
        await expect(nav.getByRole('link', { name: item, exact: true })).toBeAttached();
      }
      for (const item of hidden) {
        await expect(nav.getByRole('link', { name: item, exact: true })).toHaveCount(0);
      }
    });
  }

  test('wrong password is rejected and the user stays on the sign-in page', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Username').fill('officer1');
    await page.getByLabel('Password', { exact: true }).fill('not-the-password');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page.getByRole('alert')).toContainText("don't match");
    await expect(page).toHaveURL(/\/login$/);
  });

  test('a claimant cannot reach staff pages by URL', async ({ app, page }) => {
    await app.signIn('claimant1');
    await page.evaluate(() => {
      history.pushState({}, '', '/approvals');
      dispatchEvent(new PopStateEvent('popstate'));
    });
    await expect(page).not.toHaveURL(/\/approvals$/);
  });

  test('the session survives a reload but is scoped to the tab', async ({ app, page, newSession }) => {
    await app.signIn('officer1');
    await page.reload();
    await expect(page).toHaveURL(/\/claims\/queue$/);

    const otherTab = await newSession();
    await otherTab.page.goto('/claims/queue');
    await expect(otherTab.page).toHaveURL(/\/login$/);
  });

  test('signing out clears the session', async ({ app, page }) => {
    await app.signIn('officer1');
    await page.getByRole('button', { name: 'Sign out' }).first().click();
    await expect(page).toHaveURL(/\/login$/);
    await page.reload();
    await expect(page).toHaveURL(/\/login$/);
  });
});
