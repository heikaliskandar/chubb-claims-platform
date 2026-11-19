# ADR 0008: Supporting documents: metadata in PostgreSQL, content behind a storage interface

**Status:** Accepted

## Context

Claimants and officers need to attach evidence (photos, police reports, repair quotes, adjuster reports) to a claim. The files are personal data, can be large, and must be kept as an audit trail. Uploaded files are also a classic attack route: disguised executables, HTML or SVG that runs script when opened, path traversal through file names, and oversized uploads.

## Decision

- **A separate `claim_document` table**, not a child collection of the claim aggregate. Adding evidence doesn't change the claim's state, so it doesn't bump the claim's optimistic-lock version or conflict with an officer's concurrent update. Visibility and upload rights still come from the claim (`ClaimService.findVisibleClaim`, `Claim.acceptsDocumentsFrom`).
- **Content behind `DocumentStorage`.** The local build stores files on a Docker volume under `claimId/documentId`. Production swaps in object storage (S3, Azure Blob, GCS) with server-side encryption, with no change outside that class.
- **Type detection from content.** Only PDF, JPEG, PNG and WebP, recognised by their signatures. The client's content type and the file extension are ignored.
- **Immutable.** No delete endpoint; corrections are made by adding a newer document.
- **Hardened downloads.** Served as attachments with `no-store`, `nosniff` and a sandbox CSP.
- **Limits** of 10 MB per file and 20 per claim, enforced by Spring multipart, nginx and the service.

## Alternatives considered

- **Files in PostgreSQL (`bytea`).** Transactional and simple, but it bloats the database, its backups and replication, and doesn't match how insurers store documents at scale.
- **Uploading straight to object storage with pre-signed URLs.** The right shape at high volume, because large files never pass through the service. It needs object storage and a confirm step first; it can be added behind the same API later.
- **Documents as events on Kafka.** No: events carry facts, not files, and the event stream holds no personal data ([ADR 0007](0007-personal-data-protection.md)).

## Consequences

- One extra table and one volume to back up alongside the database. A file and its row are written in the same request; if the insert fails the file is removed.
- **Not done here:** virus scanning before a file becomes visible, retention and erasure of documents together with the claim, and per-document visibility (for example an internal-only adjuster note). All three are listed as production work in the README.
