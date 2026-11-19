# ADR 0002: REST for requests, Kafka for facts

**Status:** Accepted

## Context

The brief requires REST/HTTP and Kafka and asks us to justify which is used where.

## Decision

- **REST (synchronous)** for anything a user is waiting on: every command (submit, assign, review, request or answer info, assess, decide, approve, settle) and every read. The caller gets the result, a validation error, or a conflict straight away.
- **Kafka (asynchronous)** for facts that have already happened, published by claims-service and consumed by anyone interested. There is one topic, `claims.claim-events.v1`, keyed by claim ID, with an `eventType` field in the payload.
- **The frontend gets asynchronous updates by polling** every 10 seconds.

### Event contract

- One envelope type, `ClaimEvent`, carries the event type, the actor, and a **snapshot of the claim's current state**, including its version.
- I chose this over nine near-identical event classes because consumers need the same fields from every event. A consumer can simply store the latest snapshot, and the version lets it ignore stale ones.
- **No PII:** no policy number and no incident description.
- Payloads are plain JSON strings, so consumers don't depend on Java classes. insights-service has its own copy of the record, uses strings for enums, and ignores unknown fields.

### Why one topic per aggregate

Kafka only guarantees order within a partition. Keying every event by claim ID puts all of one claim's events in one partition, in order, while different claims spread across partitions for parallelism. Separate topics per event type would lose that ordering.

## Alternatives considered

- **Commands over Kafka** (e.g. submitting a claim via a topic). The claimant needs the claim number and any validation errors immediately; an asynchronous request–reply adds a correlation ID, a timeout and a response topic for no benefit.
- **REST callbacks from claims-service to insights-service.** This couples the write path to the reporting service's availability. With Kafka, insights-service can be down and catch up later.
- **WebSockets or SSE instead of polling.** Better user experience, but adds connection management. Polling every 10 seconds is enough for claims, which move in hours, not milliseconds.

## Consequences

- Dashboards are eventually consistent.
- Consumers must be idempotent and tolerate reordering. insights-service is both; see ADR 0003.
- New consumers (finance, fraud, a data lake) can be added without changing claims-service.
