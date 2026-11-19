# Technology stack

Every runtime and library in the platform, with its version, licence and why it was chosen. All are free and open source; no commercial licence is needed to build, run or ship the platform.

![Java](https://img.shields.io/badge/Java_21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_4.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Angular](https://img.shields.io/badge/Angular_22-DD0031?style=for-the-badge&logo=angular&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript_6-3178C6?style=for-the-badge&logo=typescript&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL_17-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![Apache Kafka](https://img.shields.io/badge/Apache_Kafka_4.4-231F20?style=for-the-badge&logo=apachekafka&logoColor=white)
![Docker](https://img.shields.io/badge/Docker_Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![nginx](https://img.shields.io/badge/nginx-009639?style=for-the-badge&logo=nginx&logoColor=white)
![Playwright](https://img.shields.io/badge/Playwright-2EAD33?style=for-the-badge&logo=playwright&logoColor=white)

## Backend

| Technology | Version | Licence | Used for | Why |
|---|---|---|---|---|
| Java (Eclipse Temurin) | 21 LTS | GPLv2 + Classpath Exception | Both services | Long-term support; records, sealed types and pattern-matching `switch` keep the domain model concise. |
| Spring Boot | 4.1.2 | Apache 2.0 | Application framework | Industry standard for Java services; one consistent way to do web, data, security, Kafka and configuration. |
| Spring Framework | 7.0 | Apache 2.0 | Web MVC, transactions, events | Underpins Boot. |
| Spring Security | 7.1 | Apache 2.0 | Authentication, `@PreAuthorize`, password hashing | The standard; the move to OIDC is a configuration change. |
| Spring Data JPA + Hibernate ORM | Hibernate 7.4 | Apache 2.0 / LGPL | claims-service persistence, `@Version` locking, Specifications | A rich aggregate with child collections suits an ORM. |
| Spring JDBC (`JdbcClient`) | 7.0 | Apache 2.0 | insights-service read model | Plain SQL is clearer than an ORM for upserts and aggregates. |
| Spring for Apache Kafka | 4.1 | Apache 2.0 | Producer, consumer, retries, dead-letter topic | Built-in error handling and DLT support. |
| Flyway | 12.4 | Apache 2.0 | Database migrations | Versioned, reviewable SQL; the app validates the schema at start-up. |
| PostgreSQL JDBC driver | 42.7 | BSD-2 | Database access | Official driver. |
| Jakarta Bean Validation (Hibernate Validator) | 3.1 | Apache 2.0 | Request validation | Declarative rules on request records. |
| springdoc-openapi | 3.2.0 | Apache 2.0 | OpenAPI 3 spec and Swagger UI | The contract is generated from the code, so it can't go stale. |
| Spring Boot Actuator | 4.1 | Apache 2.0 | Health and readiness endpoints | Container health checks. |
| Jackson | 3.1 | Apache 2.0 | JSON | Boot default. |
| Maven Wrapper | 3.10 | Apache 2.0 | Build | Anyone can build without installing Maven. |

## Data and messaging

| Technology | Version | Licence | Used for | Why |
|---|---|---|---|---|
| PostgreSQL | 17 (Alpine image) | PostgreSQL License | One database per service | Reliable, exact `NUMERIC` money, recursive queries, partial indexes, `SKIP LOCKED` for the outbox. |
| Apache Kafka (KRaft mode) | 4.4.0 | Apache 2.0 | `claims.claim-events.v1` and its DLT | Durable, ordered per key, replayable; KRaft needs no ZooKeeper. |
| Kafka UI (kafbat) | 1.5.0 | Apache 2.0 | Browse topics and events locally | Makes the event flow visible in a demo. |

## Frontend

| Technology | Version | Licence | Used for | Why |
|---|---|---|---|---|
| Angular | 22.3 | MIT | The single app for every user type | Required by the brief; standalone components, signals and zoneless change detection. |
| TypeScript | 6.0 | Apache 2.0 | Language | Strict typing of the API contract. |
| RxJS | 7.8 | Apache 2.0 | HTTP streams, polling | Angular's async model. |
| Chart.js | 4.6 | MIT | Workload bar charts | Small, accessible canvas charts; recoloured with the theme. |
| Material Symbols Rounded | (Google Fonts) | Apache 2.0 | Icons | Only the ~55 icons used are downloaded. |
| Plus Jakarta Sans | (Google Fonts) | SIL OFL 1.1 | Typeface | Clean, legible at small sizes. |
| **UI components** | in-repo | — | Buttons, inputs, tables, tabs, tags, modal, toasts, pager, avatar, org tree | No component library. A commercial licence banner from an earlier library was the trigger; owning the components also keeps the bundle small and accessibility under our control. See [frontend](features/frontend.md#design-system). |
| Vitest + jsdom | 5.1 | MIT | Frontend unit test runner | Angular's current default. |
| Prettier | 3.8 | MIT | Formatting | Consistent style without debate. |

## Testing

| Technology | Version | Licence | Used for |
|---|---|---|---|
| JUnit 5, AssertJ, Mockito | (Boot managed) | EPL 2.0 / Apache 2.0 / MIT | Unit and web-layer tests |
| Spring MockMvc, Security Test | (Boot managed) | Apache 2.0 | Controller and security tests |
| Testcontainers | 2.0.5 | MIT | Integration tests against real PostgreSQL and Kafka |
| Awaitility | (Boot managed) | Apache 2.0 | Waiting for asynchronous consumers |
| Playwright | 1.65 | Apache 2.0 | End-to-end tests, desktop and mobile |
| axe-core (`@axe-core/playwright`) | 4.14 | MPL 2.0 | Automated WCAG 2.1 AA checks |
| Lighthouse | 12 | Apache 2.0 | Performance, accessibility and best-practice audits |

## Runtime and delivery

| Technology | Version | Licence | Used for |
|---|---|---|---|
| Docker Compose | v2 | Apache 2.0 | One command starts the whole stack |
| GitHub Actions | — | — | CI: backend, frontend and end-to-end jobs on every push |
| Eclipse Temurin images | 21 JDK (build) / 21 JRE (run) | GPLv2 + CE | Multi-stage service images |
| Node | 24 (Alpine, build only) | MIT | Builds the Angular app |
| nginx | 1.31 (Alpine) | BSD-2 | Serves the app, proxies `/api`, sets security and cache headers |

## Versions policy

- Exact versions are pinned: Maven parent and properties, `package-lock.json`, image tags.
- Library versions were checked against the official registries when chosen, not taken from memory.
