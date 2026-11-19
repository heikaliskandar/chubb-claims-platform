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
| `frontend` | Angular 22 app for all roles, served by nginx, which also proxies `/api` to the services. | 4200 |

**REST** is used when a user is waiting for an answer. **Kafka** carries facts that have already happened. Details: _docs/architecture.md (Phase 7)_.

## Prerequisites

- Docker Desktop (with Compose v2). This is all you need to run the stack.
- JDK 21 to run the services or tests outside Docker.
- Node.js 22+ to run the frontend outside Docker.

## Running locally

```bash
docker compose up --build
```

| Check | URL |
|---|---|
| **Web app** | **http://localhost:4200** |
| claims-service health | http://localhost:8081/actuator/health |
| insights-service health | http://localhost:8082/actuator/health |
| Kafka UI | http://localhost:8090 |

Each health endpoint should report `"status":"UP"`, with a `db` component that is also `UP`.

The passwords in `docker-compose.yml` are **dev-only defaults**. Override them with environment variables or a git-ignored `.env` file.

To run one service from your IDE or with Maven (with Postgres started by Compose):

```bash
docker compose up -d postgres
cd services/claims-service
DB_PASSWORD=claims_dev_pw DEMO_USER_PASSWORD=password ./mvnw spring-boot:run
```

Frontend development server (with the backend running in Compose), proxying `/api` to ports 8081 and 8082:

```bash
cd frontend && npm install && npm start    # http://localhost:4200
```

To reset the database (the init script only runs on an empty volume): `docker compose down -v`.

## Demo users

HTTP Basic, all with the password `password` (set with `DEMO_USER_PASSWORD`). Production would use OIDC/SSO (e.g. Azure AD or Keycloak) instead.

| User | Role | Position | Team |
|---|---|---|---|
| `claimant1`, `claimant2` | CLAIMANT | — | — |
| `officer1` | OFFICER | Claims Officer (L1) | Motor Claims |
| `officer2` | OFFICER | Senior Claims Officer (L2) | Motor Claims |
| `officer3` | OFFICER | Claims Officer (L1) | Property Claims |
| `lead1` | OFFICER | Team Lead (L3) | Motor Claims |
| `manager1` | MANAGER | Claims Manager (L4) | — |

With `DEMO_DATA_ENABLED=true` (the Compose default), the first start seeds eight claims in different states. One of them, `CLM-SG-…` for SGD 78,000 recommended by officer1, is waiting for approval: try approving it as `officer1` (blocked by the four-eyes rule), `officer2` (limit too low) and `lead1` (allowed).

## Approval authority

Each position can approve up to a limit **per currency** (`app.authority.limits`), so no FX conversion is ever needed:

| Position | SGD | MYR | HKD | THB | IDR | PHP |
|---|---|---|---|---|---|---|
| Claims Officer | 10k | 30k | 60k | 250k | 100M | 400k |
| Senior Claims Officer | 50k | 150k | 300k | 1.25M | 500M | 2M |
| Team Lead | 250k | 750k | 1.5M | 6M | 2.5B | 10M |
| Claims Manager | unlimited | | | | | |

```
UNDER_REVIEW ── assigned officer approves amount X (POST /decision)
   ├─ X within the officer's limit → APPROVED
   └─ X above the limit           → PENDING_APPROVAL (referred)
         POST /approval by someone else (four-eyes) whose limit covers X:
         ├─ APPROVE → APPROVED     ├─ RETURN (comment) → UNDER_REVIEW     └─ REJECT (comment) → REJECTED
```

Every claim response includes `allowedActions` for the current user, `waitingOn` (CLAIMS_TEAM, ASSIGNED_OFFICER, CLAIMANT, APPROVER or NOBODY) and a plain-language `nextStep`, so the frontend never re-implements the rules.

## API

Swagger UI: http://localhost:8081/swagger-ui.html (click **Authorize** and log in as a demo user).

| Method | Path | Role |
|---|---|---|
| POST | `/api/v1/claims` (optional `Idempotency-Key` header) | CLAIMANT |
| GET | `/api/v1/claims?status=&assignedTo=&page=&size=` | OFFICER, MANAGER |
| GET | `/api/v1/claims/mine` | CLAIMANT |
| GET | `/api/v1/claims/{id}` | any (claimants: own claims only) |
| POST | `/api/v1/claims/{id}/assignment` | OFFICER |
| POST | `/api/v1/claims/{id}/review` | OFFICER (assigned) |
| POST | `/api/v1/claims/{id}/information-requests` | OFFICER (assigned) |
| POST | `/api/v1/claims/{id}/information-requests/{requestId}/response` | CLAIMANT (owner) |
| POST | `/api/v1/claims/{id}/assessment` | OFFICER (assigned) |
| POST | `/api/v1/claims/{id}/decision` | OFFICER (assigned) |
| POST | `/api/v1/claims/{id}/approval` | OFFICER, MANAGER (not the recommender, enough authority) |
| POST | `/api/v1/claims/{id}/settlement` | OFFICER (assigned) |
| GET | `/api/v1/me` | any |

insights-service (Swagger UI: http://localhost:8082/swagger-ui.html). Read-only and eventually consistent: each report carries `asOf`, the time of the latest event applied.

| Method | Path | Role |
|---|---|---|
| GET | `/api/v1/insights/workload` | OFFICER, MANAGER |
| GET | `/api/v1/insights/performance` | OFFICER, MANAGER |
| GET | `/api/v1/insights/exposure` | MANAGER |

Officer teams are configured in `app.officer-teams` in insights-service.

Errors are RFC 7807 Problem Details: 400 validation, 401 unauthenticated, 403 not allowed, 404 not found (also returned for another claimant's claim), 409 invalid status or concurrent update, 422 business rule violated.

```bash
curl -u claimant1:password -H "Content-Type: application/json" -H "Idempotency-Key: $(uuidgen)" \
  -d '{"policyNumber":"POL-SG-1001","market":"SG","claimType":"MOTOR","incidentDate":"2026-11-01",
       "incidentDescription":"Rear-ended at a traffic light","claimedAmount":3200.00}' \
  http://localhost:8081/api/v1/claims

curl -u officer1:password "http://localhost:8081/api/v1/claims?status=SUBMITTED"
curl -u officer1:password -X POST http://localhost:8081/api/v1/claims/{id}/assignment
```

## Assumptions

1. The six markets are SG, MY, HK, TH, ID and PH, with currencies SGD, MYR, HKD, THB, IDR and PHP.
2. Policy numbers are not validated (no policy admin system integration).
3. Claimants answer information requests with text only. No document uploads.
4. Only the assigned officer works a claim. Any officer can pick up an unassigned one. Approvers of referrals can be any officer or manager with enough authority; they are not restricted to the officer's own team.
5. Approving a claim requires an assessment first, and the approved amount can't exceed the claimed amount.
6. Exposure is reported per currency. Amounts in different currencies are never summed.
7. Officers can reject within their own review without referral. Senior sign-off for high-value rejections is a documented next step.
8. Positions and teams come from configuration here. In production they would arrive as identity-provider group claims; approval limits stay in the claims system because they are audited business rules, not identity data.

## Out of scope

_Explained with reasons in Phase 7:_ SSO/OIDC, file storage, payments, FX conversion, email/SMS, policy admin integration, fraud scoring, i18n, Kubernetes/cloud, CI/CD.
