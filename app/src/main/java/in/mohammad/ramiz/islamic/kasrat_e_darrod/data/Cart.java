package in.mohammad.ramiz.islamic.kasrat_e_darrod.data;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory recitation cart: maps recitation id -> chosen count. Process-wide
 * singleton so the catalog and the cart sheet share one source of truth. Cleared
 * after a successful submit.
 */
public final class Cart {

    /** A single selected recitation and how many times it was recited. */
    public static final class Line {
        public final String id;
        public final String urduName;
        public final String englishName;
        public int count;

        public Line(String id, String urduName, String englishName, int count) {
            this.id = id;
            this.urduName = urduName;
            this.englishName = englishName;
            this.count = count;
        }
    }

    public interface Listener { void onCartChanged(); }

    private static final Cart INSTANCE = new Cart();
    public static Cart get() { return INSTANCE; }

    private final Map<String, Line> lines = new LinkedHashMap<>();
    private Listener listener;

    private Cart() {}

    public void setListener(Listener l) { this.listener = l; }

    public int countFor(String id) {
        Line l = lines.get(id);
        return l == null ? 0 : l.count;
    }

    /** Set an exact count for a recitation (<= 0 removes it). */
    public void set(String id, String urduName, String englishName, int count) {
        if (count <= 0) {
            lines.remove(id);
        } else {
            Line l = lines.get(id);
            if (l == null) {
                lines.put(id, new Line(id, urduName, englishName, count));
            } else {
                l.count = count;
            }
        }
        notifyChanged();
    }

    public void remove(String id) { set(id, null, null, 0); }

    @NonNull
    public List<Line> lines() { return new ArrayList<>(lines.values()); }

    /** Number of distinct recitations selected. */
    public int distinctCount() { return lines.size(); }

    /** Sum of all recitation counts. */
    public int totalCount() {
        int t = 0;
        for (Line l : lines.values()) t += l.count;
        return t;
    }

    public boolean isEmpty() { return lines.isEmpty(); }

    public void clear() {
        lines.clear();
        notifyChanged();
    }

    private void notifyChanged() {
        if (listener != null) listener.onCartChanged();
    }
}
