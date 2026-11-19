# End-to-end tests

[Playwright](https://playwright.dev) tests that drive the real stack (Angular, nginx, both services, PostgreSQL and Kafka) the way users do, and check each requirement from the brief.

## Run

```bash
docker compose up --build -d          # from the repo root
cd e2e
npm ci
npx playwright install chromium       # first time only
npx playwright test                   # headless, desktop Chrome + Pixel 7 for @responsive tests
npx playwright test --ui              # interactive runner with time-travel debugging
npx playwright show-report            # HTML report; failures include trace, screenshot and video
```

Each test creates its own claims, so a run adds data to the database. Reset to the curated demo data afterwards with `docker compose down -v && docker compose up --build -d`, or point the suite at a separate environment.

`E2E_BASE_URL` (default `http://localhost:4200`) and `E2E_PASSWORD` (default `password`) point the suite at another environment.

## Requirement coverage

| Spec | Requirement | What is proven |
|---|---|---|
| `01-access` | R8 One app for every user type | Each role lands on its own home page with its own menu (desktop and mobile); wrong password is rejected; claimants can't reach staff pages; a reload signs out because credentials are never persisted |
| `02-claimant` | R1 Report, R2 Track, R3 Provide information | Claimant submits through the form and lands on the tracking page; invalid input is blocked; the claim shows in My claims with its activity; other claimants get 404; the "We need more information" banner leads to the claim, the answer is saved and the claim goes back to the officer |
| `03-officer-lifecycle` | R5 Pick up, R6 Review and assess, R7 Settle or reject | Queue → Assign → Start review → Assess → Approve → Settle, then the claimant sees the decision; rejection with a reason; two officers assigning at once, only one wins; buttons appear only when the rules allow |
| `04-approvals` | R7 Delegated authority (four-eyes) | Approval above the officer's SGD limit is referred; officer sees it as Pending under My referrals with no Review button; team lead reviews from To review and approves, then sees it under Decided; a lead outside the reporting line can't see or act on it; approval within the limit is final |
| `05-insights` | R9 Manager exposure, R4 Team workload | Dashboard shows exposure per currency; a new assessment reaches the exposure read model **through Kafka**; claimants and officers get 403 on exposure; team performance page lists officer workload |
| `06-api-and-security` | Contract and hardening | Idempotent submit; RFC 7807 errors with no internal details; 401 without a browser login prompt; role boundaries (403); CSP, frame, sniff, referrer and caching headers |
| `07-accessibility` | WCAG 2.1 AA | axe-core finds no violations on sign-in and on claimant, officer and manager pages |

## Conventions

- **Arrange through the API, assert through the UI.** Setup (submit, assign, assess) uses the REST API, so each test is fast and independent; the behaviour under test is driven through the browser.
- **Locators by role and label** (`getByRole`, `getByLabel`), as a user or screen reader finds them, never by CSS class. A test that can't find a button by its name has usually found an accessibility bug.
- **Every test creates its own data**, so tests run in parallel and in any order against a shared environment.
- **No sleeps.** Playwright's auto-waiting assertions, and `expect.poll` for eventually-consistent reads.
- **Several users in one test** run in separate browser contexts (`newSession`), the way two people use two browsers.

## What the suite caught

- The claim action dialogs used `(ngSubmit)` without a form directive, so the browser performed a native submit and reloaded the page, dropping the in-memory session. Every dialog action (request information, respond, assess, approve, reject, review a referral) signed the user out. Unit and web-layer tests could not see this.
- Icon ligatures were read by screen readers ("inbox Queue", "check Approve"). Decorative icons are now `aria-hidden`.
