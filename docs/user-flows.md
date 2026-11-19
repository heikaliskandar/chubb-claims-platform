# User flows

How each type of user works in the platform, mapped to the brief. Every flow can be followed in the running app with the demo data. All demo passwords are `password`.

![Claims portal sign-in](images/claims-portal-mockup.webp)

## Who uses it

| User | Role | Position | Reports to | Approves up to (SGD) | Brief |
|---|---|---|---|---|---|
| `claimant1`, `claimant2` | Claimant | — | — | — | *report an incident, track the claim, provide information when asked, receive decisions* |
| `officer1` | Claims staff | Claims Officer, Motor | lead1 | 10,000 | *pick up incoming claims, review and assess, progress to settlement or rejection* |
| `officer2` | Claims staff | Senior Claims Officer, Motor | lead1 | 50,000 | same, with higher authority |
| `officer3` | Claims staff | Claims Officer, Property | lead2 | 10,000 | same |
| `lead1`, `lead2` | Claims staff | Team Lead | manager1 | 250,000 | *see the team's workload and performance*, approve referrals |
| `manager1` | Manager | Claims Manager | — | unlimited | *real-time picture of outstanding claims and liability exposure* |

Limits are per currency (see the README), so no FX conversion is involved.

## The claim lifecycle

```mermaid
stateDiagram-v2
    [*] --> SUBMITTED: Claimant reports an incident
    SUBMITTED --> ASSIGNED: Officer picks it up from the queue
    ASSIGNED --> UNDER_REVIEW: Officer starts the review
    UNDER_REVIEW --> INFO_REQUESTED: Officer asks a question
    INFO_REQUESTED --> UNDER_REVIEW: Claimant answers
    UNDER_REVIEW --> APPROVED: Officer approves within own limit
    UNDER_REVIEW --> PENDING_APPROVAL: Officer recommends an amount above own limit
    PENDING_APPROVAL --> APPROVED: Someone above approves
    PENDING_APPROVAL --> UNDER_REVIEW: Someone above returns it
    PENDING_APPROVAL --> REJECTED: Someone above rejects
    UNDER_REVIEW --> REJECTED: Officer rejects with a reason
    APPROVED --> SETTLED: Officer marks payment made
    REJECTED --> [*]
    SETTLED --> [*]
```

Every claim screen shows the status, **who it is waiting on** (claims team, assigned officer, claimant, approver or nobody) and the **next step** in plain language. Buttons appear only when the current user is allowed to take that action.

## End to end, across users

```mermaid
sequenceDiagram
    actor C as Claimant
    actor O as Claims officer
    actor L as Team lead
    actor M as Manager
    participant API as claims-service (REST)
    participant K as Kafka
    participant I as insights-service

    C->>API: Submit claim (form)
    API-->>C: Claim number, status Submitted
    API--)K: ClaimSubmitted
    O->>API: Queue → Assign to me → Start review
    O->>API: Request information
    API--)K: InformationRequested
    K--)I: notify claimant (stub), update read model
    C->>API: Respond (banner on My claims)
    O->>API: Assess (sets the reserve)
    API--)K: ClaimAssessed
    K--)I: exposure increases
    O->>API: Approve amount above own limit
    API-->>O: Pending approval (referred)
    L->>API: Approvals → Waiting for me → Approve payment
    API--)K: ClaimApproved
    O->>API: Mark settled
    API--)K: ClaimSettled
    K--)I: exposure released
    M->>I: Dashboard (exposure, workload, performance)
    C->>API: Sees decision, amount and reason
```

REST is used wherever someone is waiting for an answer. Kafka carries facts that other parts of the system react to: dashboards and claimant notifications. Why: [ADR 0002](adr/0002-rest-vs-kafka.md).

## Claimant

```mermaid
flowchart LR
    A[Sign in] --> B[My claims]
    B -->|New claim| C[Choose Motor or Property,<br/>market, policy, date,<br/>amount, what happened]
    C -->|Submit claim| D[Claim page:<br/>number, status, next step]
    B -->|Banner: We need more information| E[Claim page]
    E -->|Respond| F[Answer sent,<br/>back with the officer]
    B -->|History| G[Settled and rejected claims]
    G --> H[Decision: outcome,<br/>amount and reason]
```

| Step | Screen | What the claimant sees |
|---|---|---|
| Report an incident | **Claims → New claim** | Form with validation; a double click cannot create two claims (`Idempotency-Key`). Optional **supporting documents** (photos, police report, quote). Lands on the new claim's page. |
| Track | **Claims → My claims** | Active claims with status and who they are waiting on. Updates every 10 seconds. |
| Provide information | Banner **We need more information** → claim → **Respond** | The officer's question and an answer box; files go under **Supporting documents** on the same page. |
| Receive the decision | **Claims → History** → claim | Outcome, approved amount and reason, plus the full activity timeline. |

Claimants never see other people's claims (404), staff pages, or internal notes such as the reserve.

## Claims officer

