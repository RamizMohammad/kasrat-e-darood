package in.mohammad.ramiz.islamic.kasrat_e_darrod.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/** App-wide UI settings: language + dark mode, persisted in SharedPreferences. */
public final class AppPrefs {
    private static final String PREFS = "noor_settings";
    private static final String KEY_LANG = "lang";
    private static final String KEY_NIGHT = "night_mode";
    private static final String KEY_LANG_CHOSEN = "lang_chosen";
    private static final String KEY_ONBOARDED = "onboarding_done";

    private final SharedPreferences prefs;

    public AppPrefs(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // --- Language (en | hi | ur) ---
    public String lang() { return prefs.getString(KEY_LANG, "en"); }

    public void setLang(String tag) {
        prefs.edit().putString(KEY_LANG, tag).apply();
        applyLanguage(tag);
    }

    /** Apply a language tag as the app's per-app locale (persisted by AppCompat). */
    public static void applyLanguage(String tag) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
    }

    // --- First run ---
    /** True once the user picked a language on the first-run screen (or upgraded from v2). */
    public boolean languageChosen() { return prefs.getBoolean(KEY_LANG_CHOSEN, false); }
    public void markLanguageChosen() { prefs.edit().putBoolean(KEY_LANG_CHOSEN, true).apply(); }

    /** True once onboarding was finished or skipped on this device. */
    public boolean onboardingDone() { return prefs.getBoolean(KEY_ONBOARDED, false); }
    public void markOnboardingDone() { prefs.edit().putBoolean(KEY_ONBOARDED, true).apply(); }

    // --- Dark mode (legacy v2 setting; v3 is dark-only) ---
    // ── Prayer times (also saved on the server; this copy works offline) ──
    private static final String KEY_PRAYER_METHOD = "prayer_method";
    private static final String KEY_PRAYER_SCHOOL = "prayer_school";

    /** AlAdhan calculation method id (1 = Karachi, the default). */
    public int prayerMethod() { return prefs.getInt(KEY_PRAYER_METHOD, 1); }

    /** 0 = Shafi, 1 = Hanafi (affects Asr). */
    public int prayerSchool() { return prefs.getInt(KEY_PRAYER_SCHOOL, 1); }

    public void setPrayer(int method, int school) {
        prefs.edit().putInt(KEY_PRAYER_METHOD, method).putInt(KEY_PRAYER_SCHOOL, school).apply();
    }

    // ── Theme (Phase D): 0 = Light, 1 = Dark, 2 = follow the phone ──────────
    public static final int THEME_LIGHT = 0, THEME_DARK = 1, THEME_SYSTEM = 2;
    private static final String KEY_THEME = "theme_mode";

    public int theme() { return prefs.getInt(KEY_THEME, THEME_LIGHT); }

    public void setTheme(int mode) {
        prefs.edit().putInt(KEY_THEME, mode).apply();
        applyTheme(mode);
    }

    /** Sets AppCompat's night mode; open screens recreate themselves in the new theme. */
    public static void applyTheme(int mode) {
        AppCompatDelegate.setDefaultNightMode(mode == THEME_DARK ? AppCompatDelegate.MODE_NIGHT_YES
                : mode == THEME_SYSTEM ? AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                : AppCompatDelegate.MODE_NIGHT_NO);
    }

    /** True when the screen is showing the dark theme right now. */
    public static boolean isDark(@androidx.annotation.NonNull android.content.Context ctx) {
        int ui = ctx.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return ui == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    // ── Daily intention (Home ring) ───────────────────────────────────────
    private static final String KEY_INTENTION = "daily_intention";

    /** Darood a day the user aims for; only on this phone. */
    public int dailyIntention() { return prefs.getInt(KEY_INTENTION, 1000); }

    public void setDailyIntention(int n) { prefs.edit().putInt(KEY_INTENTION, n).apply(); }

    // ── Phase 7c · Settings → Recitation / Reminders (this phone only) ────
    private static final String KEY_TASBIH_TARGET = "tasbih_target";
    private static final String KEY_SOUND = "sound";
    private static final String KEY_HAPTICS = "haptics";
    private static final String KEY_KEEP_ON = "keep_screen_on";
    private static final String KEY_FRIDAY = "friday_reminder";
    private static final String KEY_NUDGES = "prayer_nudges";
    private static final String KEY_NOTIF_SEEN = "notifications_seen";

    /** The lap a new tasbih starts with (each recitation then remembers its own). */
    public int tasbihTarget() { return prefs.getInt(KEY_TASBIH_TARGET, 33); }
    public void setTasbihTarget(int n) { prefs.edit().putInt(KEY_TASBIH_TARGET, n).apply(); }

    /** A soft click on each tasbih count. */
    public boolean sound() { return prefs.getBoolean(KEY_SOUND, true); }
    public void setSound(boolean on) { prefs.edit().putBoolean(KEY_SOUND, on).apply(); }

    /** Vibration on taps, logs and errors. */
    public boolean haptics() { return prefs.getBoolean(KEY_HAPTICS, true); }
    public void setHaptics(boolean on) { prefs.edit().putBoolean(KEY_HAPTICS, on).apply(); }

    /** Keep the screen awake on the tasbih counter. */
    public boolean keepScreenOn() { return prefs.getBoolean(KEY_KEEP_ON, true); }
    public void setKeepScreenOn(boolean on) { prefs.edit().putBoolean(KEY_KEEP_ON, on).apply(); }

    public boolean fridayReminder() { return prefs.getBoolean(KEY_FRIDAY, true); }
    public void setFridayReminder(boolean on) { prefs.edit().putBoolean(KEY_FRIDAY, on).apply(); }

    public boolean prayerNudges() { return prefs.getBoolean(KEY_NUDGES, false); }
    public void setPrayerNudges(boolean on) { prefs.edit().putBoolean(KEY_NUDGES, on).apply(); }

    /** created_at of the newest notification you've seen; newer ones show the unread dot. */
    @androidx.annotation.Nullable
    public String notificationsSeen() { return prefs.getString(KEY_NOTIF_SEEN, null); }
    public void setNotificationsSeen(@androidx.annotation.Nullable String iso) {
        prefs.edit().putString(KEY_NOTIF_SEEN, iso).apply();
    }
}
