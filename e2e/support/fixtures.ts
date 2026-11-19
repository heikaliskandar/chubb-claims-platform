import { BrowserContext, test as base, expect, Page } from '@playwright/test';
import { ClaimsApi, PASSWORD } from './api';

export class App {
  constructor(readonly page: Page) {}

  async signIn(username: string): Promise<void> {
    await this.page.goto('/login');
    await this.page.getByLabel('Username').fill(username);
    await this.page.getByLabel('Password', { exact: true }).fill(PASSWORD);
    await this.page.getByRole('button', { name: 'Sign in' }).click();
    await expect(this.page).not.toHaveURL(/\/login/);
  }

  async open(path: string): Promise<void> {
    await this.page.evaluate((target) => {
      history.pushState({}, '', target);
      dispatchEvent(new PopStateEvent('popstate'));
    }, path);
    await expect(this.page).toHaveURL(new RegExp(`${path.replace(/[?]/g, '\\?')}$`));
  }

  async openClaim(id: string, claimNumber: string): Promise<void> {
    await this.open(`/claims/${id}`);
    await expect(this.page.getByRole('heading', { level: 1, name: claimNumber })).toBeVisible();
  }

  async act(action: string, fields: { amount?: number; text?: string } = {}): Promise<void> {
    await this.page.locator('app-page-header').getByRole('button', { name: action, exact: true }).click();
    if (fields.amount === undefined && fields.text === undefined) {
      return;
    }
    const dialog = this.page.getByRole('dialog', { name: action });
    if (fields.amount !== undefined) {
      await dialog.locator('#amount').fill(String(fields.amount));
    }
    if (fields.text !== undefined) {
      await dialog.locator('#text').fill(fields.text);
    }
    await dialog.getByRole('button', { name: action, exact: true }).click();
    await expect(dialog).toBeHidden();
  }

  async decide(choice: string, text?: string): Promise<void> {
    await this.page
      .getByRole('region', { name: 'Approval needed' })
      .getByRole('button', { name: choice, exact: true })
      .click();
    const dialog = this.page.getByRole('dialog', { name: choice });
    if (text !== undefined) {
      await dialog.locator('#text').fill(text);
    }
    await dialog.getByRole('button', { name: choice, exact: true }).click();
    await expect(dialog).toBeHidden();
  }

  async expectStatus(label: string): Promise<void> {
    await expect(this.page.locator('app-status-tag').first()).toHaveText(label);
  }

  async nav() {
    const nav = this.page.getByRole('complementary', { name: 'Main navigation' }).getByRole('navigation');
    if (!(await nav.isVisible())) {
      await this.page.getByRole('button', { name: 'Toggle menu' }).click();
      await expect(nav).toBeVisible();
    }
    return nav;
  }
}

interface Fixtures {
  app: App;
  apiAs: (username: string) => Promise<ClaimsApi>;
  newSession: () => Promise<App>;
}

export const test = base.extend<Fixtures>({
  app: async ({ page }, use) => {
    await use(new App(page));
  },
  apiAs: async ({ baseURL }, use) => {
    const clients: ClaimsApi[] = [];
    await use(async (username) => {
      const client = await ClaimsApi.as(username, baseURL!);
      clients.push(client);
      return client;
    });
    await Promise.all(clients.map((client) => client.dispose()));
  },
  newSession: async ({ browser, baseURL }, use) => {
    const contexts: BrowserContext[] = [];
    await use(async () => {
      const context = await browser.newContext({ baseURL });
      contexts.push(context);
      return new App(await context.newPage());
    });
    await Promise.all(contexts.map((context) => context.close()));
  },
});

export { expect };
