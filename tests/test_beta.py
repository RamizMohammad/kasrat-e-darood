"""Tests for the public beta-signup flow (dedupe + honeypot)."""
from __future__ import annotations

import pytest

from app.models.beta import BetaSignup
from app.schemas.beta import BetaSignupCreate
from app.services.beta_service import beta_service

pytestmark = pytest.mark.asyncio


async def test_signup_persists_and_is_idempotent(init_db):
    s1, already1 = await beta_service.signup(
        BetaSignupCreate(full_name="Ramiz", email="Ramiz@Example.com")
    )
    assert already1 is False
    assert s1.email == "ramiz@example.com"  # normalized

    s2, already2 = await beta_service.signup(
        BetaSignupCreate(full_name="Ramiz Again", email="ramiz@example.com")
    )
    assert already2 is True
    assert s2.id == s1.id
    assert await BetaSignup.find(BetaSignup.deleted == False).count() == 1  # noqa: E712


async def test_honeypot_is_not_persisted(init_db):
    decoy, already = await beta_service.signup(
        BetaSignupCreate(full_name="Bot", email="bot@evil.com", website="http://spam")
    )
    assert already is False
    assert decoy.id is not None  # response is satisfied
    # …but nothing was stored.
    assert await BetaSignup.find(BetaSignup.deleted == False).count() == 0  # noqa: E712


async def test_stats_counts_only_real_signups(init_db):
    await beta_service.signup(BetaSignupCreate(full_name="Ali", email="a@x.com"))
    await beta_service.signup(
        BetaSignupCreate(full_name="Bott", email="b@x.com", website="x")
    )
    stats = await beta_service.stats()
    assert stats.total == 1
    assert stats.closed == 1


async def test_welcome_email_scheduled_only_for_new_non_bot(init_db):
    from fastapi import BackgroundTasks
    from app.api.v1.beta.router import signup as signup_route

    # New signup -> one background email task scheduled.
    bg = BackgroundTasks()
    res = await signup_route(BetaSignupCreate(full_name="Ramiz", email="r@x.com"), bg)
    assert res.already_registered is False
    assert len(bg.tasks) == 1

    # Duplicate -> no email.
    bg2 = BackgroundTasks()
    res2 = await signup_route(BetaSignupCreate(full_name="Ramiz", email="r@x.com"), bg2)
    assert res2.already_registered is True
    assert len(bg2.tasks) == 0

    # Honeypot (bot) -> no email.
    bg3 = BackgroundTasks()
    await signup_route(BetaSignupCreate(full_name="Bott", email="b@x.com", website="x"), bg3)
    assert len(bg3.tasks) == 0


def test_email_service_safe_without_smtp_config(monkeypatch):
    from app.services.email_service import email_service
    from app.config.settings import settings
    # Force SMTP off so the test is deterministic and never sends a real email.
    monkeypatch.setattr(settings, "SMTP_USER", "")
    monkeypatch.setattr(settings, "SMTP_PASSWORD", "")
    assert email_service.enabled is False
    email_service.send_beta_welcome(to_email="x@y.com", full_name="Test")  # safe no-op


def test_welcome_template_contains_name_and_brand():
    from app.services import email_templates
    html = email_templates.welcome_html("Ramiz", "en", "help@example.com")
    text = email_templates.welcome_text("Ramiz", "en")
    assert "Ramiz" in html and "Kasrat-e-Darood" in html
    assert "Ramiz" in text and "JazakAllah" in text


def test_welcome_template_localised():
    from app.services import email_templates
    # Hindi
    hi = email_templates.welcome_html("Ramiz", "hi")
    assert 'lang="hi"' in hi and "अस्सलामु" in hi
    # Urdu is right-to-left
    ur = email_templates.welcome_html("Ramiz", "ur")
    assert 'dir="rtl"' in ur and "السلام" in ur
    # Subject is localised; unknown language falls back to English
    assert email_templates.welcome_subject("ur") != email_templates.welcome_subject("en")
    assert email_templates.welcome_subject("zz") == email_templates.welcome_subject("en")
