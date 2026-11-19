# Chubb APAC Claims Platform

A claims platform for motor and property claims across six APAC markets:

- **Claimants** submit and track claims, and answer information requests.
- **Claims officers** pick up, review, assess and decide claims.
- **Managers** see outstanding liability exposure and team workload.

> Take-home assessment. The brief is in [docs/ASSESSMENT_BRIEF.md](docs/ASSESSMENT_BRIEF.md). How AI was used is logged in [AI_JOURNAL.md](AI_JOURNAL.md).

## Architecture at a glance

| Component | Role | Port |
|---|---|---|
| `claims-service` | System of record. REST API for all writes and claim reads. Publishes claim events to Kafka through a transactional outbox. | 8081 |
| `insights-service` | Consumes claim events and keeps a read model for the workload, performance and exposure dashboards. | 8082 |
| PostgreSQL 17 | One database and login per service (`claims`, `insights`). | 5432 |
| Kafka 4.4 (KRaft) | Topic `claims.claim-events.v1`, keyed by claim ID. | 9094 (host) |
| Kafka UI | Browse topics and messages. | 8090 |

**REST** is used when a user is waiting for an answer. **Kafka** carries facts that have already happened. Details: _docs/architecture.md (Phase 7)_.

## Prerequisites

- Docker Desktop (with Compose v2). This is all you need to run the stack.
- JDK 21 to run the services or tests outside Docker.
- Node.js LTS for the frontend (Phase 6).

## Running locally

```bash
docker compose up --build
```

| Check | URL |
|---|---|
| claims-service health | http://localhost:8081/actuator/health |
| insights-service health | http://localhost:8082/actuator/health |
| Kafka UI | http://localhost:8090 |

Each health endpoint should report `"status":"UP"`, with a `db` component that is also `UP`.

The passwords in `docker-compose.yml` are **dev-only defaults**. Override them with environment variables or a git-ignored `.env` file.

To run one service from your IDE or with Maven (with Postgres started by Compose):

```bash
docker compose up -d postgres
cd services/claims-service
DB_PASSWORD=claims_dev_pw ./mvnw spring-boot:run
```

To reset the database (the init script only runs on an empty volume): `docker compose down -v`.

## Demo users

_Added in Phase 3._

## API

_Swagger UI and examples added in Phase 3._

## Assumptions

1. The six markets are SG, MY, HK, TH, ID and PH, with currencies SGD, MYR, HKD, THB, IDR and PHP.
2. Policy numbers are not validated (no policy admin system integration).
3. Claimants answer information requests with text only. No document uploads.
4. Only the assigned officer can act on a claim. Any officer can pick up an unassigned one.
5. Approving a claim requires an assessment first, and the approved amount can't exceed the claimed amount.
6. Exposure is reported per currency. Amounts in different currencies are never summed.

## Out of scope

_Explained with reasons in Phase 7:_ SSO/OIDC, file storage, payments, FX conversion, email/SMS, policy admin integration, fraud scoring, i18n, Kubernetes/cloud, CI/CD.
