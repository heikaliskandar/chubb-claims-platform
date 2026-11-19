# Security and privacy

> The platform handles personal and financial data in six APAC markets with data protection laws (PDPA in Singapore, Malaysia and Thailand; DPA in the Philippines; PDP Law in Indonesia; PDPO in Hong Kong). Decision record: [ADR 0007](../adr/0007-personal-data-protection.md).

## Authentication

| Control | Detail |
|---|---|
| Mechanism | HTTP Basic over a stateless API (no server session). OIDC with MFA is the production path ([README](../../README.md#path-to-production)). |
| Passwords | Hashed with Spring Security's delegating encoder (bcrypt). |
| Brute force | **5 consecutive failures lock the account for 15 minutes**, in both services. The lockout is written to the audit log. |
| Session in the browser | `sessionStorage`, scoped to one tab: survives a reload, cleared on tab close and sign-out. Never `localStorage` or cookies. |
| No browser pop-up | 401 responses carry no `WWW-Authenticate` header, so the browser never shows its own login dialog. |

## Authorization

| Layer | How |
|---|---|
| Endpoint | `@PreAuthorize` checks the role on every controller method (CLAIMANT, OFFICER, MANAGER). |
| Record | Claimants: their own claims only. Staff: unassigned claims plus their reporting line ([employees](employees-and-hierarchy.md)). |
| Action | The domain decides each command (assigned officer, four-eyes, authority limit), and returns `allowedActions` so the UI matches. |
| Not revealing existence | Out-of-scope claims and people return **404**, not 403. |

## Personal data (PDPA)

| Principle | Control |
|---|---|
| Minimisation | Kafka events and the insights database hold **no personal data**: no policy number, incident description or answers. |
| Need to know | Record-level visibility above; internal fields (reserve, assessments, notes) are never sent to claimants. |
| Accountability | A dedicated `AUDIT` logger records every claim view, colleague-profile view, contact change and lockout as `action actor role resource id`. It holds identifiers, never the data itself. |
| Integrity | Database CHECK constraints on every code column and on the completeness of decisions, referrals and answers (`V5__add_domain_constraints.sql`). |

## Uploaded files

File type is detected from the content (PDF, JPEG, PNG and WebP only), never from the name. Size and count are limited. Files are stored under generated keys, so a file name can never become a path. Only the claimant and the assigned officer can upload, and only while the claim is open. Downloads are attachments with `no-store`, `nosniff` and a sandbox CSP, and every upload and download is audited. See [supporting documents](supporting-documents.md).

## Browser and transport

nginx serves the app with:
- a strict **Content Security Policy**: `default-src 'self'`, and no inline scripts except two pinned by SHA-256 hash;
- `frame-ancestors 'none'` and `X-Frame-Options: DENY`;
- `nosniff`, `Referrer-Policy`, `Permissions-Policy` and `Cross-Origin-Opener-Policy`;
- no server version shown.

CSRF protection is off because the API is stateless and uses no cookies. It must be switched back on if cookie sessions are introduced.

## Minimal exposure

- Errors are RFC 7807 Problem Details: no stack traces, SQL or class names.
- Health endpoints hide component details from anonymous callers.
- OpenAPI and Swagger UI are off unless `API_DOCS_ENABLED=true`, which only the local Compose stack sets.
- `robots.txt` blocks indexing.

## Still needed for production

These are listed in ADR 0007:
- OIDC and MFA;
- TLS everywhere;
- encryption at rest, and column-level encryption for the most sensitive fields;
- least-privilege database roles;
- a tamper-evident audit store;
- retention and erasure jobs;
- data-residency checks per market;
- gateway rate limiting and a secrets vault.

## Code

| Concern | Location |
|---|---|
| Security config | `common/config/SecurityConfig.java` (both services) |
| Lockout | `common/security/LoginAttempts.java` |
| Audit | `common/audit/AccessAudit.java` |
| Headers | `frontend/nginx.conf` |

## Tests

- `ClaimControllerTest`: 401 and 403 handling and role boundaries.
- `LoginAttemptsTest`: lockout, expiry and reset.
- e2e `06-api-and-security.spec.ts`: Problem Details, no login pop-up, role boundaries, every security header.
