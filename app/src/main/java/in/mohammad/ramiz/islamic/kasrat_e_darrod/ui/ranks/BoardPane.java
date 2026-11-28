package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.ranks;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.cache.ApiCache;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.cache.Cached;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.cache.Prefetch;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.Totals;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Motion;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Numbers;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * One board on screen (view_board_pane): the global board on Ranks, or a
 * group's on Group home.
 *
 * Numbers, ranks (ties share one) and "A member" for people who hide
 * themselves come from the server as they are. Your row is amber; while it's
 * off screen (or you're past the top 100) a copy is pinned to the bottom. The
 * last board is kept on the phone, so it opens at once and refreshes behind.
 */
public final class BoardPane implements BoardAdapter.Listener {

    public static final int LIMIT = 100;
    private static final String PREFS = "kasrat_ranks";

    /** Where a board comes from. */
    public interface Source {
        @NonNull Call<dto.BoardResponse> load(@NonNull String scope, @Nullable Boolean aroundMe, int limit);
        /** Unique per board, e.g. "global" or "group:<id>". */
        @NonNull String key();
    }

    /** What the empty board's button does ("Go to Recite"). */
    public interface Host {
        void onEmptyAction();
    }

    private final Context ctx;
    private final Source source;
    private final Host host;
    private final BooleanSupplier alive;
    private final Gson gson = new Gson();
    private final Map<String, Integer> loadedAt = new HashMap<>();   // scope → Totals.version()
    private final BoardAdapter adapter;
    private final RecyclerView list;
    private final LinearLayoutManager layout;
    private final SwipeRefreshLayout refresh;
    private final View root, pinned, pinnedRow;
    private String scope = "week";
    private boolean pinnedShown, shown = true;
    private int requestSeq;
    @Nullable private Runnable onLoaded;

    public BoardPane(@NonNull View root, @NonNull Source source, @NonNull BoardAdapter.Labels labels,
                     @NonNull Host host, @NonNull BooleanSupplier alive) {
        this.root = root;
        this.ctx = root.getContext();
        this.source = source;
        this.host = host;
        this.alive = alive;
        list = root.findViewById(R.id.board_list);
        refresh = root.findViewById(R.id.board_refresh);
        pinned = root.findViewById(R.id.board_pinned);
        pinnedRow = root.findViewById(R.id.board_pinned_row);

        refresh.setColorSchemeColors(ContextCompat.getColor(ctx, R.color.amber));
        refresh.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(ctx, R.color.surface_raised));
        refresh.setOnRefreshListener(() -> load(scope));

        adapter = new BoardAdapter(this, labels);
        layout = new LinearLayoutManager(ctx);
        list.setLayoutManager(layout);
        list.setAdapter(adapter);
        list.setItemAnimator(null);                     // rows rebind in place; no flashing
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                updatePinned();
            }
        });
        list.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updatePinned());
        pinnedRow.setOnClickListener(v -> scrollToMe());
    }

    /** Show the saved board (if any) and get a fresh one. */
    public void start(@NonNull String initialScope) {
        scope = initialScope;
        dto.BoardResponse cached = readCache(scope);
        if (cached != null) adapter.show(scope, cached, false);
        else adapter.showLoading(scope);
        if (Motion.enabled()) Motion.enterList(list);
    }

    /** Call from onResume: reloads when anything was logged or undone since. */
    public void resume() {
        Integer at = loadedAt.get(scope);
        if (at != null && at == Totals.version()) return;
        // Prefetched at launch a moment ago: use that instead of asking again.
        if (at == null && readCache(scope) != null
                && ApiCache.isFresh(ctx, cacheKey(scope), Cached.FRESH_SHORT)) {
            loadedAt.put(scope, Totals.version());
            return;
        }
        load(scope);
    }

    public void reload() {
        loadedAt.clear();
        adapter.showLoading(scope);
        load(scope);
    }

    @NonNull public String scope() { return scope; }

    @Nullable public dto.BoardResponse board() { return adapter.board(); }

    /**
     * Room the floating bottom bar takes (MainActivity.navSpace): the pinned row floats
     * 12dp above it and the list scrolls clear of both.
     */
    public void setBottomSpace(int px) {
        float dp = ctx.getResources().getDisplayMetrics().density;
        android.view.ViewGroup.MarginLayoutParams lp =
                (android.view.ViewGroup.MarginLayoutParams) pinned.getLayoutParams();
        lp.bottomMargin = px + (int) (12 * dp);
        pinned.setLayoutParams(lp);
        list.setPadding(list.getPaddingLeft(), list.getPaddingTop(), list.getPaddingRight(),
                px + (int) (84 * dp));
    }

    /** Runs after each fresh board arrives (e.g. to update a goal bar). */
    public void setOnLoaded(@Nullable Runnable r) { onLoaded = r; }

    /** The pane was hidden or shown (a tab switch): the pinned row follows. */
    public void setShown(boolean on) {
        shown = on;
        root.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) updatePinned(); else setPinned(false);
    }

    // ── BoardAdapter.Listener ───────────────────────────────────────────────

    @Override
    public void onScope(@NonNull String s) {
        Haptics.tick(ctx);
        scope = s;
        dto.BoardResponse cached = readCache(s);
        if (cached != null) adapter.show(s, cached, false);
        else adapter.showLoading(s);
        list.scrollToPosition(0);
        load(s);
    }

    @Override
    public void onStateAction(boolean error) {
        if (error) {
            adapter.showLoading(scope);
            load(scope);
        } else {
            host.onEmptyAction();
        }
    }

    // ── Loading ─────────────────────────────────────────────────────────────

    private void load(@NonNull String s) {
        int seq = ++requestSeq;
        int version = Totals.version();
        source.load(s, null, LIMIT).enqueue(new Callback<dto.BoardResponse>() {
            @Override
            public void onResponse(@NonNull Call<dto.BoardResponse> call, @NonNull Response<dto.BoardResponse> r) {
                if (!alive.getAsBoolean()) return;
                if (seq == requestSeq) refresh.setRefreshing(false);
                if (r.isSuccessful() && r.body() != null) {
                    writeCache(s, r.body());
                    loadedAt.put(s, version);
                    if (s.equals(scope) && seq == requestSeq) {
                        adapter.show(s, r.body(), adapter.board() != null);
                        list.post(BoardPane.this::updatePinned);
                        if (onLoaded != null) onLoaded.run();
                    }
                } else if (s.equals(scope)) {
                    adapter.showError();
                }
            }

            @Override
            public void onFailure(@NonNull Call<dto.BoardResponse> call, @NonNull Throwable t) {
                if (!alive.getAsBoolean()) return;
                if (seq == requestSeq) refresh.setRefreshing(false);
                if (s.equals(scope)) adapter.showError();
            }
        });
    }

    // ── Your row, pinned while it's off screen ──────────────────────────────

    private void updatePinned() {
        if (!shown) return;
        dto.BoardResponse b = adapter.board();
        if (b == null || b.me == null || b.totalOnly && (b.entries == null || b.entries.isEmpty())) {
            setPinned(false);
            return;
        }
        int mine = adapter.myPosition();
        boolean show;
        if (mine < 0) {
            show = true;                                    // past the top 100: always pinned
        } else {
            // "Completely visible" stops at the list's bottom padding, which is
            // exactly where the pinned row sits, so it never covers your real row.
            int first = layout.findFirstCompletelyVisibleItemPosition();
            int last = layout.findLastCompletelyVisibleItemPosition();
            show = first != RecyclerView.NO_POSITION && (mine < first || mine > last);
        }
        if (show) RankRows.bind(pinnedRow, b.me, true);
        setPinned(show);
    }

    private void setPinned(boolean show) {
        if (show == pinnedShown) return;
        pinnedShown = show;
        pinned.animate().cancel();
        if (!Motion.enabled()) {
            pinned.setVisibility(show ? View.VISIBLE : View.GONE);
            return;
        }
        float dy = ctx.getResources().getDisplayMetrics().density * 24;
        if (show) {
            pinned.setVisibility(View.VISIBLE);
            pinned.setAlpha(0f);
            pinned.setTranslationY(dy);
            pinned.animate().alpha(1f).translationY(0f).setDuration(180).start();
        } else {
            pinned.animate().alpha(0f).translationY(dy).setDuration(140)
                    .withEndAction(() -> pinned.setVisibility(View.GONE)).start();
        }
    }

    private void scrollToMe() {
        int mine = adapter.myPosition();
        if (mine >= 0) {
            list.smoothScrollToPosition(mine);
            return;
        }
        // Past the top 100: say who's just ahead instead.
        source.load(scope, true, LIMIT).enqueue(new Callback<dto.BoardResponse>() {
            @Override
            public void onResponse(@NonNull Call<dto.BoardResponse> call, @NonNull Response<dto.BoardResponse> r) {
                if (!alive.getAsBoolean() || r.body() == null) return;
                Snackbar.make(list, nearbyText(r.body()), Snackbar.LENGTH_LONG).show();
            }

            @Override
            public void onFailure(@NonNull Call<dto.BoardResponse> call, @NonNull Throwable t) { }
        });
    }

    /** "Next up: Bilal, 240 ahead." for someone past the top 100. */
    @NonNull
    private String nearbyText(@NonNull dto.BoardResponse b) {
        List<dto.BoardEntry> e = b.entries;
        if (b.me != null && e != null) {
            for (int i = 1; i < e.size(); i++) {
                if (e.get(i).isMe) {
                    dto.BoardEntry above = e.get(i - 1);
                    return ctx.getString(R.string.ranks_next_up, RankRows.name(ctx, above),
                            Numbers.format(above.total - b.me.total));
                }
            }
        }
        return b.me == null ? "" : ctx.getString(R.string.ranks_your_rank, RankRows.rankText(b.me));
    }

    // ── Cache ───────────────────────────────────────────────────────────────

    @Nullable
    private dto.BoardResponse readCache(@NonNull String s) {
        return ApiCache.get(ctx, cacheKey(s), dto.BoardResponse.class);
    }

    private void writeCache(@NonNull String s, @NonNull dto.BoardResponse b) {
        ApiCache.put(ctx, cacheKey(s), b);
    }

    /** Per board (ApiCache already keeps each user's copies apart). */
    @NonNull
    private String cacheKey(@NonNull String s) {
        return Prefetch.board(source.key(), s);
    }

    @NonNull
    private SharedPreferences prefs() {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
