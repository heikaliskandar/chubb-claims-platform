# ADR 0001: Two services, split into write side and reporting side

**Status:** Accepted

## Context

The platform has two very different kinds of work:

- **Claim handling** (submit, assess, decide, approve) must be strongly consistent. A reserve, a decision and its audit trail must change together or not at all, and two officers must not overwrite each other.
- **Reporting** (exposure, workload, performance) is read-heavy, aggregates across all claims, and can be a few seconds behind.

The assessment has a 3–5 hour time box, and the brief asks for both REST and Kafka.

## Decision

Two Spring Boot services:

1. **claims-service** is the system of record for claims and everything attached to them. It handles every write and every claim read.
2. **insights-service** consumes claim events from Kafka into its own read model and serves the dashboards.

Each owns its own Postgres database and login. They share no tables and communicate only through Kafka.

## Alternatives considered

- **One service.** The simplest option, but dashboard queries would scan the transactional tables, and Kafka would only be used to talk to ourselves.
- **Five or more services** (claims, assessments, decisions, notifications, reporting). Assessments and decisions share the claim's invariants and its optimistic lock; splitting them would need distributed transactions or sagas for no business benefit, and wouldn't fit the time box.

## Consequences

- Dashboards cannot slow down claim handling, and each side can scale independently.
- Reporting is eventually consistent; the UI shows `asOf` so users can see how fresh it is.
- The claimant notification stub lives in insights-service because it is already the event consumer. A dedicated notification service is the natural next split.
- Some configuration (security, demo users) is duplicated across the two services.
