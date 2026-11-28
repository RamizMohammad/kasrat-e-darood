# Kasrat-e-Darrod / Noor

An Islamic community recitation tracker. It replaces WhatsApp-style tallies
("Surah Yaseen ×3", "Darood ×500") with structured in-app submissions that roll
up into personal totals, group totals, weekly sessions, leaderboards and an
archived Hall of Fame.

This repository holds **two projects** that share one API contract:

| Path | What | Stack |
|------|------|-------|
| `app/` | Native Android client | Java + XML views, Material 3, Retrofit |
| `backend/` | REST API + business logic | Python 3.12, FastAPI, MongoDB (Beanie), Redis, Celery |
| `docs/` | Shared design | `ARCHITECTURE.md`, `DATA_MODEL.md`, `API_CONTRACT.md` |

## Android app (`app/`)
Implements the Stitch-designed Noor UI exactly: splash, login, register, and a
five-tab bottom-nav shell — Home (dashboard with prayer times, Jumu'ah
countdown, personal-goal ring, group activity), Library, Groups (community
feed), Stats (weekly insights + leaderboard) and Profile. The Noor design
system (emerald + gold, serif/sans pairing, 24dp cards, glass surfaces) lives in
`res/values/colors.xml`, `themes.xml` and `type.xml`.

Screens currently render from `data/SampleData.java`; the Retrofit layer
(`data/remote/ApiService.java`, `ApiClient.java`, `AuthInterceptor.java`) is
wired to the backend contract and ready to swap in. Set the backend address in
`ApiClient.BASE_URL` (`http://10.0.2.2:8000/` from the emulator).

Open in Android Studio and Run. Package `in.mohammad.ramiz.islamic.kasrat_e_darrod`,
minSdk 31.

### Swapping in the exact Stitch fonts
Type currently maps to system `serif` (Source Serif 4 role) and `sans-serif`
(Plus Jakarta Sans role) so it builds with no assets. To ship the exact fonts,
drop the TTFs into `app/src/main/res/font/` and change the `android:fontFamily`
values in `res/values/type.xml` to `@font/...`.

## Backend (`backend/`)
Clean-architecture FastAPI service (routes → services → repositories → Mongo).
28 documented endpoints, Firebase→JWT auth, idempotent submission roll-ups, the
Friday lock / Hall of Fame, one-call dashboard, cached leaderboard, permission
system, rate limiting, health/metrics, Celery + WebSocket scaffolds.

```bash
cd backend
cp .env.example .env
docker compose up --build         # http://localhost:8000/docs
# or locally:
pip install -r requirements.txt && uvicorn app.main:app --reload
pytest -q                         # 7 tests pass
```
See `backend/README.md` for details.

## How they connect
The Android `ApiService` methods map 1:1 to backend routes in
`docs/API_CONTRACT.md`. Auth flow: client signs in with Firebase → posts the
Firebase ID token to `/api/v1/auth/firebase` → stores the returned app JWT →
`AuthInterceptor` attaches it to every request.
