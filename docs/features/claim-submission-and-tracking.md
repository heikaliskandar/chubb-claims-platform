# Claim submission and tracking

> **Brief:** *Claimants need to be able to report an incident, track their claim, provide additional information when asked, and receive decisions.*

## What the claimant can do

| Capability | Screen | Endpoint |
|---|---|---|
| Report an incident | **Claims → New claim** | `POST /api/v1/claims` |
| See active claims | **Claims → My claims** | `GET /api/v1/claims/mine?closed=false` |
| See closed claims (settled, rejected) | **Claims → History** | `GET /api/v1/claims/mine?closed=true` |
| Open one claim | Click a row | `GET /api/v1/claims/{id}` |
| Answer an information request | Banner **We need more information** → **Respond** | `POST /api/v1/claims/{id}/information-requests/{requestId}/response` |
| Receive the decision | Decision card on the claim page | (same claim read) |

## Reporting an incident

The form asks for:
- the claim type (Motor or Property);
- the market, which fixes the currency;
- the policy number;
- the incident date, which can't be in the future;
- the claimed amount, which must be positive with at most 2 decimals;
- a description of what happened.

Validation runs in the browser and again on the server (Bean Validation). In the browser, errors appear when a field is left and, on submit, as an **error summary**: it takes focus, is announced by screen readers, and its links jump to each field. Policy numbers are trimmed and upper-cased, and thousands separators are stripped from the amount. Server errors come back as RFC 7807 Problem Details and are shown against the matching field.

On success the claim gets a number such as `CLM-SG-2026-000001`, made of the market, the year and a database sequence. The claim starts in `SUBMITTED`, waiting on the claims team.

**Double submits.** The frontend sends an `Idempotency-Key` header with each submission. Submitting again with the same key returns the original claim instead of creating a new one, which is enforced by a unique `(claimant_id, idempotency_key)` constraint. So a double click, a retry or a flaky network can't create duplicates.

## Changing a claim before it is picked up

Until a claims officer picks up the claim (status *Submitted*), the claimant can still change their mind. This is standard practice for insurers.

| Action | Screen | Endpoint | Recorded as |
|---|---|---|---|
| Edit the details | **Edit claim** on the claim page: the same form, pre-filled | `PUT /api/v1/claims/{id}` | "Details updated" in the activity history; event `CLAIM_DETAILS_UPDATED` |
| Remove a document they added (for example a duplicate) | Bin icon on the document, then confirm | `DELETE /api/v1/claims/{id}/documents/{documentId}` | Audit log; the file and record are removed |
| Withdraw the claim | **Withdraw claim**, with an optional reason | `POST /api/v1/claims/{id}/withdrawal` | Status **Withdrawn** (final); event `CLAIM_WITHDRAWN`; shown under History |

A banner on the claim page tells the claimant this is possible, and until when.

- **The market can't be edited.** It fixes the claim number (`CLM-MY-…`) and the currency. To claim in another market, withdraw and submit a new claim; the form says so.
- **Once an officer picks up the claim, it is locked.** Edit and Withdraw disappear, documents can no longer be removed, and the API returns 409 or 403. Further changes go through the officer (information requests), so the officer never works on something that changes under them.
- **Race-safe.** If an officer picks up the claim while the claimant is editing, the save is rejected with a clear message and the form shows that the claim is now locked.

## Tracking

Every claim response carries three fields the UI shows directly, so the frontend never re-implements the rules:

| Field | Example |
|---|---|
| `status` | `INFO_REQUESTED` |
| `waitingOn` | `CLAIMANT` (or `CLAIMS_TEAM`, `ASSIGNED_OFFICER`, `APPROVER`, `NOBODY`) |
| `nextStep` | "Please answer the claims officer's question." |

The claim page also shows an **activity timeline** built from `claim_status_history`: every status change, who made it and when. Lists and the claim page refresh every 10 seconds, so a claimant sees an officer's action without reloading.

## Information requests

An officer asks a question, which moves the claim to `INFO_REQUESTED`. On **My claims**, the claimant sees a banner linking every claim that is waiting on them. Their answer moves the claim back to `UNDER_REVIEW`. Questions and answers stay on the claim as a thread.

## Decisions

When a claim is approved or rejected, the claimant sees:
- the outcome;
- the approved amount, if any;
- the reason;
- who decided and when.

insights-service also turns `INFORMATION_REQUESTED`, `CLAIM_APPROVED`, `CLAIM_REJECTED` and `CLAIM_SETTLED` events into a claimant notification. It's a logged stub for now: the point where an email or SMS sender would be called.

## Privacy

- A claimant can only read their own claims. Anyone else's claim returns **404**, so its existence isn't revealed.
- Internal fields are never sent to claimants: the reserve, assessments and the officer's notes.

## Code

| Layer | Location |
|---|---|
| Screens | `frontend/src/app/features/claimant/submit-claim`, `features/claims/claim-list`, `features/claims/claim-detail` |
| API | `services/claims-service/.../claim/api/ClaimController.java` |
| Rules | `claim/domain/Claim.java` (`submit`, `provideInformation`), `ClaimStatus.java` |

## Tests

- `ClaimControllerTest`: validation, idempotent submit, role checks.
- `ClaimTest`: information request rules.
- e2e `02-claimant.spec.ts`: submit through the form, tracking, another claimant gets 404, banner → respond.
