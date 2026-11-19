# API reference

## Live documentation

With the stack running, open **http://localhost:4200/api/docs**.

- **Both services in one place.** Use *Select a definition* to switch between *Claims service* and *Insights service*.
- **Generated from the running code** by springdoc-openapi on every request. A new endpoint, parameter or field appears as soon as the service restarts, so the docs can't drift from the code.
- **Try it out.** Click **Authorize**, enter a demo user (for example `officer1` / `password`), and call any endpoint. Requests go through the same nginx proxy and the same security as the web app, so what works here works in the app. Request duration is shown, to help with debugging.
- **Every operation lists its errors** (400, 401, 403, 404, and 413, 415 or 422 where they apply) with the RFC 7807 Problem Details schema. A customizer adds the standard ones to every operation automatically, so a new endpoint can't forget them.
- **The introduction page** explains authentication, roles, visibility (why some calls return 404), `allowedActions`, idempotency, paging and eventual consistency.

Raw specs: `/v3/api-docs/claims` and `/v3/api-docs/insights`. The docs are switched on by `API_DOCS_ENABLED=true`, which only the local Compose stack sets; they are off by default.

## The committed contract

| File | Service |
|---|---|
| [`claims-service.openapi.json`](claims-service.openapi.json) | Claims, approvals, documents, employees, current user |
| [`insights-service.openapi.json`](insights-service.openapi.json) | Workload, performance, exposure |

Each service has an `OpenApiContractIT` test, run by `./mvnw verify` and in CI, that:

1. **checks quality:** every operation has a summary and a tag, and documents 401 (and 404 when the path has an ID);
2. **compares the live spec with the committed file**, and fails if they differ.

So an API change can't slip through unnoticed: it shows up as a readable diff in this folder, in the same pull request as the code. To accept a change on purpose:

```bash
cd services/claims-service      # or services/insights-service
./mvnw verify -Dopenapi.update=true
git add ../../docs/api
```

The same files can be used to generate typed clients, for example `openapi-generator` for the Angular models, and for contract tests in other teams.
