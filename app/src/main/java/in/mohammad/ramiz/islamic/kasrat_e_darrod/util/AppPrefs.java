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

    // --- Dark mode ---
    public boolean darkMode() { return prefs.getBoolean(KEY_NIGHT, false); }

    public void setDarkMode(boolean on) {
        prefs.edit().putBoolean(KEY_NIGHT, on).apply();
        applyDarkMode(on);
    }

    public static void applyDarkMode(boolean on) {
        AppCompatDelegate.setDefaultNightMode(on
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
    }
}
