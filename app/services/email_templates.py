"""Localised HTML + plaintext email templates for Kasrat-e-Darood.

Supports English (en), Hindi (hi) and Urdu (ur). Urdu renders right-to-left.
Email clients require inline CSS, so the template uses inline styles and the
Kasrat-e-Darood palette (emerald + gold).
"""
from __future__ import annotations

_PRIMARY = "#004532"
_GOLD = "#e9c349"
_SURFACE = "#fafaf5"
_INK = "#1a1c19"
_MUTED = "#3f4944"

# Per-language copy. {name} is substituted at render time.
_STRINGS: dict[str, dict[str, str]] = {
    "en": {
        "subject": "You're on the Kasrat-e-Darood closed-beta list",
        "greeting": "Assalamu Alaikum {name},",
        "intro": ("Thank you for requesting early access to Kasrat-e-Darood — a calm, "
                  "shared space to track your recitations, celebrate consistency, and "
                  "grow together as a community."),
        "next_title": "WHAT HAPPENS NEXT",
        "step1": "We add your Google account to the Play Store closed-testing list.",
        "step2": "You'll get a follow-up email with the install link and steps.",
        "step3": "Start logging your Surahs, Darood & Dhikr — and keep the heart steady.",
        "closing": ("Closed beta is limited, so your early feedback genuinely shapes the "
                    "app. JazakAllah khair for being one of the first."),
        "support_prefix": "Questions? Reach us at",
        "signoff": "JazakAllah khair — The Kasrat-e-Darood team",
        "made": "Made with intention for the ummah.",
    },
    "hi": {
        "subject": "आप Kasrat-e-Darood क्लोज़्ड बीटा सूची में हैं",
        "greeting": "अस्सलामु अलैकुम {name},",
        "intro": ("Kasrat-e-Darood में जल्दी एक्सेस का अनुरोध करने के लिए शुक्रिया — एक "
                  "शांत, साझा स्थान जहाँ आप अपनी तिलावत दर्ज करें, निरंतरता का जश्न मनाएँ "
                  "और समुदाय के साथ आगे बढ़ें।"),
        "next_title": "आगे क्या होगा",
        "step1": "हम आपका Google अकाउंट Play Store क्लोज़्ड-टेस्टिंग सूची में जोड़ते हैं।",
        "step2": "आपको इंस्टॉल लिंक और स्टेप्स के साथ एक फ़ॉलो-अप ईमेल मिलेगा।",
        "step3": "अपनी सूरह, दरूद और ज़िक्र दर्ज करना शुरू करें — और दिल को स्थिर रखें।",
        "closing": ("क्लोज़्ड बीटा सीमित है, इसलिए आपका शुरुआती फ़ीडबैक ऐप को सँवारता है। "
                    "सबसे पहले जुड़ने के लिए जज़ाकअल्लाह ख़ैर।"),
        "support_prefix": "सवाल? हमसे यहाँ संपर्क करें:",
        "signoff": "जज़ाकअल्लाह ख़ैर — Kasrat-e-Darood टीम",
        "made": "उम्मत के लिए नीयत के साथ बनाया गया।",
    },
    "ur": {
        "subject": "آپ Kasrat-e-Darood کلوزڈ بیٹا فہرست میں شامل ہیں",
        "greeting": "السلام علیکم {name}،",
        "intro": ("Kasrat-e-Darood میں ابتدائی رسائی کی درخواست کرنے کا شکریہ — ایک "
                  "پُرسکون، مشترکہ جگہ جہاں آپ اپنی تلاوت درج کریں، تسلسل کا جشن منائیں "
                  "اور کمیونٹی کے ساتھ آگے بڑھیں۔"),
        "next_title": "آگے کیا ہوگا",
        "step1": "ہم آپ کا Google اکاؤنٹ Play Store کلوزڈ ٹیسٹنگ فہرست میں شامل کرتے ہیں۔",
        "step2": "آپ کو انسٹال لنک اور مراحل کے ساتھ ایک فالو اپ ای میل ملے گی۔",
        "step3": "اپنی سورتیں، درود اور ذکر درج کرنا شروع کریں — اور دل کو مستحکم رکھیں۔",
        "closing": ("کلوزڈ بیٹا محدود ہے، اس لیے آپ کا ابتدائی فیڈبیک ایپ کو سنوارتا ہے۔ "
                    "سب سے پہلے شامل ہونے پر جزاک اللہ خیر۔"),
        "support_prefix": "سوالات؟ ہم سے یہاں رابطہ کریں:",
        "signoff": "جزاک اللہ خیر — Kasrat-e-Darood ٹیم",
        "made": "امت کے لیے نیت کے ساتھ بنایا گیا۔",
    },
}


