# Noor — Backend (FastAPI)

Production-oriented backend for **Noor / Kasrat-e-Darrod**, an Islamic community
recitation tracker. Built with FastAPI + PostgreSQL (SQLAlchemy 2 async + Alembic) + Redis,
following Clean Architecture.

## Quick start (Docker)
```bash
cp .env.example .env          # edit secrets
docker compose up --build     # api on http://localhost:8000
```
Swagger UI: http://localhost:8000/docs

## Quick start (local)
```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env          # set DATABASE_URL
uvicorn app.main:app --reload # applies migrations on startup
python -m scripts_seed        # seed the global recitation library
```

## Layout
```
app/
  api/health.py          ops endpoints
  api/router.py          v1 aggregator
  api/v1/<module>/router.py   thin HTTP routes
  services/              business logic (framework-agnostic)
  repositories/          PostgreSQL queries
  models/                SQLAlchemy tables
  schemas/               Pydantic v2 DTOs
  security/              JWT, Firebase, permissions
  dependencies/          auth + per-group authorization
  middlewares/           request-id, rate limit
  database/              Postgres engine/sessions, migrations runner, Redis
  tasks/scheduler.py     scheduled jobs, run inside the API
migrations/              Alembic migrations (versions/0001_initial_schema.py)
  websocket/             realtime manager + /ws
tests/                   pytest (unit + integration)
```

## Architecture
Routes → Services → Repositories → PostgreSQL. No business logic in routes; no
infrastructure in services. See `../docs/ARCHITECTURE.md`,
`../docs/DATA_MODEL.md`, `../docs/API_CONTRACT.md`.

## Auth
Client signs in with Firebase, sends the Firebase ID token to
`POST /api/v1/auth/firebase`, and receives an app JWT access token + a rotating
refresh token. In development, leave `FIREBASE_CREDENTIALS_FILE` blank to use a
deterministic identity stub so the whole stack runs without Firebase.

## Core flow
`POST /api/v1/submissions` stores a recitation and atomically rolls up the
user's lifetime total, the active weekly session's group total + per-user
tally, and the activity feed; it returns fresh totals and is idempotent via
`client_uuid`. `POST /api/v1/groups/{id}/week/present` performs the Friday lock:
archives the week, builds the Hall of Fame, and opens the next week.

## Database
- One transaction per request: committed when the request succeeds, rolled
  back on any error.
- Counters (lifetime total, weekly group total) are updated with atomic
  `UPDATE ... SET n = n + x`, so concurrent submissions never lose counts.
- Schema changes: edit the models, then
  `alembic revision --autogenerate -m "what changed"` and review the file.
  `alembic upgrade head` applies them (startup also does this when
  `DB_AUTO_MIGRATE=true`).

## Tests
Tests run against a real, throw-away PostgreSQL database (it is wiped):
```bash
createdb kasrat_test
TEST_DATABASE_URL=postgresql://postgres:postgres@localhost:5432/kasrat_test pytest -q
```

## Implemented now vs. scaffolded
**Implemented & tested:** auth (Firebase→JWT, refresh rotation, logout), users,
groups + memberships + invite codes, categories/recitations, submissions
(single/bulk/undo, idempotent roll-ups), weekly sessions + Friday lock + Hall of
Fame, dashboard (one-call), leaderboard (cached, tie-aware), permission system,
rate limiting, structured logging, health/metrics, WebSocket manager.
**Me tab (B1):** `GET /statistics/me` returns a 365-day heat map, totals per
recitation, today / week / month / lifetime and current + best streak in one
call (cached 60 s per user, cleared on every log and undo). "A day" is a local
day in `DEFAULT_TIMEZONE` (Asia/Kolkata), not UTC. `PATCH /users/me` takes a
validated, partial `preferences` object (hide on boards, weekly goal, prayer
method, reminders, push types); `GET /submissions/me?limit=&before=` pages with
an `X-Next-Cursor` header.
**Scheduled jobs** (in-process, `app/tasks/scheduler.py`): nightly cleanup of
expired tokens/codes, streak reset at 00:05 IST for users who missed a day, and
a quote push every 4 hours. No Celery worker or beat.
**Scaffolded for extension:**
statistics/achievements/goals/notifications/announcements modules — add a
model + repo + service + router without touching existing code.
