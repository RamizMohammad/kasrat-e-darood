# Noor — Architecture Overview

Noor (product codename for **Kasrat-e-Darrod**) is an Islamic community recitation
tracking platform. It replaces ad-hoc WhatsApp tallies ("Surah Yaseen ×3",
"Darood ×500") with structured in-app submissions that automatically roll up into
personal totals, group totals, leaderboards, weekly sessions and an archived
"Hall of Fame".

This repository contains two deliverables that build against one shared API
contract (`docs/API_CONTRACT.md`):

```
KasrateDarrod/
├── app/            # Android client (Java + XML, Retrofit)  ← native UI
├── backend/        # FastAPI + MongoDB backend (Python)      ← server
└── docs/           # Architecture, data model, API contract
```

## Backend — Clean Architecture

The backend follows Clean Architecture. Dependencies point inward; business
logic never touches the web framework or the database driver directly.

```
HTTP request
   │
   ▼
api/v1/<module>/router.py      (FastAPI routes — thin, no business logic)
   │  depends on
   ▼
services/<module>_service.py   (use-cases / business rules)
   │  depends on
   ▼
repositories/<module>_repo.py  (persistence boundary, Mongo access)
   │  depends on
   ▼
models/ (Beanie documents)  +  database/ (Motor client)
```

Rules enforced:
- Routes only validate input (Pydantic schemas) and call a service.
- Services contain all business rules (submission roll-ups, Friday lock,
  ranking). They are framework-agnostic and unit-testable.
- Repositories are the only layer that talks to MongoDB.
- Cross-cutting concerns (auth, request-id, error handling, rate limiting)
  live in `middlewares/` and `dependencies/`.
- Everything is async. Dependency injection via FastAPI `Depends`.

This layering is what makes the system "easy to update and extend": a new
feature = new model + repo + service + router, with no edits to existing
modules. Modules can later be split into microservices because the service
layer has no infrastructure coupling.

## Core domain flow

1. **Submission** — user submits `{recitation_id, count}`. The submission
   service, inside a transaction: stores the submission, increments the user's
   weekly/lifetime totals, increments the group total, appends an activity-feed
   event, and (later) evaluates achievements. Returns updated totals.
2. **Weekly session** — every group has exactly one `ACTIVE` week. Submissions
   attach to the active week.
3. **Friday lock** — an admin calls *Present Weekly Recitations*. The service,
   in one transaction: computes totals + rankings, builds the Hall of Fame,
   sets the week `LOCKED`/`ARCHIVED`, snapshots statistics, then opens a new
   `ACTIVE` week. After lock, submissions for that week are immutable.
4. **Leaderboard / dashboard** — read models served from aggregation pipelines,
   cached in Redis with automatic invalidation on write.

## Tech stack
Python 3.12 · FastAPI · Pydantic v2 · Motor/Beanie · MongoDB Atlas · Redis ·
Celery · Firebase Auth + FCM · JWT · Docker · Uvicorn/Nginx · Pytest.

## Android client
Java + XML views, Material Components, Retrofit + Gson + OkHttp. MVVM-lite:
Activities/Fragments → Repository → Retrofit `ApiService`. The design system
(`docs/`-derived) is implemented in `res/values/colors.xml`, `themes.xml`,
`type` and shape styles to match the Stitch screens exactly.

See `DATA_MODEL.md` for collections/indexes and `API_CONTRACT.md` for endpoints.
