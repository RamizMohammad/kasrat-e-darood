"""Application logging (Loguru).

One format for everything: our own logs, uvicorn, SQLAlchemy, Alembic and
the Google/Firebase libraries are all routed through Loguru, so a line from
any of them looks the same and carries the request id.

Pretty (default) — coloured, aligned, one line per event:

    2026-11-28 01:10:04.512 │ INFO    │ 3f2a9c1e │ auth_service:login_with_password:102 │ Signed in

JSON (``LOG_FORMAT=json``) — one compact object per line, for log shippers.

Settings (env): LOG_LEVEL, LOG_FORMAT=pretty|json, LOG_COLOR=auto|always|never
(``NO_COLOR`` is honoured), LOG_SQL=true to see SQL statements.
"""
from __future__ import annotations

import json
import logging
import os
import sys
from datetime import timezone

from loguru import logger

from app.config.settings import settings

_configured = False

# Third-party loggers and the level they're allowed to speak at.
_LIBRARY_LEVELS = {
    "uvicorn": "INFO",
    "uvicorn.error": "INFO",
    "uvicorn.access": "WARNING",     # our middleware writes the access log
    "fastapi": "INFO",
    "alembic": "INFO",
    "sqlalchemy": "WARNING",
    "sqlalchemy.engine": "WARNING",
    "asyncio": "WARNING",
    "httpx": "WARNING",
    "httpcore": "WARNING",
    "urllib3": "WARNING",
    "google": "WARNING",
    "google.auth": "WARNING",
    "firebase_admin": "WARNING",
    "cachecontrol": "WARNING",
}

# Level colours (Loguru markup). Names padded to one width so columns line up.
_LEVEL_STYLE = {
    "TRACE": "<dim>",
    "DEBUG": "<blue>",
    "INFO": "<green>",
    "SUCCESS": "<green><bold>",
    "WARNING": "<yellow><bold>",
    "ERROR": "<red><bold>",
    "CRITICAL": "<white><bold><RED>",
}


# ── Formats ────────────────────────────────────────────────────────────────

def _short_name(name: str | None) -> str:
    """'app.services.auth_service' → 'auth_service'; libraries keep their name."""
    if not name:
        return "?"
    return name.rsplit(".", 1)[-1] if name.startswith("app.") else name


def _pretty_format(record) -> str:
    style = _LEVEL_STYLE.get(record["level"].name, "")
    close = "".join(f"</{t[1:]}" for t in reversed(style.split(">")[:-1])) if style else ""
    record["extra"].setdefault("request_id", "-")
    record["extra"]["_where"] = f'{_short_name(record["name"])}:{record["function"]}:{record["line"]}'
    line = (
        "<dim>{time:YYYY-MM-DD HH:mm:ss.SSS}</dim> <dim>│</dim> "
        f"{style}{{level: <8}}{close} <dim>│</dim> "
        "<magenta>{extra[request_id]: <8.8}</magenta> <dim>│</dim> "
        "<cyan>{extra[_where]}</cyan> <dim>│</dim> "
        f"{style if record['level'].no >= 30 else ''}{{message}}{close if record['level'].no >= 30 else ''}"
    )
    # Anything bound with logger.bind(...) other than our own keys goes at the end.
    extras = {k: v for k, v in record["extra"].items()
              if k not in ("request_id", "_where", "_json")}
    if extras:
        record["extra"]["_kv"] = "  " + " ".join(f"{k}={v}" for k, v in extras.items())
        line += "<dim>{extra[_kv]}</dim>"
    line += "\n"
    if record["exception"]:
        line += "{exception}\n"
    return line


