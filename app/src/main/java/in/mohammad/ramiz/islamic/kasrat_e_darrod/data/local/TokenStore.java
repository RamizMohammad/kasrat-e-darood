package in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists the app JWT access/refresh tokens in SharedPreferences. */
public class TokenStore {
    private static final String PREFS = "noor_auth";
    private static final String KEY_ACCESS = "access_token";
    private static final String KEY_REFRESH = "refresh_token";

    private final SharedPreferences prefs;

    public TokenStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void save(String access, String refresh) {
        prefs.edit().putString(KEY_ACCESS, access).putString(KEY_REFRESH, refresh).apply();
    }

    public String accessToken() { return prefs.getString(KEY_ACCESS, null); }
    public String refreshToken() { return prefs.getString(KEY_REFRESH, null); }
    public boolean isLoggedIn() { return accessToken() != null; }
    public void clear() { prefs.edit().clear().apply(); }
}