def _t(lang: str) -> dict[str, str]:
    return _STRINGS.get(lang, _STRINGS["en"])


def welcome_subject(lang: str = "en") -> str:
    return _t(lang)["subject"]


def welcome_text(full_name: str, lang: str = "en", support_email: str | None = None) -> str:
    """Plaintext fallback in the requested language."""
    s = _t(lang)
    support = (f"\n\n{s['support_prefix']} {support_email}." if support_email else "")
    return (
        f"{s['greeting'].format(name=full_name)}\n\n"
        f"{s['intro']}\n\n"
        f"{s['next_title']}\n"
        f"  1. {s['step1']}\n"
        f"  2. {s['step2']}\n"
        f"  3. {s['step3']}\n\n"
        f"{s['closing']}"
        f"{support}\n\n"
        f"{s['signoff']}"
    )


def welcome_html(full_name: str, lang: str = "en", support_email: str | None = None) -> str:
    """Branded HTML email in the requested language (RTL for Urdu)."""
    s = _t(lang)
    rtl = lang == "ur"
    direction = "rtl" if rtl else "ltr"
    align = "right" if rtl else "left"
    if lang == "ur":
        font = "'Noto Nastaliq Urdu', 'Segoe UI', Arial, sans-serif"
    elif lang == "hi":
        font = "'Noto Sans Devanagari', 'Segoe UI', Arial, sans-serif"
    else:
        font = "Arial, Helvetica, sans-serif"

    support_block = (
        f'<p style="margin:0 0 8px;color:{_MUTED};font-size:13px;">'
        f'{s["support_prefix"]} '
        f'<a href="mailto:{support_email}" style="color:{_PRIMARY};">{support_email}</a></p>'
        if support_email else ""
    )
    return f"""\
<!DOCTYPE html>
<html lang="{lang}" dir="{direction}">
<head><meta charset="utf-8" /><meta name="viewport" content="width=device-width, initial-scale=1.0" /></head>
<body style="margin:0;padding:0;background:{_SURFACE};font-family:{font};color:{_INK};direction:{direction};text-align:{align};">
  <div style="max-width:560px;margin:0 auto;padding:32px 20px;">
    <!-- Logo -->
    <div style="text-align:center;margin-bottom:24px;">
      <div style="display:inline-block;width:56px;height:56px;line-height:56px;border-radius:16px;background:{_PRIMARY};color:{_GOLD};font-size:26px;text-align:center;">&#10022;</div>
      <div style="margin-top:12px;font-size:22px;font-weight:bold;color:{_PRIMARY};">Kasrat-e-Darood</div>
    </div>

    <!-- Card -->
    <div style="background:#ffffff;border-radius:20px;padding:32px;border:1px solid #e3e3de;">
      <p style="margin:0 0 6px;color:{_PRIMARY};font-size:18px;font-weight:bold;">{s['greeting'].format(name=full_name)}</p>
      <p style="margin:0 0 18px;color:{_MUTED};font-size:15px;line-height:1.7;">{s['intro']}</p>

      <div style="background:{_SURFACE};border-radius:14px;padding:18px 20px;margin:0 0 20px;">
        <p style="margin:0 0 12px;font-size:12px;letter-spacing:1px;text-transform:uppercase;color:#6f7973;font-weight:bold;">{s['next_title']}</p>
        <p style="margin:0 0 10px;font-size:14px;color:{_INK};line-height:1.7;"><span style="color:{_GOLD};">&#10022;</span>&nbsp; {s['step1']}</p>
        <p style="margin:0 0 10px;font-size:14px;color:{_INK};line-height:1.7;"><span style="color:{_GOLD};">&#10022;</span>&nbsp; {s['step2']}</p>
        <p style="margin:0;font-size:14px;color:{_INK};line-height:1.7;"><span style="color:{_GOLD};">&#10022;</span>&nbsp; {s['step3']}</p>
      </div>

      <p style="margin:0;color:{_MUTED};font-size:14px;line-height:1.7;">{s['closing']}</p>
    </div>

    <!-- Footer -->
    <div style="text-align:center;margin-top:22px;">
      {support_block}
      <p style="margin:0;color:#6f7973;font-size:12px;">{s['signoff']}</p>
      <p style="margin:6px 0 0;color:#9aa39d;font-size:11px;">{s['made']}</p>
    </div>
  </div>
</body>
</html>"""


