# AI Working Journal

A running log of how I directed the AI on this assessment: what I asked, what I accepted, what I challenged and what I overrode, and why.

- **Tool:** Claude Code (Claude Opus 5.5) in VS Code
- **Working instruction:** [docs/MASTER_PROMPT.md](docs/MASTER_PROMPT.md). Phased: the AI stops after each phase, and I review and commit before continuing.

**Legend:** ✅ Accepted · ⚠️ Challenged · ❌ Overrode · ❓ Asked

## Time log

Phase boundaries are taken from the commit timestamps (each phase was committed when the next one started).

| Phase | Start | End | Duration |
|---|---|---|---|
| 0 Plan | 17:38 | 17:55 | 17m |
| 1 Scaffold | 17:55 | 18:00 | 5m |
| 2 Domain & persistence | 18:00 | 18:11 | 11m |
| 3 Claims REST API | 18:11 | 18:21 | 10m |
| 4 Events / outbox | 18:21 | 18:26 | 5m |
| 5 insights-service | 18:26 | 18:41 | 15m |
| 5b Delegated approval authority (added scope) | 18:41 | 18:51 | 10m |
| 6 Angular frontend | 18:51 | 19:00 | 9m |
| 7 Docs & polish | 19:00 | 19:10 | ~10m |
| **Total** | | | **~1h32m** |

## Ground rules I set for the AI

- ❌ **No AI co-author trailer on commits.** Every commit is authored by me. AI involvement is disclosed here instead, which is what the brief asks for.
- ❌ **Production-style code with minimal comments.** After Phase 1 the AI was writing explanatory comments and javadoc everywhere. I told it to comment only what is truly non-obvious. Two comments survive in config/SQL plus one in Java (the `flush()` before an event snapshot), each guarding a line that looks wrong at first glance. Rationale lives in the ADRs and this journal instead.
- ⚠️ **Verify, don't recall.** I asked the AI to check library versions against the live registries rather than trust its memory, and to compile and run the tests after every phase.
- **Environment:** my machine had no JDK or Docker. The AI downloaded a portable JDK into its scratch directory (not installed system-wide) so the unit and web tests could actually run. The Testcontainers integration tests and `docker compose up` were written but **not executed** before submission; this is called out in the README.

---

## Phase 0: Plan

- ❓ I asked for a plan only, no code. The AI found a gap in the state machine I gave it: nothing in the API moved a claim from ASSIGNED to UNDER_REVIEW. I chose an explicit `POST /claims/{id}/review` so every status change is one deliberate, audited action.
- ✅ Accepted HTTP Basic over JWT for the demo login: simpler, nothing to manage, OIDC documented as the production path.
- ⚠️ Challenged the scope against the 3-hour target. Agreed a cut line that protects the backend and trims the frontend first, as the brief asks.

## Phase 1: Scaffold

- ❓ Had the AI look up current versions instead of guessing. It found Spring Boot 4.1.2 with springdoc 3.2.0 as a matching pair, and that `provectuslabs/kafka-ui` is unmaintained. ✅ Switched to the maintained `kafbat/kafka-ui` fork.
- ⚠️ Challenged the generated scaffold against "no unused dependencies". Empty pom metadata, unused test starters and a `contextLoads` test that needs a database were removed; each dependency is added in the phase that uses it.
- ✅ Accepted forcing LF line endings with `.gitattributes`, after the AI pointed out that a Windows CRLF checkout would break `mvnw` and the Postgres init script inside Linux containers.

## Phase 2: Domain & persistence

- ⚠️ Challenged the folder layout in my own prompt. Information requests, assessments and the decision belong to the `Claim` aggregate rather than a separate `inforequest` package, so all rules sit in one place under one optimistic lock. I also dropped a redundant `claimant_id` index that the unique idempotency constraint already covers.
- ✅ The state-machine test checks all status pairs against a table written separately from the code, so it can't just restate the implementation. Money rules (no silent rounding, `compareTo` not `equals`, CHECK constraints in the DB) are tested.

## Phase 3: Claims REST API

- ⚠️ Challenged the AI's first JPQL approach for optional queue filters (`:status IS NULL OR …` can fail on Postgres with null parameters) and switched to a JPA Specification. DTO mapping happens inside the transaction because `open-in-view` is off.
- ✅ Accepted two insurance-specific access rules: claimants never see the reserve or assessments, and another claimant's claim returns 404 rather than 403, so its existence isn't revealed.
- ✅ Accepted HTTP Basic with stateless sessions, a 401 Problem Details response with no browser popup, and CSRF off, and I can explain why that's safe. The web tests run against the real security config.

