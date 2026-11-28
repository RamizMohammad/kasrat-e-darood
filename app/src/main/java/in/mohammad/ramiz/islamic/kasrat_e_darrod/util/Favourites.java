package in.mohammad.ramiz.islamic.kasrat_e_darrod.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;

/** Favourite recitations (Recite hearts + "Favourites" tab). Kept on this phone only. */
public final class Favourites {

    private static final String PREFS = "kasrat_favourites";
    private static final String KEY = "ids";

    private final SharedPreferences prefs;

    public Favourites(@NonNull Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean has(@Nullable String id) {
        return id != null && prefs.getStringSet(KEY, new HashSet<>()).contains(id);
    }

    /** Flips the heart; returns the new state. */
    public boolean toggle(@NonNull String id) {
        Set<String> ids = new HashSet<>(prefs.getStringSet(KEY, new HashSet<>()));
        boolean on = !ids.remove(id);
        if (on) ids.add(id);
        prefs.edit().putStringSet(KEY, ids).apply();
        return on;
    }

    @NonNull
    public Set<String> all() {
        return new HashSet<>(prefs.getStringSet(KEY, new HashSet<>()));
    }
}
