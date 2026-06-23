# Kasrat-e-Darood — Web Frontend

Static landing page + closed-beta signup form for **Kasrat-e-Darood**.
Trilingual (English / हिंदी / اردو, with RTL for Urdu), Android-only, fully
responsive. It is a pure static site (one `index.html`, no build step) that talks
to the API over HTTPS.

## Architecture
The frontend is deployed **separately** from the API (e.g. on Vercel) and calls
the backend cross-origin:

```
Browser ──▶ kasrat-e-darood.mohammadramiz.in   (this site, Vercel CDN)
   │
   └── fetch ──▶ api.kasrat-e-darood.mohammadramiz.in   (FastAPI backend)
```

The API enforces CORS so only this origin's browser requests are accepted.
(Native/non-browser clients are not subject to CORS — the API's real protection
is auth, rate limiting and validation.)

## Configure the API URL
Edit the one constant near the bottom of `index.html`:

```js
const API_BASE = "https://api.kasrat-e-darood.mohammadramiz.in";
```
Leave it `""` to call the same origin (useful when the backend serves the page
locally during development).

## Deploy to Vercel
1. Push this `frontend/` folder to a Git repo (or `vercel` CLI from here).
2. In Vercel: **New Project** → import the repo.
   - Framework preset: **Other** (no build).
   - Root directory: this folder.
   - Build command: *(none)* · Output directory: *(leave default / `.`)*
3. Add your custom domain `kasrat-e-darood.mohammadramiz.in`.
4. Deploy. `vercel.json` adds basic security headers.

Or from the CLI:
```bash
npm i -g vercel
vercel            # preview
vercel --prod     # production
```

## Point the backend's CORS at this site
On the API host set these environment variables (see backend README):

```bash
APP_ENV=production
CORS_ALLOW_ORIGINS=https://kasrat-e-darood.mohammadramiz.in
# Optional: allow Vercel preview deployments too
CORS_ALLOW_ORIGIN_REGEX=https://.*\.vercel\.app
```

## Anti-spam
The form includes a hidden honeypot field (`website`). Real users never see it;
bots that fill it get a silent fake-success and are not stored. The API also
rate-limits requests.
