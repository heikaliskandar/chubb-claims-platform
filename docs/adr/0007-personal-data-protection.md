# ADR 0007: Personal data protection (PDPA)

**Status:** Accepted

## Context

Claims hold personal data: claimant identity, policy numbers, incident descriptions, answers to information requests, and staff contact details. The platform serves markets with personal data protection laws: Singapore and Malaysia (PDPA), Thailand (PDPA), the Philippines (Data Privacy Act), Indonesia (PDP Law) and Hong Kong (PDPO). Common to all of them are purpose limitation, access on a need-to-know basis, protection against unauthorised access, accountability for who accessed what, and retention limits. Insurance and banking regulators (MAS TRM, BNM RMiT) add expectations on authentication and audit.

## Decision

| Principle | How it is applied |
|---|---|
| **Minimisation across boundaries** | Kafka events carry a snapshot with **no PII**: no policy number, no incident description, no information request text. insights-service stores only IDs, codes, amounts and timestamps, so the analytics side never holds personal data. |
| **Need-to-know access** | Claimants see only their own claims. Staff see unassigned claims plus those of themselves and people below them (ADR 0006). Exposure is manager-only. Out-of-scope records return **404**, not 403, so their existence is not revealed. |
| **Accountability** | Every read of a claim or another employee's profile, every profile change and every account lockout writes a structured line to a dedicated `AUDIT` logger: `action actor role resource id`. Personal data is never written to logs, only identifiers. Writes are additionally kept in `claim_status_history`. |
| **Protection against unauthorised access** | Five consecutive failed sign-ins lock the account for 15 minutes, in both services. Passwords are hashed with the delegating encoder (bcrypt). |
| **Minimal exposure** | Health endpoints hide component details from anonymous callers; OpenAPI docs are off unless `API_DOCS_ENABLED=true` (on only in the local Compose stack); errors are RFC 7807 with no stack traces, SQL or class names. |
| **Integrity** | Database CHECK constraints guard status, market, currency and position codes and the completeness of decisions, referrals and answers, so a bug or manual SQL cannot write an inconsistent record. |

## Required before production (not built here)

1. **Identity:** OIDC with MFA for staff, via the company identity provider; lockout and risk-based sign-in move there. HTTP Basic and the in-memory lockout are demo-only (the lockout is per instance and resets on restart).
2. **Encryption:** TLS everywhere, including Postgres and Kafka; storage encryption with KMS-managed keys; column-level encryption or tokenisation for `policy_number`, `incident_description` and `information_request.response` if the threat model includes database administrators.
3. **Least-privilege database roles:** a migration role that owns the schema, and an application role with DML only. Today one role does both.
4. **Audit sink:** route the `AUDIT` logger to tamper-evident, write-once storage (SIEM), retained per regulation, with alerts on lockouts and unusual access volume.
5. **Retention and erasure:** a scheduled job that archives and then anonymises closed claims after the retention period per market (commonly 7 years for insurance records), and a process for data subject access and correction requests.
6. **Data residency:** confirm where each market's data may be stored and processed; Indonesia and some financial regulators restrict transfers abroad.
7. **Rate limiting** at the API gateway, and secrets from a vault rather than environment variables.

## Consequences

- Reads are audited as well as writes, which adds one log line per claim view. That volume is expected and is what an investigator needs after an incident.
- The analytics side can be opened to more users and tools without a privacy review of its data, because it holds no personal data.
