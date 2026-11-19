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
| `01-access` | R8 One app for every user type | Each role lands on its own home page with its own menu (desktop and mobile); wrong password shows an inline alert; claimants can't reach staff pages; the session survives a reload but not a new tab, and signing out clears it |
| `02-claimant` | R1 Report, R2 Track, R3 Provide information | Claimant submits through the form and lands on the tracking page; invalid input shows a focused error summary whose links jump to each field; the claim shows in My claims with its activity; other claimants get 404; the "We need more information" banner leads to the claim, the answer is saved and the claim goes back to the officer |
| `03-officer-lifecycle` | R5 Pick up, R6 Review and assess, R7 Settle or reject | Queue → Assign → Start review → Assess → Approve → Settle, then the claimant sees the decision; rejection with a reason; two officers assigning at once, only one wins; buttons appear only when the rules allow |
| `04-approvals` | R7 Delegated authority (four-eyes) | Approval above the officer's SGD limit is referred; the panel explains it in plain words ("above your approval limit of SGD 10,000"); the officer sees it as *Waiting for decision* under Sent by me with no way to approve it; the team lead finds it under Waiting for me, sees Daniel Lim's name and limit, and chooses **Approve payment**; it then shows *Approved by Arjun Mehta*. A lead can **send back for changes** and the officer sees why. A lead outside the reporting line can't see or act on it; within the limit, the decision is final |
| `05-insights` | R9 Manager exposure, R4 Team workload | Dashboard shows exposure per currency; a new assessment reaches the exposure read model **through Kafka**; claimants and officers get 403 on exposure; team performance page lists officer workload |
| `06-api-and-security` | Contract and hardening | Idempotent submit; RFC 7807 errors with no internal details; 401 without a browser login prompt; role boundaries (403); CSP, frame, sniff, referrer and caching headers |
| `08-navigation` | Role-based visibility | As each of the 8 demo users: every sidebar link and the My profile entry opens a page with no refused API call; every row on every list and tab (claims, approvals, people) opens without a 401, 403 or 404. Nothing a user can see is a dead end. |
| `09-documents` | Supporting documents | Attach files while reporting a claim; the assigned officer downloads a file the claimant added; a file pretending to be an image is refused; documents follow the claim's visibility; closed claims accept no more |
| `10-api-docs` | Live API documentation | `/api/docs` shows both services; every operation in both specs has a summary and documents 401; specs and try-it-out use the same origin and authentication as the app |
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