def _json_format(record) -> str:
    payload = {
        "ts": record["time"].astimezone(timezone.utc).isoformat(timespec="milliseconds"),
        "level": record["level"].name,
        "logger": record["name"],
        "where": f'{record["function"]}:{record["line"]}',
        "request_id": record["extra"].get("request_id"),
        "msg": record["message"],
    }
    extras = {k: v for k, v in record["extra"].items() if k not in ("request_id", "_where", "_json", "_kv")}
    if extras:
        payload["extra"] = extras
    if record["exception"]:
        exc = record["exception"]
        payload["exception"] = {
            "type": exc.type.__name__ if exc.type else None,
            "value": str(exc.value) if exc.value else None,
        }
    record["extra"]["_json"] = json.dumps(payload, default=str, ensure_ascii=False)
    # The traceback still follows the JSON line, so it isn't lost.
    return "{extra[_json]}\n" + ("{exception}\n" if record["exception"] else "")


# ── stdlib logging → Loguru ────────────────────────────────────────────────

class InterceptHandler(logging.Handler):
    """Send records from the standard ``logging`` module into Loguru."""

    def emit(self, record: logging.LogRecord) -> None:
        try:
            level: str | int = logger.level(record.levelname).name
        except ValueError:
            level = record.levelno
        # Find the caller outside the logging module so file:line is right.
        frame, depth = logging.currentframe(), 2
        while frame is not None and frame.f_code.co_filename == logging.__file__:
            frame = frame.f_back
            depth += 1
        logger.opt(depth=depth, exception=record.exc_info).patch(
            lambda r: r.update(name=record.name)
        ).log(level, record.getMessage())


def _route_stdlib(level: str) -> None:
    logging.basicConfig(handlers=[InterceptHandler()], level=0, force=True)
    for name, lib_level in _LIBRARY_LEVELS.items():
        lg = logging.getLogger(name)
        lg.handlers = [InterceptHandler()]
        lg.propagate = False
        lg.setLevel(lib_level)
    if _flag("LOG_SQL"):
        logging.getLogger("sqlalchemy.engine").setLevel("INFO")
    # Everything else (unknown libraries) at our level.
    logging.getLogger().setLevel(level)


# ── Setup ──────────────────────────────────────────────────────────────────

def _flag(name: str) -> bool:
    return os.getenv(name, "").strip().lower() in ("1", "true", "yes", "on")


def _use_colour() -> bool:
    mode = os.getenv("LOG_COLOR", "always").strip().lower()
    if os.getenv("NO_COLOR") or mode in ("never", "false", "0", "off"):
        return False
    if mode == "auto":
        return sys.stdout.isatty()
    # "always": `docker logs` and `docker compose logs` show ANSI colours even
    # though the container has no TTY.
    return True


def configure_logging(force: bool = False) -> None:
    """Set up the sinks once (safe to call again; ``force`` re-applies)."""
    global _configured
    if _configured and not force:
        return
    level = os.getenv("LOG_LEVEL", "").strip().upper() or ("DEBUG" if settings.DEBUG else "INFO")
    fmt = os.getenv("LOG_FORMAT", "pretty").strip().lower()

    logger.remove()
    logger.configure(extra={"request_id": "-"})
    logger.add(
        sys.stdout,
        level=level,
        format=_json_format if fmt == "json" else _pretty_format,
        colorize=False if fmt == "json" else _use_colour(),
        # Tracebacks: full chain always; local variable values only in DEBUG
        # (they can hold passwords and tokens).
        backtrace=True,
        diagnose=settings.DEBUG,
        enqueue=True,       # non-blocking, safe across threads (FCM, Alembic)
        catch=True,
    )
    _route_stdlib(level)
    _configured = True
    logger.info("Logging ready · env={} · level={} · format={}", settings.APP_ENV, level, fmt)


def access_log(method: str, path: str, status: int, ms: float, ip: str) -> None:
    """One line per request; the level follows the status code."""
    if path in ("/health", "/health/live"):
        level = "DEBUG"                       # container health checks: quiet
    elif status >= 500:
        level = "ERROR"
    elif status >= 400:
        level = "WARNING"
    else:
        level = "INFO"
    speed = "slow " if ms >= 1000 else ""
    logger.opt(depth=1).log(level, "{} {} → {} · {}{:.0f} ms · {}",
                            method, path, status, speed, ms, ip)
