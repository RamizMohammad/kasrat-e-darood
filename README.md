# Noor — Backend (FastAPI)

Production-oriented backend for **Noor / Kasrat-e-Darrod**, an Islamic community
recitation tracker. Built with FastAPI + MongoDB (Beanie) + Redis + Celery,
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
cp .env.example .env
uvicorn app.main:app --reload
python -m scripts_seed        # seed the global recitation library
```

## Layout
```
app/
  api/health.py          ops endpoints
  api/router.py          v1 aggregator
  api/v1/<module>/router.py   thin HTTP routes
  services/              business logic (framework-agnostic)
  repositories/          MongoDB access
  models/                Beanie documents
  schemas/               Pydantic v2 DTOs
  security/              JWT, Firebase, permissions
  dependencies/          auth + per-group authorization
  middlewares/           request-id, rate limit
  database/              Mongo + Redis clients
  tasks/                 Celery app + jobs
  websocket/             realtime manager + /ws
tests/                   pytest (unit + integration)
```

## Architecture
Routes → Services → Repositories → MongoDB. No business logic in routes; no
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

## Tests
```bash
pytest -q          # 7 tests: tokens, permissions, submission roll-up, Friday lock
```

## Implemented now vs. scaffolded
**Implemented & tested:** auth (Firebase→JWT, refresh rotation, logout), users,
groups + memberships + invite codes, categories/recitations, submissions
(single/bulk/undo, idempotent roll-ups), weekly sessions + Friday lock + Hall of
Fame, dashboard (one-call), leaderboard (cached, tie-aware), permission system,
rate limiting, structured logging, health/metrics, WebSocket manager.
**Scaffolded for extension:** Celery jobs (reminders/streaks/reports),
statistics/achievements/goals/notifications/announcements modules — add a
model + repo + service + router without touching existing code.