# --- Password-reset OTP --------------------------------------------------------
_OTP_STRINGS: dict[str, dict[str, str]] = {
    "en": {
        "subject": "Your Kasrat-e-Darood password reset code",
        "greeting": "Assalamu Alaikum,",
        "intro": "Use the code below to reset your Kasrat-e-Darood password.",
        "expires": "This code expires in {minutes} minutes. If you didn't request "
                   "a reset, you can safely ignore this email.",
        "signoff": "JazakAllah khair — The Kasrat-e-Darood team",
    },
    "hi": {
        "subject": "आपका Kasrat-e-Darood पासवर्ड रीसेट कोड",
        "greeting": "अस्सलामु अलैकुम,",
        "intro": "अपना Kasrat-e-Darood पासवर्ड रीसेट करने के लिए नीचे दिया कोड इस्तेमाल करें।",
        "expires": "यह कोड {minutes} मिनट में समाप्त हो जाएगा। यदि आपने रीसेट का अनुरोध "
                   "नहीं किया, तो इस ईमेल को अनदेखा करें।",
        "signoff": "JazakAllah khair — Kasrat-e-Darood टीम",
    },
    "ur": {
        "subject": "آپ کا Kasrat-e-Darood پاس ورڈ ری سیٹ کوڈ",
        "greeting": "السلام علیکم،",
        "intro": "اپنا Kasrat-e-Darood پاس ورڈ ری سیٹ کرنے کے لیے نیچے دیا گیا کوڈ استعمال کریں۔",
        "expires": "یہ کوڈ {minutes} منٹ میں ختم ہو جائے گا۔ اگر آپ نے ری سیٹ کی درخواست نہیں "
                   "کی تو اس ای میل کو نظر انداز کر دیں۔",
        "signoff": "جزاک اللہ خیر — Kasrat-e-Darood ٹیم",
    },
}


def _ot(lang: str) -> dict[str, str]:
    return _OTP_STRINGS.get(lang, _OTP_STRINGS["en"])


def otp_subject(lang: str = "en") -> str:
    return _ot(lang)["subject"]


def otp_text(code: str, minutes: int, lang: str = "en") -> str:
    s = _ot(lang)
    return (
        f"{s['greeting']}\n\n{s['intro']}\n\n"
        f"    {code}\n\n"
        f"{s['expires'].format(minutes=minutes)}\n\n{s['signoff']}"
    )


def otp_html(code: str, minutes: int, lang: str = "en") -> str:
    s = _ot(lang)
    rtl = lang == "ur"
    direction = "rtl" if rtl else "ltr"
    align = "right" if rtl else "left"
    if lang == "ur":
        font = "'Noto Nastaliq Urdu', 'Segoe UI', Arial, sans-serif"
    elif lang == "hi":
        font = "'Noto Sans Devanagari', 'Segoe UI', Arial, sans-serif"
    else:
        font = "Arial, Helvetica, sans-serif"
    return f"""\
<!DOCTYPE html>
<html lang="{lang}" dir="{direction}">
<head><meta charset="utf-8" /><meta name="viewport" content="width=device-width, initial-scale=1.0" /></head>
<body style="margin:0;padding:0;background:{_SURFACE};font-family:{font};color:{_INK};direction:{direction};text-align:{align};">
  <div style="max-width:520px;margin:0 auto;padding:32px 20px;">
    <div style="text-align:center;margin-bottom:24px;">
      <div style="display:inline-block;width:56px;height:56px;line-height:56px;border-radius:16px;background:{_PRIMARY};color:{_GOLD};font-size:26px;text-align:center;">&#10022;</div>
      <div style="margin-top:12px;font-size:22px;font-weight:bold;color:{_PRIMARY};">Kasrat-e-Darood</div>
    </div>
    <div style="background:#ffffff;border-radius:20px;padding:32px;border:1px solid #e3e3de;">
      <p style="margin:0 0 6px;color:{_PRIMARY};font-size:18px;font-weight:bold;">{s['greeting']}</p>
      <p style="margin:0 0 22px;color:{_MUTED};font-size:15px;line-height:1.7;">{s['intro']}</p>
      <div style="text-align:center;background:{_SURFACE};border-radius:14px;padding:22px;margin:0 0 22px;">
        <div style="font-size:36px;font-weight:bold;letter-spacing:10px;color:{_PRIMARY};font-family:Arial,Helvetica,sans-serif;">{code}</div>
      </div>
      <p style="margin:0;color:{_MUTED};font-size:13px;line-height:1.7;">{s['expires'].format(minutes=minutes)}</p>
    </div>
    <div style="text-align:center;margin-top:22px;">
      <p style="margin:0;color:#6f7973;font-size:12px;">{s['signoff']}</p>
    </div>
  </div>
</body>
</html>"""