```mermaid
flowchart LR
    Q[Queue:<br/>unassigned claims] -->|Assign to me| A[Assigned to me]
    A -->|Start review| R[Under review]
    R -->|Request info| W[Waiting on claimant]
    W -->|Claimant responds| R
    R -->|Assess: set reserve| R
    R -->|Approve within limit| AP[Approved]
    R -->|Approve above limit| P[Pending approval:<br/>appears in Sent by me]
    R -->|Reject with reason| X[Rejected]
    AP -->|Mark settled| S[Settled]
```

| Step | Screen | Notes |
|---|---|---|
| Pick up work | **Claims → Queue** → claim → **Assign to me** | If two officers click at once, one wins and the other gets a clear conflict message (optimistic locking). |
| Review and assess | **Start review**, **Request info**, **Assess** | Each assessment is kept; the latest sets the reserve that drives exposure. |
| Decide | **Approve** or **Reject** | Above the officer's limit for that currency, approval becomes a referral to someone above them. |
| Settle | **Mark settled** | Releases the reserve from exposure. |
| See own workload | **Claims → Assigned to me**, **Approvals → Sent by me** | |
| See the team | **Team → Performance**, **Team → People** | Open claims by officer and status, decisions, average time to decision. |

Officers see unassigned claims plus their own. They cannot see claims held by colleagues at the same level.

## Team lead

```mermaid
flowchart LR
    I[Approvals → Waiting for me] -->|Review and decide| D[Approval needed panel:<br/>why it needs you, amounts, reason]
    D -->|Approve payment| A[Approved]
    D -->|Send back for changes| R[Back to the officer:<br/>Under review]
    D -->|Decline the claim| X[Rejected]
    I --> H[Decided tab:<br/>everything signed off]
    T[Team → Performance] --> W[Workload per officer,<br/>decisions, avg hours]
```

- **Four-eyes:** the person who recommended an amount can never approve it.
- **Reporting line:** a referral can only be reviewed by someone above the recommending officer with enough authority. lead2 cannot see lead1's team's referrals.
- Team leads see their whole reporting line's claims and people, and can also work claims as an officer.

## Manager

```mermaid
flowchart LR
    D[Overview dashboard] --> E[Outstanding reserve<br/>per currency]
    D --> M[Exposure by market<br/>and claim type]
    D --> W[Workload by officer]
    D --> C[Claims → All open / History]
    D --> A[Approvals:<br/>anything above team-lead limits]
```

Totals are **never summed across currencies**. Dashboards come from the event-fed read model and show **Updated hh:mm:ss**, so it is clear they are near-real-time rather than transactional.

## Demo data: one claim per stage

A fresh stack (`docker compose down -v && docker compose up --build -d`) contains exactly these claims. Each one shows a different part of the flow.

| Claim | Stage | Waiting on | What it shows | Try it as |
|---|---|---|---|---|
| CLM-SG-2026-000001 | Submitted | Claims team | A new motor claim in the queue | `officer1`: Queue → Assign to me |
| CLM-MY-2026-000002 | Submitted | Claims team | A property claim in the queue | `officer3` |
| CLM-PH-2026-000003 | Assigned | officer3 | Picked up, review not started | `officer3`: Start review |
| CLM-SG-2026-000004 | Under review | officer1 | Question asked **and answered**, with the **repair quote and a damage photo** attached; assessed at SGD 6,500, within officer1's limit | `officer1`: Approve SGD 6,500, then Mark settled |
| CLM-TH-2026-000005 | Info requested | Claimant | Officer is waiting for the police report | `claimant2`: banner → Respond |
| CLM-SG-2026-000006 | Pending approval | Approver | officer1 recommended SGD 78,000, above the SGD 10,000 limit, with the **loss adjuster report** attached | `lead1`: Approvals → Waiting for me → Review and decide |
| CLM-HK-2026-000007 | Approved | officer2 | Referral already approved by lead1 (four-eyes complete) | `officer2`: Mark settled; `lead1`: Approvals → Decided |
| CLM-PH-2026-000008 | Rejected | Nobody | Rejection with the policy exclusion as the reason | `claimant2`: History |
| CLM-ID-2026-000009 | Settled | Nobody | Full lifecycle completed | `claimant1`: History → decision |

Together they cover all six markets and every status, and give the manager dashboard open exposure in four currencies.

## Ten-minute walkthrough

1. **Claimant reports.** Sign in as `claimant1` → **New claim** → submit a Singapore motor claim for SGD 30,000. Point out the claim number, status and next step.
2. **Officer picks up.** Sign in as `officer1` → **Queue** → open the new claim → **Assign to me** → **Start review** → **Request info**.
3. **Claimant answers.** As `claimant1`, the **We need more information** banner appears → **Respond**.
4. **Officer assesses and decides above their limit.** As `officer1` → **Assess** SGD 30,000 → **Approve** SGD 30,000 → status becomes **Pending approval**; **Approvals → Sent by me** shows it as Pending.
5. **Team lead approves.** As `lead1` → **Approvals → Waiting for me** → **Review and decide**. The panel explains why it needs sign-off; choose **Approve payment**. Show that `lead2` never sees it.
6. **Officer settles; claimant sees the decision.** `officer1` → **Mark settled**; `claimant1` → **History** → decision card.
7. **Manager sees the effect.** `manager1` → **Overview**: SGD exposure rose at assessment and fell at settlement. Open Kafka UI (http://localhost:8090) to show the events behind it.