## Phase 4: Events / outbox

- ⚠️ Challenged nine separate event classes. Accepted one `ClaimEvent` envelope carrying a snapshot of the claim's state and its version, so the consumer stores the latest state and safely ignores events that arrive out of order.
- ✅ Accepted the outbox publisher design: `SKIP LOCKED` batches, wait for every Kafka acknowledgement before marking rows published, roll back and retry on failure. I can explain the dual-write problem and why delivery is at-least-once, not exactly-once.
- ❌ Removed a repository method the AI added only for tests (dead code). Chose `TEXT` over `JSONB` for the payload to avoid an untested Hibernate/Jackson 2 vs 3 mapping risk.

## Phase 5: insights-service

- ❌ Overrode the scaffold: insights uses JDBC, not JPA, because the read model is all SQL upserts and `GROUP BY`. ⚠️ Challenged "pre-aggregated" running totals as error-prone with reordered events; chose one row per claim aggregated at query time, with a documented path to materialized views or a cache.
- ✅ Duplicate handling (`ON CONFLICT DO NOTHING`) and the version-guarded upsert run in one transaction; 3 retries then the dead-letter topic, and malformed JSON skips the retries. The integration test proves one notification per duplicate event and that an older snapshot can't overwrite a newer one.
- ⚠️ Caught: the AI left the validation starter out of the insights pom although its config uses Bean Validation. The compile step caught it. Exposure totals are per currency, and `CurrencyTotal.plus` refuses to mix currencies.

## Phase 5b: Delegated approval authority (scope I added)

- ❓ After the backend was done, I asked the AI what industry-standard claims handling adds: identity and access, hierarchy, approval levels. It proposed a build-now / document-later split. ✅ I chose to **build** per-currency approval limits by position (L1–L4), referral to `PENDING_APPROVAL`, the four-eyes rule, and `allowedActions` / `waitingOn` / `nextStep` on every claim. I chose to **document only** OIDC/Keycloak, SLA timers, automatic triage and finance payment release, to protect the time box.
- ⚠️ Caught during implementation: because `PENDING_APPROVAL → APPROVED/REJECTED` is a legal transition, the assigned officer could have bypassed the approver by calling `/decision` on a referred claim. Officer commands now require `UNDER_REVIEW`, and a test locks that in.
- ✅ Design point I can defend: identity (role, position, team) belongs in the identity provider; approval limits stay in the claims system as audited business configuration. `allowedActions` reuses the domain's own checks, so the UI can't drift from the backend.

## Phase 6: Angular frontend

- ✅ Accepted a frontend with no business rules: one claim-detail component serves every role, and its buttons come only from the backend's `allowedActions`. That Phase 5b investment is what let all four frontend priorities fit.
- ⚠️ Caught: the first submit form subscribed to `valueChanges` without cleanup; replaced with `toSignal`. Credentials are kept in memory only, accepting logout on refresh to avoid exposing them to XSS.
- ❌ Skipped by decision: frontend component tests (optional in the prompt), generating TypeScript models from OpenAPI (documented next step), and an end-to-end browser run (needs Docker).

## Phase 6b: UI redesign

- ❓ The first UI (Angular Material defaults) looked unfinished. I asked for a modern, consistent admin layout: sidebar, breadcrumbs, minimal text.
- ✅ Rebuilt on a component library with a customised theme (later replaced, see Phase 9) (custom royal-blue palette with teal, amber and coral accents; Plus Jakarta Sans), a shared shell (sidebar, top bar, user menu) and one page-header component (breadcrumb, title, actions), so every page has the same structure. Tables are lazy-paged with PrimeNG `p-table`, actions that need input open a dialog, the claim history uses a timeline, and the dashboard has stat cards and a stacked workload chart.
- ✅ Second pass on the shell: the sidebar and top bar are floating rounded cards, the active menu item is a soft blue pill, a profile card sits at the bottom of the sidebar, and the sidebar collapses to icons. Users can upload a profile photo (resized in the browser, stored locally per user). The dashboard has a welcome card with an original SVG illustration drawn in code.
- ❌ I asked the AI to copy a commercial admin template I had downloaded. It refused because of licensing and because presenting it as my own work would misrepresent the assessment, and built an original layout on PrimeNG instead.
- ⚠️ The initial bundle grew to 660 kB raw / 151 kB transferred because of the theme engine. I raised the warning budget to 750 kB instead of hiding the warning; the hard limit stays at 1 MB.

