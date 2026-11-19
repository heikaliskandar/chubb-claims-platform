# CHUBB APAC ENGINEERING — Take-Home Assessment

**Young Graduate Program (Full-stack Developer)**

**Time Guidance:** 2–3 hours target | Hard cap: 5 hours

---

## Background

Chubb APAC processes motor and property claims across six markets. Today the process is fragmented: claimants submit by phone or email and wait with no visibility into what is happening. Claims staff manage incoming work from shared inboxes and spreadsheets, with no consolidated view of their workload. Managers have no real-time picture of outstanding claims or liability exposure.

Your task is to build the platform.

**Claimants** need to be able to report an incident, track their claim, provide additional information when asked, and receive decisions.

**Claims staff** need to be able to pick up incoming claims, review and assess them, progress claims to settlement or rejection, and see their team's workload and performance.

Both user types access the same Angular application.

## Technology (Preferred)

- **Frontend:** Angular — it is the frontend framework across both platforms.
- **Backend:** C#/.NET or Java/Spring Boot
- **Communication:** REST/HTTP and Kafka. You decide what uses which, and you should be prepared to explain that decision.

No other technology constraints. Database, caching, testing libraries, API tooling, frontend architecture — all your decisions.

## Approach

Start with the backend. The backend is the foundation of the platform and where we expect the majority of your time to go. Extend to the frontend if time permits.

A well-built backend with a partial frontend will outscore a rushed attempt at both. The decision about how to allocate your time — and how you explain it — is part of the assessment.

## What to Build

This brief is deliberately underspecified. You decide how to decompose the problem, what the service boundaries are, what the data model looks like, how the frontend is structured, and what to prioritize. The decisions you make — and your ability to explain them — are a significant part of what is being assessed.

Some questions to consider as you decompose the problem:

- What are the core entities in this domain? How do they relate?
- Is the backend one service or more than one? What drives that decision?
- What operations need to be synchronous? What can be event-driven?
- What is the contract between frontend and backend, and who defines it?
- How do you handle the async nature of claims processing across both tiers?
- What does a claims officer need to see to manage their workload effectively?
- What does the system need to know about outstanding liability exposure?

You will not build everything. Decide what matters most and build that well.

## Deliverables

1. Git repository with meaningful commit history showing your development process
2. Working application that can be started locally
3. AI working journal — a running log (committed alongside the code) of what you asked the AI, what you accepted, what you challenged, and what you overrode, with brief reasoning. Does not need to be polished.
4. Any supporting documentation you feel is appropriate

## Notes

This is a sprint-format assessment. Target 2–3 hours. Hard cap: 5 hours. We are not expecting a finished product — we are evaluating how much you can build, and how well, when you work with AI effectively.

- This brief is deliberately underspecified. We expect you to decompose the problem, make technology and architecture decisions, and design a solution. Those decisions are part of what we are evaluating.
- Prioritise ruthlessly. Decide what to build and what to leave out, and be prepared to explain that prioritisation in the walkthrough.
- AI is your primary working interface. We expect AI tooling to drive the bulk of code generation. What we are evaluating is how you direct, challenge, and override it. Document your process as you go. You sign off every line you submit — the panel will probe anything you cannot defend.

If you have questions about the assessment, contact the hiring panel at <Vinitha.SooreshRao@Chubb.com> or <Keshwin.Dhillon@chubb.com>

**Good luck.**
