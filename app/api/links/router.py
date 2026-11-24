"""Public pages on the invite domain (no /api/v1 prefix):

* /j/{code}  — what an invite link shows when the app isn't installed: the
  group's name and a Play Store button. With the app installed, Android's
  verified App Links open the app straight away and this page isn't seen.
* /.well-known/assetlinks.json — proves the app owns the domain.
"""
from __future__ import annotations

import html
import re

from fastapi import APIRouter, Depends
from fastapi.responses import HTMLResponse, JSONResponse

from app.config.settings import settings
from app.core.exceptions import AppError
from app.database.postgres import use_db
from app.services.group_service import invite_link
from app.services.join_service import join_service

router = APIRouter(include_in_schema=False, dependencies=[Depends(use_db)])

_CODE_RE = re.compile(r"^[A-Za-z0-9]{4,12}$")


@router.get("/.well-known/assetlinks.json")
async def assetlinks() -> JSONResponse:
    fingerprints = [f.strip().upper() for f in settings.ANDROID_CERT_SHA256.split(",") if f.strip()]
    body = [{
        "relation": ["delegate_permission/common.handle_all_urls"],
        "target": {"namespace": "android_app", "package_name": settings.ANDROID_PACKAGE,
                   "sha256_cert_fingerprints": fingerprints},
    }]
    return JSONResponse(body, headers={"Cache-Control": "public, max-age=3600"})


@router.get("/j/{code}", response_class=HTMLResponse)
async def invite_page(code: str) -> HTMLResponse:
    title, line, status = "Kasrat-e-Darood", "This invite link isn't valid any more.", 404
    if _CODE_RE.match(code):
        try:
            p = await join_service.preview_code(code, None)
            title = p.name
            members = f"{p.member_count:,} member" + ("" if p.member_count == 1 else "s")
            line = f"{members} · {p.week_total:,} recitations this week"
            status = 200
        except AppError as e:
            if e.code == "group_deleted":
                line = "This group was deleted."
    return HTMLResponse(_page(code.upper(), title, line, valid=status == 200), status_code=status,
                        headers={"Cache-Control": "no-store", "X-Robots-Tag": "noindex"})


def _page(code: str, title: str, line: str, *, valid: bool) -> str:
    e = html.escape
    link = invite_link(code)
    host_path = link.split("://", 1)[-1]
    intent = (f"intent://{host_path}#Intent;scheme=https;package={settings.ANDROID_PACKAGE};"
              f"S.browser_fallback_url={settings.PLAY_STORE_URL};end")
    if valid:
        body = f""" <div class="code">{e(code)}</div>
 <a class="btn primary" href="{e(intent)}">Open in the app</a>
 <a class="btn ghost" href="{e(settings.PLAY_STORE_URL)}">Get it on Google Play</a>
 <p style="margin:18px 0 0;font-size:13px">In the app: Groups → Join with code → {e(code)}</p>"""
    else:
        body = f""" <a class="btn ghost" href="{e(settings.PLAY_STORE_URL)}">Get it on Google Play</a>"""
    return f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>{e(title)} · Kasrat-e-Darood</title>
<meta property="og:title" content="Join {e(title)} on Kasrat-e-Darood">
<meta property="og:description" content="{e(line)}">
<style>
 body{{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;
   background:#0f1115;color:#ece6da;font:16px/1.5 system-ui,-apple-system,"Segoe UI",sans-serif}}
 .card{{background:#f3ecdf;color:#2a241c;border-radius:20px;padding:32px 28px;max-width:360px;
   margin:24px;text-align:center;box-shadow:0 10px 40px #0007}}
 .bism{{font-size:22px;color:#6d5f4a;margin-bottom:12px}}
 h1{{font-size:24px;margin:4px 0 6px;unicode-bidi:plaintext}}
 p{{margin:0 0 20px;color:#6d5f4a}}
 .code{{font:600 22px/1 ui-monospace,Menlo,monospace;letter-spacing:6px;margin:0 0 24px}}
 a.btn{{display:block;padding:14px;border-radius:12px;text-decoration:none;font-weight:600;margin-top:10px}}
 .primary{{background:#d9a441;color:#1b1610}} .ghost{{border:1px solid #c9bba3;color:#2a241c}}
</style></head>
<body><div class="card">
 <div class="bism">بِسْمِ ٱللَّٰهِ</div>
 <h1>{e(title)}</h1>
 <p>{e(line)}</p>
{body}
</div></body></html>"""
