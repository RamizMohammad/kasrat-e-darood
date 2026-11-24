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
user's lifetime total and the active week's total; it returns fresh totals and
is idempotent via `client_uuid`.

**Logs belong to users, not groups (v3).** There is one series of weeks (kept
on the global "Noor Community" group, `groups.is_global`). Every log points at
the active week. The global board sums everyone's logs; a group board sums its
active members' logs made **since they joined** (`memberships.created_at`). So
one log moves the global board and every group board the user is in, and
leaving a group takes your numbers off its board (they stay in your own stats).

**The week closes itself** on Friday at `WEEK_CLOSE_LOCAL_TIME` (default 18:30
IST, around Maghrib): archive + Hall of Fame, every group's week frozen into
`group_weeks` (total + top 3, "week winners" activity + push), and the next
week opens at that same instant. The job checks every 5 minutes and only acts
once the week is due, so a restart can't skip a close and a second run does
nothing. Super members/admins can still close early with
`POST /statistics/close-week?week_id=` (a repeated press is a no-op).

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
**Boards (B2):** `GET /leaderboards/global?scope=week|all&around_me=&limit=`
(tie-aware ranks, your row always in `me`, last week's rank as
`previous_rank`, people who hide themselves show as "A member" with no id or
picture), `GET /weeks/current`, `GET /weeks/hall-of-fame?cursor=`.
**Groups (B3):** `POST /groups` (≤ 10 owned, name 3–40 chars, any script),
`GET /groups/mine`, `GET /groups/{id}` (members: everything; outsiders: a
preview of public groups; 410 when deleted), `PATCH /groups/{id}`,
`DELETE /groups/{id}` (owner, `{"confirm_name": ...}`),
`GET /groups/{id}/board?scope=week|all` (total-only groups show members only
the total and their own number), `GET /leaderboards/groups?metric=total|avg`;
`GET /dashboard` adds `groups_this_week`.
**Joining (B4):** 6-character codes from `ABCDEFGHJKMNPQRSTUVWXYZ23456789`;
`GET /invites/{code}` (works signed out), `POST /invites/{code}/join`,
`POST /groups/{id}/join` (public), `POST /groups/{id}/invite/reset`,
`GET /groups/{id}/requests`, `.../requests/{uid}/approve|decline`,
`POST .../requests/approve` (bulk), `GET /groups/discover?q=&sort=active|new`.
Checks: banned, already in, full (500), deleted, 20 joins an hour, a declined
request may be repeated after 7 days. Outside the API prefix:
`/j/{code}` (invite page with a Play Store button) and
`/.well-known/assetlinks.json` (set `ANDROID_CERT_SHA256` to the upload key's
AND Play App Signing key's SHA-256, comma-separated).
**Running a group (B5):** `GET /groups/{id}/members?q=&status=`,
`PATCH .../members/{uid}` (role), `DELETE .../members/{uid}`,
`POST .../members/{uid}/ban|unban`, `POST /groups/{id}/leave`,
`POST /groups/{id}/transfer`, `POST /groups/{id}/announcements` (one pinned),
`GET .../announcements/latest`, `GET /groups/{id}/activity`,
`GET /groups/{id}/audit` (owner). The roles table is in
`app/security/permissions.py`; on top of it you can only remove, ban or
re-role someone you outrank. `POST /users/me/devices` registers an FCM token
(subscribed to `group_{id}` topics while `notify.groups` is on);
`DELETE /users/me/devices/{token}` on log out; `GET /notifications/inbox` for
personal notices (requests, decisions, role changes). A group's weekly goal
pushes "goal reached" once per week. Paged lists return `X-Next-Cursor`.
**Scheduled jobs** (in-process, `app/tasks/scheduler.py`): nightly cleanup of
expired tokens/codes, streak reset at 00:05 IST for users who missed a day,
the week close check every 5 minutes, and a quote push every 4 hours. No
Celery worker or beat.
**Scaffolded for extension:**
statistics/achievements/goals/notifications/announcements modules — add a
model + repo + service + router without touching existing code.
