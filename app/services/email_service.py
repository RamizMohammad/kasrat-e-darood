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
from email.utils import formataddr, formatdate, make_msgid

from loguru import logger

from app.config.settings import settings
from app.services import email_templates


class EmailService:
    @property
    def enabled(self) -> bool:
        return bool(settings.SMTP_USER and settings.SMTP_PASSWORD)

    def _from_addr(self) -> str:
        return settings.SMTP_FROM_EMAIL or settings.SMTP_USER

    def _from_header(self) -> str:
        return formataddr((settings.SMTP_FROM_NAME, self._from_addr()))

    def _apply_deliverability_headers(self, msg: EmailMessage) -> None:
        """Add the standard headers mailbox providers expect.

        Missing Date / Message-ID makes a message look machine-generated and is
        a common reason for landing in spam. Reply-To and List-Unsubscribe
        further improve trust (Gmail in particular rewards List-Unsubscribe).
        """
        from_addr = self._from_addr()
        domain = from_addr.split("@")[-1] if "@" in from_addr else "localhost"
        msg["Date"] = formatdate(localtime=True)
        msg["Message-ID"] = make_msgid(domain=domain)
        reply_to = settings.SUPPORT_EMAIL or from_addr
        msg["Reply-To"] = reply_to
        msg["List-Unsubscribe"] = f"<mailto:{reply_to}?subject=unsubscribe>"
        msg["X-Auto-Response-Suppress"] = "OOF, AutoReply"

        # Deliverability note: when sending through Gmail SMTP, the From address
        # must be the authenticated Gmail account (or a verified "Send mail as"
        # alias) — otherwise DKIM/DMARC won't align and mail is flagged as spam.
        if "gmail.com" in settings.SMTP_HOST and not from_addr.endswith("@gmail.com"):
            logger.warning(
                "From address {} is not a gmail.com address but SMTP host is "
                "Gmail — DMARC alignment may fail and mail may be marked spam.",
                from_addr,
            )

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
        # Plaintext first, then HTML alternative — a text/plain part also helps
        # deliverability.
        msg.set_content(email_templates.welcome_text(full_name, lang, support))
        msg.add_alternative(
            email_templates.welcome_html(full_name, lang, support), subtype="html"
        )
        self._apply_deliverability_headers(msg)
        self._send(msg, to_email)

    def send_password_reset(
        self, *, to_email: str, code: str, minutes: int, lang: str = "en"
    ) -> None:
        """Send a password-reset OTP email in `lang`. No-op if SMTP disabled."""
        if not self.enabled:
            logger.warning(
                "SMTP not configured; skipping password-reset email to {}", to_email
            )
            return
        msg = EmailMessage()
        msg["Subject"] = email_templates.otp_subject(lang)
        msg["From"] = self._from_header()
        msg["To"] = to_email
        msg.set_content(email_templates.otp_text(code, minutes, lang))
        msg.add_alternative(
            email_templates.otp_html(code, minutes, lang), subtype="html"
        )
        self._apply_deliverability_headers(msg)
        self._send(msg, to_email)


email_service = EmailService()
