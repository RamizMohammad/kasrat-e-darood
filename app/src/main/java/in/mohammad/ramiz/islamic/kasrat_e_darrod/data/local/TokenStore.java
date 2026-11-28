package in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.security.SecureBox;

/**
 * The signed-in session: app JWT access/refresh tokens and a basic profile.
 *
 * Tokens and the email are encrypted with the Keystore key (SecureBox), so the
 * prefs file is useless if copied off the phone (rooted dump, adb backup).
 * Values saved in plain text by older versions are read once and re-saved
 * encrypted. Decrypted tokens are kept in memory so each request doesn't
 * decrypt again.
 */
public class TokenStore {
    private static final String PREFS = "noor_auth";
    private static final String KEY_ACCESS = "access_token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_PHOTO = "user_photo";
    private static final String KEY_USER_AVATAR = "user_avatar";

    // Process-wide memo of the decrypted tokens ("" = known to be absent).
    @Nullable private static volatile String accessMemo, refreshMemo;

    private final Context app;
    private final SharedPreferences prefs;

    public TokenStore(Context context) {
        app = context.getApplicationContext();
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void save(String access, String refresh) {
        prefs.edit().putString(KEY_ACCESS, seal(access)).putString(KEY_REFRESH, seal(refresh)).commit();
        accessMemo = nn(access);
        refreshMemo = nn(refresh);
    }

    /** Persist a full authenticated session returned by /auth/login or /register. */
    public void saveSession(String access, String refresh,
                            String userId, String userName, String userEmail) {
        String previous = userId();
        if (previous != null && !previous.equals(userId)) wipeUserData();   // a different account
        prefs.edit()
                .putString(KEY_ACCESS, seal(access))
                .putString(KEY_REFRESH, seal(refresh))
                .putString(KEY_USER_ID, userId)
                .putString(KEY_USER_NAME, userName)
                .putString(KEY_USER_EMAIL, seal(userEmail))
                .commit();
        accessMemo = nn(access);
        refreshMemo = nn(refresh);
    }

    public void setPhotoUrl(String url) {
        prefs.edit().putString(KEY_USER_PHOTO, url).apply();
    }

    @Nullable
    public String accessToken() {
        String m = accessMemo;
        if (m == null) accessMemo = m = nn(open(KEY_ACCESS));
        return m.isEmpty() ? null : m;
    }

    @Nullable
    public String refreshToken() {
        String m = refreshMemo;
        if (m == null) refreshMemo = m = nn(open(KEY_REFRESH));
        return m.isEmpty() ? null : m;
    }

    public String userId() { return prefs.getString(KEY_USER_ID, null); }
    public String userName() { return prefs.getString(KEY_USER_NAME, null); }
    @Nullable public String userEmail() { return open(KEY_USER_EMAIL); }
    public String photoUrl() { return prefs.getString(KEY_USER_PHOTO, null); }

    /** Illustrated avatar spec ("av1:…") or null. */
    public String avatar() { return prefs.getString(KEY_USER_AVATAR, null); }
    public void setAvatar(String spec) { prefs.edit().putString(KEY_USER_AVATAR, spec).apply(); }
    public boolean isLoggedIn() { return accessToken() != null; }

    /** Sign-out: forget the session and everything saved for this user. */
    public void clear() {
        prefs.edit().clear().commit();
        accessMemo = "";
        refreshMemo = "";
        wipeUserData();
    }

    /** In-memory totals and cart, the API cache and the screens' saved copies. */
    private void wipeUserData() {
        in.mohammad.ramiz.islamic.kasrat_e_darrod.data.Totals.reset();
        in.mohammad.ramiz.islamic.kasrat_e_darrod.data.Cart.get().clear();
        in.mohammad.ramiz.islamic.kasrat_e_darrod.data.cache.ApiCache.clearAll(app);
        for (String legacy : LEGACY_CACHES) app.deleteSharedPreferences(legacy);
    }

    /** Plain-text caches written by older versions (now in the encrypted ApiCache). */
    public static final String[] LEGACY_CACHES = {"kasrat_groups", "kasrat_ranks"};

    // ── Encryption ──────────────────────────────────────────────────────────

    @Nullable
    private static String seal(@Nullable String plain) {
        if (plain == null) return null;
        String box = SecureBox.encryptString(plain);
        // Keystore unavailable (rare, broken devices): keep the app usable.
        return box != null ? box : plain;
    }

    /** Decrypts a stored value; upgrades an old plain-text value in place. */
    @Nullable
    private String open(String key) {
        String stored = prefs.getString(key, null);
        if (stored == null) return null;
        if (SecureBox.isSealed(stored)) {
            String plain = SecureBox.decryptString(stored);
            if (plain == null) prefs.edit().remove(key).apply();   // key lost (e.g. restored on a new phone)
            return plain;
        }
        String box = SecureBox.encryptString(stored);
        if (box != null) prefs.edit().putString(key, box).apply();
        return stored;
    }

    private static String nn(@Nullable String s) { return s == null ? "" : s; }
}
