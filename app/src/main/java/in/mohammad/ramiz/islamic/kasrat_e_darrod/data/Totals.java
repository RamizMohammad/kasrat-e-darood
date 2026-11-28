package in.mohammad.ramiz.islamic.kasrat_e_darrod.data;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;

/**
 * The last totals the app knows (today, this week, lifetime, streak). Every
 * screen that logs a recitation publishes the server's fresh numbers here, so
 * Home's tiles roll to the new values wherever the log happened.
 *
 * {@link #shown} remembers what Home last displayed: when Home is rebuilt
 * after a tab switch it starts from those numbers and rolls to the new ones.
 */
public final class Totals {

    public interface Listener { void onTotals(@NonNull Snapshot s); }

    public static final class Snapshot {
        public final long today, week, lifetime, streak;
        /** This week's community (Global circle) total. */
        public final long groupWeek;

        public Snapshot(long today, long week, long lifetime, long streak, long groupWeek) {
            this.today = today;
            this.week = week;
            this.lifetime = lifetime;
            this.streak = streak;
            this.groupWeek = groupWeek;
        }
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    @Nullable private static Snapshot latest;
    @Nullable private static Snapshot shown;
    private static int version;

    private Totals() { }

    @Nullable public static Snapshot latest() { return latest; }

    /** What Home last put on screen (null before the first load). */
    @Nullable public static Snapshot shown() { return shown; }

    public static void markShown(@NonNull Snapshot s) { shown = s; }

    /** Bumps on every publish, so a slow dashboard reply can't overwrite a newer log. */
    public static int version() { return version; }

    public static void addListener(@NonNull Listener l) { if (!LISTENERS.contains(l)) LISTENERS.add(l); }

    public static void removeListener(@NonNull Listener l) { LISTENERS.remove(l); }

    /** From the dashboard. */
    public static void publish(@NonNull dto.DashboardResponse d) {
        publish(new Snapshot(d.todayTotal, d.weeklyTotal, d.lifetimeTotal, d.streak, d.groupTotal));
    }

    /** From a log, bulk log, tasbih or undo: the server sends the new streak too. */
    public static void publish(@NonNull dto.Totals t) {
        publish(new Snapshot(t.today, t.userWeek, t.userLifetime, t.streak, t.groupWeek));
    }

    public static void publish(@NonNull Snapshot s) {
        latest = s;
        version++;
        MAIN.post(() -> {
            for (Listener l : LISTENERS) l.onTotals(s);
        });
    }

    /** On sign-out. */
    public static void reset() {
        latest = null;
        shown = null;
        version++;
    }
}
