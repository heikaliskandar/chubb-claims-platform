# ADR 0006: The reporting line drives visibility and approvals

**Status:** Accepted

## Context

Staff need a directory with positions and a hierarchy. In claims operations the hierarchy is not just HR data: it decides who may see a colleague's work and who may approve a referral. ADR 0005 left approvals open to anyone with enough authority, regardless of team.

## Decision

- **Employee directory in claims-service.** An `employee` table holds the employee number, name, contact details, position, department, team, market, office, join date, employment type, status and `manager_username` (a self-reference).
- **Everyone below a person** is computed with a recursive SQL query. It uses `UNION`, not `UNION ALL`, so a cycle introduced by bad data cannot recurse forever.
- **Positions come from the directory at login.** They are added as `POSITION_*` authorities, mirroring an identity provider that syncs groups from HR.
- **Visibility: below, never above.**
  - The directory and profiles show you and everyone below you. Anyone else returns 404, so their existence isn't revealed.
  - Your own profile shows your direct manager's name and position, but you cannot open their profile.
  - Claims: staff see unassigned claims plus claims assigned to themselves or anyone below them.
- **Approvals follow the reporting line.** A referral can only be reviewed by someone the recommending officer reports to, directly or indirectly, who also has enough authority. The four-eyes rule still applies first.
- **Self-service:** employees can edit their own phone and office. Everything else belongs to the HR system of record.

## Alternatives considered

- **A separate staff-service.** A cleaner bounded context, but it adds a service, a database and cross-service calls on every approval check. Worth doing once more modules need staff data.
- **Approval routing by team name instead of reporting line.** Simpler, but it breaks for cross-team escalation and for managers above several teams.
- **Visibility by market.** Common in multi-country insurers and compatible with this model; the reporting line was chosen first because the brief is organised around teams.

## Consequences

- One extra query per claim command to load the reporting line. It is indexed (`manager_username`) and small; it could be cached per request or per session at scale.
- insights-service dashboards are not yet filtered by reporting line.
- In production the directory would be synced from the HR system rather than seeded.