# --- Account-deletion OTP ------------------------------------------------------
_DEL_OTP_STRINGS: dict[str, dict[str, str]] = {
    "en": {
        "subject": "Confirm your Kasrat-e-Darood account deletion",
        "greeting": "Assalamu Alaikum,",
        "intro": "Use the code below to confirm deleting your Kasrat-e-Darood "
                 "account. This cannot be undone.",
        "expires": "This code expires in {minutes} minutes. If you didn't request "
                   "this, ignore this email and your account stays safe.",
        "signoff": "The Kasrat-e-Darood team",
    },
    "hi": {
        "subject": "अपने Kasrat-e-Darood अकाउंट को हटाने की पुष्टि करें",
        "greeting": "अस्सलामु अलैकुम,",
        "intro": "अपना Kasrat-e-Darood अकाउंट हटाने की पुष्टि के लिए नीचे दिया कोड "
                 "इस्तेमाल करें। यह वापस नहीं किया जा सकता।",
        "expires": "यह कोड {minutes} मिनट में समाप्त हो जाएगा। यदि आपने अनुरोध नहीं "
                   "किया तो इस ईमेल को अनदेखा करें।",
        "signoff": "Kasrat-e-Darood टीम",
    },
    "ur": {
        "subject": "اپنے Kasrat-e-Darood اکاؤنٹ کے حذف کی تصدیق کریں",
        "greeting": "السلام علیکم،",
        "intro": "اپنا Kasrat-e-Darood اکاؤنٹ حذف کرنے کی تصدیق کے لیے نیچے دیا گیا "
                 "کوڈ استعمال کریں۔ یہ واپس نہیں ہو سکتا۔",
        "expires": "یہ کوڈ {minutes} منٹ میں ختم ہو جائے گا۔ اگر آپ نے درخواست نہیں "
                   "کی تو اس ای میل کو نظر انداز کر دیں۔",
        "signoff": "Kasrat-e-Darood ٹیم",
    },
}

_DEL_DONE_STRINGS: dict[str, dict[str, str]] = {
    "en": {
        "subject": "Your Kasrat-e-Darood account has been deleted",
        "greeting": "Assalamu Alaikum,",
        "body": "Your Kasrat-e-Darood account and personal data have been deleted. "
                "We're sorry to see you go — you're always welcome back, insha'Allah.",
        "signoff": "The Kasrat-e-Darood team",
    },
    "hi": {
        "subject": "आपका Kasrat-e-Darood अकाउंट हटा दिया गया है",
        "greeting": "अस्सलामु अलैकुम,",
        "body": "आपका Kasrat-e-Darood अकाउंट और निजी डेटा हटा दिया गया है। "
                "आपका जाना हमें अच्छा नहीं लगा — आप कभी भी वापस आ सकते हैं, इंशाअल्लाह।",
        "signoff": "Kasrat-e-Darood टीम",
    },
    "ur": {
        "subject": "آپ کا Kasrat-e-Darood اکاؤنٹ حذف کر دیا گیا ہے",
        "greeting": "السلام علیکم،",
        "body": "آپ کا Kasrat-e-Darood اکاؤنٹ اور ذاتی ڈیٹا حذف کر دیا گیا ہے۔ "
                "آپ کا جانا ہمیں اچھا نہیں لگا — آپ کبھی بھی واپس آ سکتے ہیں، ان شاء اللہ۔",
        "signoff": "Kasrat-e-Darood ٹیم",
    },
}


def _font_for(lang: str) -> tuple[str, str, str]:
    rtl = lang == "ur"
    if lang == "ur":
        font = "'Noto Nastaliq Urdu', 'Segoe UI', Arial, sans-serif"
    elif lang == "hi":
        font = "'Noto Sans Devanagari', 'Segoe UI', Arial, sans-serif"
    else:
        font = "Arial, Helvetica, sans-serif"
    return font, ("rtl" if rtl else "ltr"), ("right" if rtl else "left")


def deletion_otp_subject(lang: str = "en") -> str:
    return _DEL_OTP_STRINGS.get(lang, _DEL_OTP_STRINGS["en"])["subject"]


