# Data model

Each service owns its database. Neither reads the other's. The schema is created and changed only by **Flyway** migrations, and Hibernate runs with `ddl-auto: validate`, so the app refuses to start if the entities and schema disagree.

## claims-service (system of record)

```mermaid
erDiagram
    CLAIM ||--o{ CLAIM_STATUS_HISTORY : "audit trail"
    CLAIM ||--o{ INFORMATION_REQUEST : "questions"
    CLAIM ||--o{ ASSESSMENT : "reserve history"
    CLAIM ||--o{ OUTBOX_EVENT : "events to publish"
    CLAIM ||--o{ CLAIM_DOCUMENT : "supporting documents"
    EMPLOYEE ||--o{ EMPLOYEE : "manages"

    CLAIM {
        uuid id PK
        varchar claim_number UK "CLM-SG-2026-000001"
        varchar claimant_id
        varchar policy_number
        varchar market "SG MY HK TH ID PH"
        varchar claim_type "MOTOR PROPERTY"
        date incident_date
        varchar incident_description
        varchar currency
        numeric claimed_amount "19,2 > 0"
        numeric reserve_amount "19,2 >= 0"
        varchar status
        varchar assigned_officer_id
        varchar decision_outcome
        numeric approved_amount
        varchar decision_reason
        varchar referred_by
        numeric referred_amount
        varchar idempotency_key "unique per claimant"
        bigint version "optimistic lock"
        timestamptz created_at
        timestamptz updated_at
    }
    CLAIM_STATUS_HISTORY {
        uuid id PK
        uuid claim_id FK
        varchar from_status
        varchar to_status
        varchar changed_by
        varchar reason
        timestamptz changed_at
    }
    INFORMATION_REQUEST {
        uuid id PK
        uuid claim_id FK
        varchar question
        varchar response
        varchar status "OPEN ANSWERED"
    }
    ASSESSMENT {
        uuid id PK
        uuid claim_id FK
        varchar officer_id
        numeric assessed_amount
        varchar notes
    }
    OUTBOX_EVENT {
        uuid id PK
        uuid aggregate_id
        varchar event_type
        text payload
        timestamptz published_at "null = pending"
    }
    CLAIM_DOCUMENT {
        uuid id PK
        uuid claim_id FK
        varchar category "PHOTO POLICE_REPORT REPAIR_QUOTE ..."
        varchar file_name
        varchar content_type "pdf jpeg png webp"
        bigint size_bytes
        varchar sha256
        varchar storage_key UK "claimId/documentId"
        varchar uploaded_by
        timestamptz uploaded_at
    }
    EMPLOYEE {
        varchar username PK
        varchar employee_number UK
        varchar full_name
        varchar email UK
        varchar position
        varchar team
        varchar market
        varchar manager_username FK
        date join_date
        varchar status
    }
```

## insights-service (read model)

| Table | Purpose |
|---|---|
| `claim_projection` | Latest state of each claim from events: IDs, codes, amounts and timestamps only. **No personal data.** |
| `processed_event` | Event IDs already applied, so a redelivery is skipped. |

## Design choices

| Choice | Why |
|---|---|
| `NUMERIC(19,2)` and a currency on every amount | Exact money; never `double`. Totals are only ever made per currency. |
| `TIMESTAMPTZ` | Six markets in five time zones; everything is stored in UTC. |
| UUID primary keys and a separate human claim number | IDs can be generated anywhere without a round trip; the claim number is for people. |
| `@Version` column | Optimistic locking: concurrent updates fail with 409 instead of being lost. |
| Decision and referral columns embedded on `claim` | One-to-one with the claim, read with it every time; no join. |
| CHECK constraints on every code and on "all or nothing" groups | The database rejects an invalid status, or a half-written decision, referral or answer, even from a bug or manual SQL. |
| Partial indexes (`outbox_event` unpublished, `claim` referred) | Small and fast for the exact queries the publisher and approvals inbox run. |
| Recursive query on `employee` | Reporting line of any depth, with no fixed number of levels. |

## Migrations

| Version | Change |
|---|---|
| V1 | Claim, status history, information requests, assessments |
| V2 | Transactional outbox |
| V3 | Referral (delegated approval) |
| V4 | Employee directory |
| V5 | Domain CHECK constraints and the approvals index |
| V6 | Supporting documents (metadata; content in `DocumentStorage`) |

Migrations are append-only. Once committed, a migration is never edited; a fix is a new version.
