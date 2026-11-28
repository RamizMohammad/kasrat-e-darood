package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.profile;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.Totals;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiErrors;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.base.BaseActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.EdgeInsets;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Numbers;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.AppPrefs;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.Skeleton;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Phase 7a · History (Stitch history_teal). One month at a time (‹ November 2026 ›) with its
 * total and active days; each day's logs in one hairline card, newest first. Pages of 100
 * (GET /submissions/me, X-Next-Cursor) load until the chosen month is complete.
 * A log can be undone for 5 minutes; its capsule counts down and goes when the window closes
 * (the server checks the window too). The time line names the prayer it followed, from the
 * prayer times Home saved ("After Fajr").
 */
public class SubmissionsActivity extends BaseActivity {

    private static final int PAGE = 100;
    private static final long UNDO_WINDOW_MS = 5 * 60_000L;
    private static final int[] PRAYERS = {R.string.prayer_fajr, R.string.prayer_dhuhr, R.string.prayer_asr,
            R.string.prayer_maghrib, R.string.prayer_isha};

    private RecyclerView list;
    private SwipeRefreshLayout refresh;
    private View skeleton, state;
    private final Adapter adapter = new Adapter();
    private final List<dto.MySubmissionDto> items = new ArrayList<>();
    @Nullable private String cursor;
    private boolean loading, endReached, failed;
    private int generation;                   // ignores replies from before a refresh
    private String lang;
    private YearMonth month = YearMonth.now();
    @Nullable private LocalTime[] prayerTimes;
    private final Set<String> closed = new HashSet<>();   // the server said "too late"
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (adapter.tickUndo()) ticker.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_submissions);
        EdgeInsets.padTop(findViewById(R.id.subs_header));
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        lang = new AppPrefs(this).lang();
        prayerTimes = savedPrayerTimes(this);
        if (savedInstanceState != null) {
            try {
                month = YearMonth.parse(savedInstanceState.getString("month", month.toString()));
            } catch (RuntimeException ignored) { }
        }

        list = findViewById(R.id.subs_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        EdgeInsets.padBottom(list, false);
        refresh = findViewById(R.id.subs_refresh);
        refresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.amber));
        refresh.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.surface_lowest));
        refresh.setOnRefreshListener(this::reload);
        skeleton = findViewById(R.id.subs_skeleton);
        state = findViewById(R.id.subs_state);
        findViewById(R.id.subs_prev).setOnClickListener(v -> moveMonth(-1));
        findViewById(R.id.subs_next).setOnClickListener(v -> moveMonth(1));

        bindMonth();
        reload();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("month", month.toString());
    }

    @Override
    protected void onResume() {
        super.onResume();
        ticker.post(tick);
    }

    @Override
    protected void onPause() {
        ticker.removeCallbacks(tick);
        super.onPause();
    }

    // ── Month ───────────────────────────────────────────────────────────────

    private void moveMonth(int by) {
        YearMonth next = month.plusMonths(by);
        if (next.isAfter(YearMonth.now())) return;
        if (by < 0 && endReached && !hasBefore(month)) return;
        Haptics.tick(this);
        month = next;
        bindMonth();
        render();
        ensureMonth();
    }

    private void bindMonth() {
        Locale loc = getResources().getConfiguration().getLocales().get(0);
        ((TextView) findViewById(R.id.subs_month)).setText(month.format(DateTimeFormatter.ofPattern("LLLL yyyy", loc)));
        boolean current = month.equals(YearMonth.now());
        ((TextView) findViewById(R.id.subs_summary_label)).setText(current
                ? getString(R.string.history_this_month)
                : month.format(DateTimeFormatter.ofPattern("LLLL", loc)));
        View next = findViewById(R.id.subs_next);
        next.setEnabled(!current);
        next.setAlpha(current ? 0.4f : 1f);
        boolean prev = !endReached || hasBefore(month);
        View p = findViewById(R.id.subs_prev);
        p.setEnabled(prev);
        p.setAlpha(prev ? 1f : 0.4f);
    }

    private boolean hasBefore(@NonNull YearMonth m) {
        LocalDate start = m.atDay(1);
        for (dto.MySubmissionDto s : items) {
            ZonedDateTime t = when(s.createdAt);
            if (t != null && t.toLocalDate().isBefore(start)) return true;
        }
        return false;
    }

    /** True when the loaded pages already reach past the start of the month. */
    private boolean monthComplete() {
        return endReached || hasBefore(month);
    }

    // ── Paging ──────────────────────────────────────────────────────────────

    private void reload() {
        generation++;
        cursor = null;
        endReached = false;
        loading = false;
        failed = false;
        items.clear();
        if (!refresh.isRefreshing()) {
            skeleton.setVisibility(View.VISIBLE);
            Skeleton.shimmer(skeleton);
        }
        state.setVisibility(View.GONE);
        adapter.rebuild();
        fetch();
    }

    private void ensureMonth() {
        if (!loading && !failed && !monthComplete()) fetch();
    }

    private void fetch() {
        loading = true;
        int gen = generation;
        ApiClient.get(this).submissionsPage(PAGE, cursor)
                .enqueue(new Callback<List<dto.MySubmissionDto>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<dto.MySubmissionDto>> call,
                                           @NonNull Response<List<dto.MySubmissionDto>> r) {
                        if (gen != generation || isFinishing()) return;
                        loading = false;
                        refresh.setRefreshing(false);
                        if (!r.isSuccessful() || r.body() == null) {
                            failed = true;
                            render();
                            return;
                        }
                        cursor = r.headers().get("X-Next-Cursor");
                        endReached = TextUtils.isEmpty(cursor) || r.body().isEmpty();
                        items.addAll(r.body());
                        bindMonth();
                        if (monthComplete()) render();
                        else fetch();                  // keep going until the month is whole
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<dto.MySubmissionDto>> call, @NonNull Throwable t) {
                        if (gen != generation || isFinishing()) return;
                        loading = false;
                        refresh.setRefreshing(false);
                        failed = true;
                        render();
                    }
                });
    }

    /** Draw the chosen month (or the skeleton while it's still loading). */
    private void render() {
        boolean ready = monthComplete() || failed;
        if (!ready) {
            skeleton.setVisibility(View.VISIBLE);
            Skeleton.shimmer(skeleton);
            state.setVisibility(View.GONE);
            adapter.rebuild();
            ((TextView) findViewById(R.id.subs_summary)).setText("—");
            return;
        }
        Skeleton.reveal(skeleton, null);
        skeleton.setVisibility(View.GONE);
        adapter.rebuild();
        long total = 0;
        Set<LocalDate> days = new HashSet<>();
        for (dto.MySubmissionDto s : inMonth()) {
            total += s.count;
            ZonedDateTime t = when(s.createdAt);
            if (t != null) days.add(t.toLocalDate());
        }
        ((TextView) findViewById(R.id.subs_summary)).setText(getString(R.string.history_summary, Numbers.format(total),
                getResources().getQuantityString(R.plurals.me_k_active_days, days.size(), days.size())));
        if (failed && inMonth().isEmpty()) {
            showState(R.string.subs_error_title, R.string.subs_error_hint, R.string.action_try_again, v -> {
                failed = false;
                if (items.isEmpty()) reload();
                else {
                    state.setVisibility(View.GONE);
                    render();
                    ensureMonth();
                }
            });
        } else if (items.isEmpty()) {
            showState(R.string.subs_empty_title, R.string.subs_empty_hint, R.string.me_empty_action, v -> finish());
        } else if (inMonth().isEmpty()) {
            Locale loc = getResources().getConfiguration().getLocales().get(0);
            state.setVisibility(View.VISIBLE);
            ((TextView) state.findViewById(R.id.subs_state_title)).setText(getString(R.string.history_month_empty,
                    month.format(DateTimeFormatter.ofPattern("LLLL", loc))));
            ((TextView) state.findViewById(R.id.subs_state_hint)).setText(R.string.history_month_empty_hint);
            state.findViewById(R.id.subs_state_action).setVisibility(View.GONE);
        } else {
            state.setVisibility(View.GONE);
        }
        ticker.removeCallbacks(tick);
        ticker.post(tick);
    }

    @NonNull
    private List<dto.MySubmissionDto> inMonth() {
        List<dto.MySubmissionDto> out = new ArrayList<>();
        for (dto.MySubmissionDto s : items) {
            ZonedDateTime t = when(s.createdAt);
            if (t != null && YearMonth.from(t).equals(month)) out.add(s);
        }
        return out;
    }

    private void showState(@StringRes int title, @StringRes int hint, @StringRes int action, View.OnClickListener onAction) {
        state.setVisibility(View.VISIBLE);
        ((TextView) state.findViewById(R.id.subs_state_title)).setText(title);
        ((TextView) state.findViewById(R.id.subs_state_hint)).setText(hint);
        MaterialButton b = state.findViewById(R.id.subs_state_action);
        b.setVisibility(View.VISIBLE);
        b.setText(action);
        b.setOnClickListener(onAction);
    }

    // ── Undo ────────────────────────────────────────────────────────────────

    private void undo(@NonNull dto.MySubmissionDto s, @NonNull View button) {
        button.setEnabled(false);
        Haptics.tick(this);
        ApiClient.get(this).undoSubmission(s.id).enqueue(new Callback<dto.Totals>() {
            @Override
            public void onResponse(@NonNull Call<dto.Totals> call, @NonNull Response<dto.Totals> r) {
                if (isFinishing()) return;
                if (r.isSuccessful() && r.body() != null) {
                    Totals.publish(r.body());
                    items.remove(s);
                    render();
                    Haptics.click(SubmissionsActivity.this);
                    snack(getString(R.string.subs_undone, Numbers.format(s.count), name(s)));
                } else {
                    button.setEnabled(true);
                    Haptics.reject(SubmissionsActivity.this);
                    if (r.code() == 409) {
                        closed.add(s.id);                         // window closed: hide the capsule
                        adapter.notifyDataSetChanged();
                        snack(getString(R.string.subs_undo_too_late));
                    } else {
                        String m = ApiErrors.of(r).message;
                        snack(m != null ? m : getString(R.string.subs_undo_failed));
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<dto.Totals> call, @NonNull Throwable t) {
                if (isFinishing()) return;
                button.setEnabled(true);
                Haptics.reject(SubmissionsActivity.this);
                snack(getString(R.string.error_network));
            }
        });
    }

    private void snack(@NonNull String text) {
        Snackbar.make(list, text, Snackbar.LENGTH_LONG).show();
    }

    @NonNull
    private String name(@NonNull dto.MySubmissionDto s) {
        if ("ur".equals(lang) && !TextUtils.isEmpty(s.urduName)) return s.urduName;
        return s.recitationName != null ? s.recitationName : getString(R.string.subs_recitation);
    }

    // ── Time and prayer ─────────────────────────────────────────────────────

    @Nullable
    private static ZonedDateTime when(@Nullable String iso) {
        if (iso == null) return null;
        try {
            return OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault());
        } catch (RuntimeException e) {
            try {   // "2026-11-24T13:29:24.123456" without an offset = UTC
                return java.time.LocalDateTime.parse(iso).atZone(java.time.ZoneOffset.UTC)
                        .withZoneSameInstant(ZoneId.systemDefault());
            } catch (RuntimeException e2) {
                return null;
            }
        }
    }

    /** Fajr … Isha as Home saved them for the phone's location ("05:21,12:30,…"), or null. */
    @Nullable
    private static LocalTime[] savedPrayerTimes(@NonNull Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences("kasrat_prayer", Context.MODE_PRIVATE);
        String times = p.getString("times", null);
        if (times == null) return null;
        String[] parts = times.split(",");
        if (parts.length < 5) return null;
        LocalTime[] out = new LocalTime[5];
        try {
            for (int i = 0; i < 5; i++) out[i] = LocalTime.parse(parts[i].trim());
        } catch (RuntimeException e) {
            return null;
        }
        return out;
    }

    /** "After Maghrib" for a time between Maghrib and Isha; "Before Fajr" in the small hours. */
    @Nullable
    private String prayerLabel(@NonNull LocalTime t) {
        if (prayerTimes == null) return null;
        if (t.isBefore(prayerTimes[0])) return getString(R.string.history_before, getString(PRAYERS[0]));
        for (int i = 4; i >= 0; i--) {
            if (!t.isBefore(prayerTimes[i])) return getString(R.string.history_after, getString(PRAYERS[i]));
        }
        return null;
    }

    /** Milliseconds of undo left, or 0. */
    private long undoLeft(@NonNull dto.MySubmissionDto s) {
        if (s.id != null && closed.contains(s.id)) return 0;
        ZonedDateTime t = when(s.createdAt);
        if (t == null) return 0;
        long left = t.toInstant().toEpochMilli() + UNDO_WINDOW_MS - Instant.now().toEpochMilli();
        return Math.max(0, left);
    }

    /** Milliseconds since the undo window closed (huge if unknown). */
    private static long sinceExpiry(@NonNull dto.MySubmissionDto s) {
        ZonedDateTime t = when(s.createdAt);
        if (t == null) return Long.MAX_VALUE;
        return Instant.now().toEpochMilli() - (t.toInstant().toEpochMilli() + UNDO_WINDOW_MS);
    }

    // ── Rows ────────────────────────────────────────────────────────────────

    private static final int HEADER = 0, ROW = 1;
    private static final Object TICK = new Object();

    /** A log and where it sits in its day's card (for the corner/hairline segment). */
    private static final class Entry {
        final dto.MySubmissionDto s;
        final int index, of;

        Entry(dto.MySubmissionDto s, int index, int of) {
            this.s = s;
            this.index = index;
            this.of = of;
        }
    }

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        /** LocalDate for a header, Entry for a row. */
        private final List<Object> rows = new ArrayList<>();
        private final List<Long> dayTotals = new ArrayList<>();

        void rebuild() {
            rows.clear();
            dayTotals.clear();
            if (monthComplete() || failed) {
                List<dto.MySubmissionDto> month = inMonth();
                int i = 0;
                while (i < month.size()) {
                    ZonedDateTime t = when(month.get(i).createdAt);
                    LocalDate day = t != null ? t.toLocalDate() : LocalDate.MIN;
                    int j = i;
                    long sum = 0;
                    while (j < month.size()) {
                        ZonedDateTime tj = when(month.get(j).createdAt);
                        if (!(tj != null ? tj.toLocalDate() : LocalDate.MIN).equals(day)) break;
                        sum += month.get(j).count;
                        j++;
                    }
                    rows.add(day);
                    dayTotals.add(sum);
                    for (int k = i; k < j; k++) rows.add(new Entry(month.get(k), k - i, j - i));
                    i = j;
                }
            }
            notifyDataSetChanged();
        }

        /** Refresh countdowns; true while any row can still be undone. */
        boolean tickUndo() {
            boolean any = false;
            for (int i = 0; i < rows.size(); i++) {
                Object o = rows.get(i);
                if (!(o instanceof Entry)) continue;
                dto.MySubmissionDto s = ((Entry) o).s;
                long left = undoLeft(s);
                if (left > 0) any = true;
                if (left > 0 || sinceExpiry(s) < 2500) notifyItemChanged(i, TICK);
            }
            return any;
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position) instanceof LocalDate ? HEADER : ROW;
        }

        @Override
        public int getItemCount() { return rows.size(); }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            int layout = type == HEADER ? R.layout.item_history_day : R.layout.item_history_row;
            View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
            return new RecyclerView.ViewHolder(v) { };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            View v = h.itemView;
            if (getItemViewType(position) == HEADER) {
                LocalDate day = (LocalDate) rows.get(position);
                int headers = 0;
                for (int i = 0; i < position; i++) if (rows.get(i) instanceof LocalDate) headers++;
                v.setPadding(v.getPaddingLeft(), position == 0 ? dp(8) : dp(24), v.getPaddingRight(), v.getPaddingBottom());
                ((TextView) v.findViewById(R.id.day_label)).setText(dayLabel(day));
                ((TextView) v.findViewById(R.id.day_total)).setText(getString(R.string.history_day_total,
                        Numbers.format(dayTotals.get(headers))));
                return;
            }
            Entry e = (Entry) rows.get(position);
            dto.MySubmissionDto s = e.s;
            v.setBackgroundResource(e.of == 1 ? R.drawable.bg_k_row_single
                    : e.index == 0 ? R.drawable.bg_k_row_top
                    : e.index == e.of - 1 ? R.drawable.bg_k_row_bottom : R.drawable.bg_k_row_mid);
            ((TextView) v.findViewById(R.id.sub_name)).setText(name(s));
            ZonedDateTime t = when(s.createdAt);
            String time = "";
            if (t != null) {
                time = t.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT));
                String p = prayerLabel(t.toLocalTime());
                if (p != null) time += " · " + p;
            }
            ((TextView) v.findViewById(R.id.sub_time)).setText(time);
            ((TextView) v.findViewById(R.id.sub_count)).setText(Numbers.format(s.count));
            bindUndo(v, s);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position,
                                     @NonNull List<Object> payloads) {
            if (payloads.contains(TICK) && getItemViewType(position) == ROW) {
                bindUndo(h.itemView, ((Entry) rows.get(position)).s);
            } else {
                onBindViewHolder(h, position);
            }
        }

        private void bindUndo(@NonNull View v, @NonNull dto.MySubmissionDto s) {
            View undo = v.findViewById(R.id.sub_undo);
            long left = undoLeft(s);
            if (left <= 0) {
                undo.setVisibility(View.GONE);
                return;
            }
            long secs = (left + 999) / 1000;
            undo.setVisibility(View.VISIBLE);
            undo.setEnabled(true);
            ((TextView) v.findViewById(R.id.sub_undo_label)).setText(getString(R.string.subs_undo_countdown,
                    String.format(Locale.US, "%d:%02d", secs / 60, secs % 60)));
            undo.setContentDescription(getString(R.string.subs_undo_cd, name(s), secs));
            undo.setOnClickListener(x -> undo(s, undo));
        }

        @NonNull
        private String dayLabel(@NonNull LocalDate day) {
            LocalDate today = LocalDate.now();
            if (day.equals(today)) return getString(R.string.subs_today);
            if (day.equals(today.minusDays(1))) return getString(R.string.subs_yesterday);
            if (day.equals(LocalDate.MIN)) return "";
            Locale loc = getResources().getConfiguration().getLocales().get(0);
            return day.format(DateTimeFormatter.ofPattern("EEE d MMM", loc));
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
