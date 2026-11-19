# ADR 0005: Delegated approval authority and the four-eyes rule

**Status:** Accepted (added after the core backend was finished, to reflect standard claims governance)

## Context

In real claims operations no single officer can approve any amount. Insurers use **delegated authority limits** by position, and payments above a limit need a second, more senior person to approve them (**four-eyes** or maker-checker). The first version let any assigned officer approve anything.

## Decision

- **Positions** L1–L4: Claims Officer, Senior Claims Officer, Team Lead, Claims Manager. They're carried as `POSITION_*` authorities next to the role, just as they would arrive from an identity provider's group claim.
- **Limits per position per currency** live in `app.authority.limits`. Because every limit is per currency, no FX conversion is ever needed. The Claims Manager has unlimited authority. A position or currency with no configured limit has **no** authority (fail closed).
- **Approving** (`POST /decision`, outcome `APPROVED`):
  - If the amount is within the officer's limit, the claim moves to `APPROVED`.
  - Otherwise it moves to `PENDING_APPROVAL`, with a `Referral` recording who recommended what amount and why. No decision is recorded yet, and the reserve stays at the assessed amount.
- **Reviewing a referral** (`POST /approval`: APPROVE, RETURN with a comment, or REJECT with a comment):
  - The approver must not be the officer who recommended the amount (four-eyes), and must have authority covering the amount. If either check fails, the response is 403.
  - The decision is recorded against the approver.
- Officer `approve`, `reject` and `assess` require `UNDER_REVIEW`. Otherwise the assigned officer could act on a referred claim and bypass the approver, because `PENDING_APPROVAL → APPROVED/REJECTED` is a legal transition.
- **Identity vs authority:** who you are (role, position, team) belongs in the identity provider. What you may approve stays in the claims system, because limits are audited, versioned business configuration.

## Alternatives considered

- **Limits stored in the identity provider as claims.** Puts business rules where the business can't audit or version them.
- **One limit in a base currency with FX conversion.** Needs a rate source and a time-of-rate policy, and limits would move with exchange rates.
- **A workflow engine** (Camunda, Flowable). The right tool for multi-step approval chains and SLA timers, but heavy for one referral step.

## Consequences

- New status `PENDING_APPROVAL`, two new events (`CLAIM_REFERRED_FOR_APPROVAL`, `REFERRAL_RETURNED`) and one new endpoint.
- Approvers are not restricted to the recommending officer's team or market.
- Officers can still reject on their own without sign-off; senior sign-off for high-value rejections is a next step.
