# ADR 0004: The claim state machine lives in the domain

**Status:** Accepted

## Context

A claim moves through a regulated lifecycle. Illegal moves (settling an unapproved claim, approving a rejected one) must be impossible whichever API or job triggers them. Every move must be auditable.

## Decision

- `ClaimStatus.allowedNextStatuses()` is an **exhaustive `switch`**. Adding a status without deciding its transitions is a compile error. `isTerminal()` means "has no next status", so it can't drift from the transitions.
- `Claim` is the **aggregate root**. Information requests, assessments, the status history, the decision and any referral can only be changed through `Claim` methods.
- Business rules are checked before any state changes:
  - Only the assigned officer can act on the claim.
  - Approval requires a prior assessment.
  - The approved amount can't exceed the claimed amount.
  - Money is validated (positive, no more than 2 decimal places).
- One `@Version` covers the whole aggregate.
- Every transition appends an immutable `ClaimStatusHistory` row (`@Immutable`, never updated).
- **`ASSIGNED → UNDER_REVIEW` has its own endpoint** (`POST /claims/{id}/review`). The original plan had no trigger for it. An explicit, audited action is clearer than one call making two transitions.
- Domain exceptions map to HTTP status codes in a single handler:

| Exception | HTTP status |
|---|---|
| Invalid status for the action | 409 Conflict |
| Business rule broken | 422 Unprocessable Content |
| Not the assigned officer or the claimant | 403 Forbidden |
| Unknown information request | 404 Not Found |

- **`allowedActions`** in every claim response is computed by `Claim.availableActions(actor)`, which uses the same checks as the commands. The frontend shows exactly the buttons the backend will accept.

## Alternatives considered

- **Checks in controllers or services.** Rules get duplicated and drift; a scheduled job could bypass them.
- **A state machine library** (e.g. Spring Statemachine). Heavier than eight states and a dozen transitions need, and harder to read in a review.
- **A separate `inforequest` module with its own service and repository.** An information request never exists without its claim, and its rules are claim rules.

## Consequences

- The tests in `ClaimStatusTest` check all 64 status pairs against a table written separately from the code. `ClaimTest` covers the rules and the available actions.
- Loading a claim loads its children. List views use summary DTOs that touch only scalar fields, which avoids N+1 queries.
