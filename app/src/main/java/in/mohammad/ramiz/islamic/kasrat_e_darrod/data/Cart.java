package in.mohammad.ramiz.islamic.kasrat_e_darrod.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The recitation cart: recitation id → chosen count. One process-wide instance
 * shared by the Library, the cart sheet and Recitation detail.
 *
 * Saved to disk on every change, so a cart survives the app being killed.
 * Each line carries a client_uuid the server uses to ignore duplicates: a
 * "Log all" that is retried after a network error never counts twice. A line
 * gets a fresh uuid whenever its count changes.
 */
public final class Cart {

    /** One selected recitation and how many times it was recited. */
    public static final class Line {
        public final String id;
        public final String arabicName;
        public final String englishName;
        public final int step;
        public int count;
        public String clientUuid;

        Line(String id, String arabicName, String englishName, int step, int count) {
            this.id = id;
            this.arabicName = arabicName;
            this.englishName = englishName;
            this.step = Math.max(1, step);
            this.count = count;
            this.clientUuid = UUID.randomUUID().toString();
        }

        /** A frozen copy: what was sent stays exactly what was sent. */
        Line copy() {
            Line c = new Line(id, arabicName, englishName, step, count);
            c.clientUuid = clientUuid;
            return c;
        }
    }

    public interface Listener { void onCartChanged(); }

    private static final String PREFS = "kasrat_cart";
    private static final String KEY = "lines";
    private static final Cart INSTANCE = new Cart();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static Cart get() { return INSTANCE; }

    private final Map<String, Line> lines = new LinkedHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    @Nullable private SharedPreferences prefs;

    private Cart() { }

    /** Call once from Application.onCreate: restores the saved cart. */
    public synchronized void attach(@NonNull Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY, null);
        if (json == null) return;
        try {
            List<Line> saved = new Gson().fromJson(json, new TypeToken<List<Line>>() { }.getType());
            lines.clear();
            if (saved != null) {
                for (Line l : saved) if (l != null && l.id != null && l.count > 0) lines.put(l.id, l);
            }
        } catch (RuntimeException ignored) {
            prefs.edit().remove(KEY).apply();          // unreadable: start empty
        }
    }

    public void addListener(@NonNull Listener l) { if (!listeners.contains(l)) listeners.add(l); }

    public void removeListener(@NonNull Listener l) { listeners.remove(l); }

    public synchronized int countFor(String id) {
        Line l = lines.get(id);
        return l == null ? 0 : l.count;
    }

    /** Set an exact count for a recitation (<= 0 removes it). */
    public synchronized void set(@NonNull String id, String arabicName, String englishName, int step, int count) {
        if (count <= 0) {
            lines.remove(id);
        } else {
            Line l = lines.get(id);
            if (l == null) {
                lines.put(id, new Line(id, arabicName, englishName, step, count));
            } else if (l.count != count) {
                l.count = count;
                l.clientUuid = UUID.randomUUID().toString();
            }
        }
        changed();
    }

    /** Adds one step (the recitation's default increment). */
    public synchronized void add(@NonNull String id, String arabicName, String englishName, int step) {
        set(id, arabicName, englishName, step, countFor(id) + Math.max(1, step));
    }

    /** Removes one step; drops the line at zero. */
    public synchronized void subtract(@NonNull String id) {
        Line l = lines.get(id);
        if (l == null) return;
        set(id, l.arabicName, l.englishName, l.step, l.count - l.step);
    }

    public synchronized void remove(@NonNull String id) {
        if (lines.remove(id) != null) changed();
    }

    @NonNull
    public synchronized List<Line> lines() {
        List<Line> out = new ArrayList<>(lines.size());
        for (Line l : lines.values()) out.add(l.copy());
        return out;
    }

    public synchronized int distinctCount() { return lines.size(); }

    public synchronized long totalCount() {
        long t = 0;
        for (Line l : lines.values()) t += l.count;
        return t;
    }

    public synchronized boolean isEmpty() { return lines.isEmpty(); }

    /**
     * Takes the logged amounts out of the cart. A line changed while the log
     * was being sent keeps only what was added on top of what was sent.
     */
    public synchronized void removeLogged(@NonNull List<Line> logged) {
        boolean any = false;
        for (Line done : logged) {
            Line now = lines.get(done.id);
            if (now == null) continue;
            any = true;
            if (now.clientUuid.equals(done.clientUuid) || now.count <= done.count) {
                lines.remove(done.id);
            } else {
                now.count -= done.count;
                now.clientUuid = UUID.randomUUID().toString();
            }
        }
        if (any) changed();
    }

    public synchronized void clear() {
        lines.clear();
        changed();
    }

    private void changed() {
        if (prefs != null) {
            prefs.edit().putString(KEY, new Gson().toJson(new ArrayList<>(lines.values()))).apply();
        }
        // Screens listen on the main thread; sign-out can clear the cart from a network thread.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            for (Listener l : listeners) l.onCartChanged();
        } else {
            MAIN.post(() -> {
                for (Listener l : listeners) l.onCartChanged();
            });
        }
    }
}