def deletion_otp_text(code: str, minutes: int, lang: str = "en") -> str:
    s = _DEL_OTP_STRINGS.get(lang, _DEL_OTP_STRINGS["en"])
    return (f"{s['greeting']}\n\n{s['intro']}\n\n    {code}\n\n"
            f"{s['expires'].format(minutes=minutes)}\n\n{s['signoff']}")


def deletion_otp_html(code: str, minutes: int, lang: str = "en") -> str:
    s = _DEL_OTP_STRINGS.get(lang, _DEL_OTP_STRINGS["en"])
    font, direction, align = _font_for(lang)
    return f"""\
<!DOCTYPE html>
<html lang="{lang}" dir="{direction}">
<head><meta charset="utf-8" /><meta name="viewport" content="width=device-width, initial-scale=1.0" /></head>
<body style="margin:0;padding:0;background:{_SURFACE};font-family:{font};color:{_INK};direction:{direction};text-align:{align};">
  <div style="max-width:520px;margin:0 auto;padding:32px 20px;">
    <div style="text-align:center;margin-bottom:24px;">
      <div style="display:inline-block;width:56px;height:56px;line-height:56px;border-radius:16px;background:{_PRIMARY};color:{_GOLD};font-size:26px;text-align:center;">&#10022;</div>
      <div style="margin-top:12px;font-size:22px;font-weight:bold;color:{_PRIMARY};">Kasrat-e-Darood</div>
    </div>
    <div style="background:#ffffff;border-radius:20px;padding:32px;border:1px solid #e3e3de;">
      <p style="margin:0 0 6px;color:{_PRIMARY};font-size:18px;font-weight:bold;">{s['greeting']}</p>
      <p style="margin:0 0 22px;color:{_MUTED};font-size:15px;line-height:1.7;">{s['intro']}</p>
      <div style="text-align:center;background:{_SURFACE};border-radius:14px;padding:22px;margin:0 0 22px;">
        <div style="font-size:36px;font-weight:bold;letter-spacing:10px;color:{_PRIMARY};font-family:Arial,Helvetica,sans-serif;">{code}</div>
      </div>
      <p style="margin:0;color:{_MUTED};font-size:13px;line-height:1.7;">{s['expires'].format(minutes=minutes)}</p>
    </div>
    <div style="text-align:center;margin-top:22px;">
      <p style="margin:0;color:#6f7973;font-size:12px;">{s['signoff']}</p>
    </div>
  </div>
</body>
</html>"""


def deletion_done_subject(lang: str = "en") -> str:
    return _DEL_DONE_STRINGS.get(lang, _DEL_DONE_STRINGS["en"])["subject"]


def deletion_done_text(lang: str = "en") -> str:
    s = _DEL_DONE_STRINGS.get(lang, _DEL_DONE_STRINGS["en"])
    return f"{s['greeting']}\n\n{s['body']}\n\n{s['signoff']}"


def deletion_done_html(lang: str = "en") -> str:
    s = _DEL_DONE_STRINGS.get(lang, _DEL_DONE_STRINGS["en"])
    font, direction, align = _font_for(lang)
    return f"""\
<!DOCTYPE html>
<html lang="{lang}" dir="{direction}">
<head><meta charset="utf-8" /><meta name="viewport" content="width=device-width, initial-scale=1.0" /></head>
<body style="margin:0;padding:0;background:{_SURFACE};font-family:{font};color:{_INK};direction:{direction};text-align:{align};">
  <div style="max-width:520px;margin:0 auto;padding:32px 20px;">
    <div style="text-align:center;margin-bottom:24px;">
      <div style="display:inline-block;width:56px;height:56px;line-height:56px;border-radius:16px;background:{_PRIMARY};color:{_GOLD};font-size:26px;text-align:center;">&#10022;</div>
      <div style="margin-top:12px;font-size:22px;font-weight:bold;color:{_PRIMARY};">Kasrat-e-Darood</div>
    </div>
    <div style="background:#ffffff;border-radius:20px;padding:32px;border:1px solid #e3e3de;">
      <p style="margin:0 0 6px;color:{_PRIMARY};font-size:18px;font-weight:bold;">{s['greeting']}</p>
      <p style="margin:0;color:{_MUTED};font-size:15px;line-height:1.7;">{s['body']}</p>
    </div>
    <div style="text-align:center;margin-top:22px;">
      <p style="margin:0;color:#6f7973;font-size:12px;">{s['signoff']}</p>
    </div>
  </div>
</body>
</html>"""