## Phase 11: Requirements check and navigation

- ❓ I asked the AI to check every line of the brief against what we had. It found two real gaps: officers had **no screen** for team workload and performance (only the API), and approvers had no single place to see referral statuses. Claimants also had nothing pointing them to claims needing their action.
- ✅ Added: grouped sidebar menus (Claims, Approvals, Team, Account); separate Queue, Assigned to me, All open and History views; an **Approvals** page whose tabs depend on whether you manage anyone (To review, Decided, My referrals) with a Review button only where the domain rules allow it; a **Team performance** page; an "action needed" banner for claimants; and a requirements traceability table in the README.
- ✅ Approval status (pending, approved, returned, rejected) is derived from the claim's own audit trail, not stored twice, and is unit-tested, including a returned referral that is referred again.

## Phase 10: Chasing the 502

- ❓ Sign-in returned 502 in the Codespace, and I could not share the container logs at the time. The AI booted both services locally against a real embedded PostgreSQL 17 (a temporary test, removed afterwards) and called the main endpoints over HTTP. Migrations, seeding, positions from the directory, reporting-line visibility, contact updates and claim submission all worked, so the code was not the cause.
- ✅ Hardened the environment instead: nginx re-resolves service addresses, the backends restart on failure, the JVMs and Kafka have memory caps for small machines, and the frontend waits for both backends to accept connections before starting.
- ⚠️ The smoke run also caught a contract leak: `/api/v1/me` returned two helper flags (`claimant`, `officer`) as JSON fields. Fixed with `@JsonIgnore`.

## Phase 9: Removing the paid UI library

- ⚠️ The running app showed an "Invalid PrimeUI License" banner. The AI found that PrimeNG 22 and PrimeIcons are no longer open source: they need a PrimeUI licence key. It corrected its own earlier claim that PrimeNG was free to use, and refused to hide or bypass the licence check.
- ✅ I chose to remove the dependency entirely. The UI now uses a small set of in-repo components (buttons, inputs, select, tabs, modal, menu, toasts, table, pager, tags, avatar, breadcrumb, timeline, org tree) in the spirit of shadcn, plus chart.js (MIT) and Google Material Symbols (Apache-2.0) for icons. Every colour comes from one set of light/dark design tokens, and there is one font.
- ✅ Result: no licence risk, and the initial bundle dropped from 660 kB to 310 kB, so the default 500 kB budget is restored. Trade-off: we own the accessibility details (Esc to close, focus rings, ARIA roles on tabs, menus and dialogs), which are built in.

## Phase 8: Employees module

- ❓ I asked for an employees module with positions, a hierarchy (see people below you, not above), and profiles with join date and other standard HR fields. ⚠️ The AI pushed back that a staff directory is only defensible in a claims platform if the hierarchy drives the claims workflow, and asked me three questions. I decided: the hierarchy **drives claims** (approvals and visibility), employees see their **manager's name only**, and the data lives **in claims-service**, not a new service, to protect the time cap.
- ✅ Built: an `employee` table with a self-referencing reporting line, a recursive SQL query for everyone below a person (`UNION` so a bad cycle can't loop), directory/profile/update-contact endpoints, positions loaded from the directory at login, People pages (searchable list, org chart) and a tabbed profile (Overview, Team, Claims, Authority).
- ✅ Claims now follow the hierarchy: staff see unassigned claims plus those of themselves and their reports, and a referral can only be reviewed by someone above the recommending officer with enough authority. This closed the earlier "approver not restricted to team" limitation. Tests cover a peer lead being blocked, and a manager above with too low a limit being blocked.

## Phase 7: Docs & polish

- ✅ The AI wrote `docs/architecture.md` (component, state and sequence diagrams in Mermaid, REST vs Kafka table, reliability matrix, scaling at 10x/100x) and five ADRs, including one for the approval-authority scope I added.
- ⚠️ I made sure the docs say plainly what was **not** verified (integration tests and the full Compose stack) rather than implying everything ran.

## Reflection

- **What worked:** making the AI stop after every phase, and asking it to verify against real registries, compilers and tests rather than its memory. Most of the bugs it introduced were caught by compiling or testing before I committed.
- **Where I had to step in:** scope (cutting the frontend first, then deliberately adding approval authority), style (comments), and keeping honest about what was and wasn't verified.
- **What I'd do next:** run the integration suite and the Compose stack, then OIDC, then SLA and escalation rules.
