package in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists the app JWT access/refresh tokens and basic profile in SharedPreferences. */
public class TokenStore {
    private static final String PREFS = "noor_auth";
    private static final String KEY_ACCESS = "access_token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_PHOTO = "user_photo";

    private final SharedPreferences prefs;

    public TokenStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void save(String access, String refresh) {
        prefs.edit().putString(KEY_ACCESS, access).putString(KEY_REFRESH, refresh).apply();
    }

    /** Persist a full authenticated session returned by /auth/login or /register. */
    public void saveSession(String access, String refresh,
                            String userId, String userName, String userEmail) {
        prefs.edit()
                .putString(KEY_ACCESS, access)
                .putString(KEY_REFRESH, refresh)
                .putString(KEY_USER_ID, userId)
                .putString(KEY_USER_NAME, userName)
                .putString(KEY_USER_EMAIL, userEmail)
                .apply();
    }

    public void setPhotoUrl(String url) {
        prefs.edit().putString(KEY_USER_PHOTO, url).apply();
    }

    public String accessToken() { return prefs.getString(KEY_ACCESS, null); }
    public String refreshToken() { return prefs.getString(KEY_REFRESH, null); }
    public String userId() { return prefs.getString(KEY_USER_ID, null); }
    public String userName() { return prefs.getString(KEY_USER_NAME, null); }
    public String userEmail() { return prefs.getString(KEY_USER_EMAIL, null); }
    public String photoUrl() { return prefs.getString(KEY_USER_PHOTO, null); }
    public boolean isLoggedIn() { return accessToken() != null; }
    public void clear() { prefs.edit().clear().apply(); }
}
