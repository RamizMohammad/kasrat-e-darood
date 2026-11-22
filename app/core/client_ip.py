"""Work out the real client IP behind the nginx reverse proxy.

nginx sets ``X-Real-IP`` to ``$remote_addr`` (it overwrites anything the
client sent), so the header is trustworthy *only* when the request reached
the API from nginx itself, i.e. from a private / Docker network address.
A request that hits the published port directly from the internet keeps
its own socket address, so it cannot spoof another user's IP.
"""
from __future__ import annotations

import ipaddress

from starlette.requests import Request

from app.config.settings import settings


def _trusted_networks() -> list[ipaddress._BaseNetwork]:
    nets = []
    for raw in settings.TRUSTED_PROXY_CIDRS.split(","):
        raw = raw.strip()
        if raw:
            nets.append(ipaddress.ip_network(raw, strict=False))
    return nets


_TRUSTED = _trusted_networks()


def _is_trusted(host: str) -> bool:
    try:
        addr = ipaddress.ip_address(host)
    except ValueError:
        return False
    return any(addr in net for net in _TRUSTED)


def client_ip(request: Request) -> str:
    """Return the best-known client IP for rate limiting and logging."""
    peer = request.client.host if request.client else ""
    if peer and _is_trusted(peer):
        real = request.headers.get("x-real-ip", "").strip()
        if real:
            try:
                ipaddress.ip_address(real)
                return real
            except ValueError:
                pass
    return peer or "unknown"
