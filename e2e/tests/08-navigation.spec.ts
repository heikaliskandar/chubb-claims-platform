import { Page } from "@playwright/test";
import { expect, test } from "../support/fixtures";

const USERS = [
  "claimant1",
  "claimant2",
  "officer1",
  "officer2",
  "officer3",
  "lead1",
  "lead2",
  "manager1",
];

function watchForRefusals(page: Page): string[] {
  const refused: string[] = [];
  page.on("response", (response) => {
    if (
      response.url().includes("/api/") &&
      [401, 403, 404].includes(response.status())
    ) {
      refused.push(`${response.status()} ${new URL(response.url()).pathname}`);
    }
  });
  return refused;
}

test.describe("Every menu item a user can see opens a working page", () => {
  for (const user of USERS) {
    test(`${user}: sidebar links`, async ({ app, page }) => {
      const refused = watchForRefusals(page);
      await app.signIn(user);
      const nav = await app.nav();
      const links = nav.getByRole("link");
      const count = await links.count();
      expect(count).toBeGreaterThan(0);

      for (let i = 0; i < count; i++) {
        const link = links.nth(i);
        const label = (await link.innerText()).trim();
        const href = (await link.getAttribute("href"))!;
        refused.length = 0;

        await link.click();
        await expect(page, `${user} → ${label}`).toHaveURL(
          new RegExp(`${href}$`),
        );
        await expect(
          page.getByRole("heading", { level: 1 }),
          `${user} → ${label}`,
        ).toBeVisible();
        await page.waitForLoadState("networkidle");
        await expect(
          page
            .getByRole("status")
            .filter({ hasText: /Forbidden|Not found|Request failed/ }),
        ).toHaveCount(0);
        expect(refused, `${user} → ${label} made refused API calls`).toEqual(
          [],
        );
      }
    });

    test(`${user}: every row on every list opens`, async ({ app, page }) => {
      test.setTimeout(300_000);
      const refused = watchForRefusals(page);
      await app.signIn(user);
      const nav = await app.nav();
      const hrefs = await nav
        .getByRole("link")
        .evaluateAll((links) =>
          links.map((link) => link.getAttribute("href")!),
        );

      for (const href of hrefs) {
        await app.open(href);
        await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
        await page.waitForLoadState("networkidle");
        await page.waitForTimeout(800);
        const tabs = page.getByRole("tab");
        const tabCount = await tabs.count();
        const tabNames = tabCount
          ? [...Array(tabCount).keys()].map(String)
          : [""];

        for (const tab of tabNames) {
          if (tab) {
            await app.open(href);
            await expect(page.getByRole("tab")).toHaveCount(tabCount);
            await page.getByRole("tab").nth(Number(tab)).click();
            await page.waitForLoadState("networkidle");
            await page.waitForLoadState("networkidle");
          }
          const rows = page.locator(
            'tr.clickable, a.person-card, [role="treeitem"] a, .directory a',
          );
          const total = await rows.count();
          for (let i = 0; i < total; i++) {
            if (tab) {
              await app.open(href);
              await expect(page.getByRole("tab")).toHaveCount(tabCount);
              await page.getByRole("tab").nth(Number(tab)).click();
              await page.waitForLoadState("networkidle");
            } else {
              await app.open(href);
            }
            await page.waitForTimeout(500);
            if (
              (await page
                .locator(
                  'tr.clickable, a.person-card, [role="treeitem"] a, .directory a',
                )
                .count()) <= i
            ) {
              continue;
            }
            const row = page
              .locator(
                'tr.clickable, a.person-card, [role="treeitem"] a, .directory a',
              )
              .nth(i);
            const text = (await row.innerText()).split("\n")[0].trim();
            refused.length = 0;
            await row.click();
            await expect(
              page.getByRole("heading", { level: 1 }),
              `${user} ${href} ${tab} → ${text}`,
            ).toBeVisible();
            await page.waitForTimeout(400);
            expect(refused, `${user} ${href} ${tab} → ${text}`).toEqual([]);
          }
        }
      }
    });

    test(`${user}: user menu entries`, async ({ app, page }) => {
      const refused = watchForRefusals(page);
      await app.signIn(user);
      await page.locator('[aria-haspopup="menu"]').first().click();
      const profile = page.getByRole("menuitem", { name: /profile/i });
      if (user.startsWith("claimant")) {
        await expect(profile).toHaveCount(0);
        return;
      }
      await profile.click();
      await expect(
        page.getByRole("heading", { level: 1 }),
        `${user} → My profile`,
      ).toBeVisible();
      await page.waitForLoadState("networkidle");
      expect(refused, `${user} → My profile made refused API calls`).toEqual(
        [],
      );
    });
  }
});
