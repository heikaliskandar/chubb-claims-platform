# Documentation

## Start here

| Document | What it covers |
|---|---|
| [User flows](user-flows.md) | How claimants, officers, team leads and managers use the platform, with diagrams, the demo data and a ten-minute walkthrough |
| [Architecture](architecture.md) | Services, REST vs Kafka, write path, reliability, money, scaling to 10x and 100x |
| [Technology stack](tech-stack.md) | Every framework and library, with version, licence and reason |

## Features

| Feature | Brief requirement |
|---|---|
| [Claim submission and tracking](features/claim-submission-and-tracking.md) | Claimants report, track, provide information, receive decisions |
| [Claim handling](features/claim-handling.md) | Staff pick up, review, assess, settle or reject |
| [Approvals and delegated authority](features/approvals.md) | Beyond the brief: per-position limits, four-eyes referrals, approvals inbox |
| [Dashboards and insights](features/dashboards-and-insights.md) | Team workload and performance; manager exposure |
| [Employees and reporting line](features/employees-and-hierarchy.md) | Beyond the brief: directory, profiles, who sees what |
| [Events and messaging](features/events-and-messaging.md) | REST vs Kafka, outbox, idempotent consumer, DLT |
| [Security and privacy](features/security-and-privacy.md) | Authentication, authorization, PDPA controls, browser hardening |
| [Data model](features/data-model.md) | Schema, ER diagram, migrations, design choices |
| [Frontend](features/frontend.md) | One Angular app, structure, design system, accessibility |
| [Testing](features/testing.md) | Unit, integration, end-to-end and audits |

## Decisions (ADRs)

| # | Decision |
|---|---|
| [0001](adr/0001-service-boundaries.md) | Two services: claims (system of record) and insights (read model) |
| [0002](adr/0002-rest-vs-kafka.md) | REST when someone waits for the answer; Kafka for facts others react to |
| [0003](adr/0003-transactional-outbox.md) | Transactional outbox for reliable event publishing |
| [0004](adr/0004-claim-state-machine.md) | Explicit claim state machine |
| [0005](adr/0005-delegated-approval-authority.md) | Delegated approval authority per position and currency |
| [0006](adr/0006-reporting-line-visibility.md) | The reporting line drives visibility and approvals |
| [0007](adr/0007-personal-data-protection.md) | Personal data protection (PDPA) |

## Assessment inputs

| Document | |
|---|---|
| [Assessment brief](ASSESSMENT_BRIEF.md) | The task as given |
| [Master prompt](MASTER_PROMPT.md) | The working instructions the AI followed, phase by phase |
| [AI journal](../AI_JOURNAL.md) | What was asked, accepted, challenged and overridden |
