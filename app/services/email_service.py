"""SMTP email sending (Gmail) for transactional messages.

Uses the standard-library ``smtplib`` so there is no extra dependency. Sends are
intended to run in a background task (FastAPI ``BackgroundTasks``), which keeps
the HTTP response fast and runs this synchronous code in a worker thread.

If SMTP credentials are not configured, sending is skipped gracefully so the
rest of the app keeps working in development.
"""
from __future__ import annotations

import smtplib
import ssl
from email.message import EmailMessage

from loguru import logger

from app.config.settings import settings
from app.services import email_templates


class EmailService:
    @property
    def enabled(self) -> bool:
        return bool(settings.SMTP_USER and settings.SMTP_PASSWORD)

    def _from_header(self) -> str:
        addr = settings.SMTP_FROM_EMAIL or settings.SMTP_USER
        return f"{settings.SMTP_FROM_NAME} <{addr}>"

    def _send(self, msg: EmailMessage, to_email: str) -> None:
        """Low-level send via STARTTLS (587) or SSL (465)."""
        context = ssl.create_default_context()
        try:
            if settings.SMTP_USE_SSL:
                with smtplib.SMTP_SSL(settings.SMTP_HOST, settings.SMTP_PORT,
                                      context=context, timeout=20) as server:
                    server.login(settings.SMTP_USER, settings.SMTP_PASSWORD)
                    server.send_message(msg)
            else:
                with smtplib.SMTP(settings.SMTP_HOST, settings.SMTP_PORT, timeout=20) as server:
                    server.ehlo()
                    server.starttls(context=context)
                    server.login(settings.SMTP_USER, settings.SMTP_PASSWORD)
                    server.send_message(msg)
            logger.info("Email sent to {}", to_email)
        except Exception as exc:  # never crash the request because of email
            logger.error("Failed to send email to {}: {}", to_email, exc)

    def send_beta_welcome(self, *, to_email: str, full_name: str, lang: str = "en") -> None:
        """Send the closed-beta welcome email in `lang`. No-op if SMTP disabled."""
        if not self.enabled:
            logger.warning(
                "SMTP not configured; skipping welcome email to {}", to_email
            )
            return

        support = settings.SUPPORT_EMAIL or None
        msg = EmailMessage()
        msg["Subject"] = email_templates.welcome_subject(lang)
        msg["From"] = self._from_header()
        msg["To"] = to_email
        msg.set_content(email_templates.welcome_text(full_name, lang, support))
        msg.add_alternative(
            email_templates.welcome_html(full_name, lang, support), subtype="html"
        )
        self._send(msg, to_email)


email_service = EmailService()
