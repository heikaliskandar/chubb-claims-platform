# ADR 0003: Transactional outbox, with idempotent consumers

**Status:** Accepted

## Context

Every claim change must update Postgres *and* publish an event. Doing both directly is a **dual write**, and it can't be atomic:

- If the DB commits and the Kafka send then fails, insights-service never hears about the change and exposure is understated.
- If the Kafka send succeeds and the DB transaction then rolls back, consumers act on a change that never happened.

For a regulated insurer, both cases are reconciliation incidents.

## Decision

### Producer side (claims-service)

- `ClaimEventRecorder` writes the event to an `outbox_event` table **in the same database transaction** as the claim change. It uses `Propagation.MANDATORY`, so calling it outside a transaction fails loudly.
- Before taking the snapshot it flushes, so the claim's `@Version` is already incremented and the event carries the version that will commit.
- `OutboxPublisher` runs every second:
  1. It locks up to 100 unpublished rows with `FOR UPDATE SKIP LOCKED`.
  2. It sends them to Kafka keyed by claim ID and waits for every acknowledgement (`acks=all`, idempotent producer).
  3. It marks them published in the same transaction.
- If any send fails, the transaction rolls back and the whole batch is retried on the next poll.
- A partial index on `published_at IS NULL` keeps the polling query cheap however many rows have been published.

### Consumer side (insights-service)

- Delivery is at-least-once, so the consumer is idempotent:
  - It records `event_id` with `INSERT … ON CONFLICT DO NOTHING`; a duplicate is skipped.
  - It updates the projection only when the incoming snapshot's version is newer.
- Both happen in **one database transaction**, so a failure rolls both back and the retry is clean.
- After 3 failed attempts, 1 second apart, the message goes to `claims.claim-events.v1.dlt`. Malformed JSON goes there immediately.

## Alternatives considered

- **Kafka transactions plus a chained transaction manager.** This still isn't atomic with Postgres; there's a gap between the two commits.
- **Change data capture (Debezium) on the outbox table.** More robust and no polling, but adds Kafka Connect to run and operate. It's the right step at higher volume (see the Scaling section in `architecture.md`).
- **Publishing after commit** (`@TransactionalEventListener`). Simple, but events are lost if the process dies between the commit and the send.

## Consequences

- Events reach Kafka within about a second of the commit.
- Duplicates are possible, for example if the process crashes after a send but before the row is marked published. The consumer makes them harmless.
- With several publisher instances, two events for the same claim could be published out of order. The version check makes that harmless.
- Published outbox rows accumulate. A retention job is a known next step.
