package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.base.BaseActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.KDayBarsView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.EdgeInsets;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Numbers;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.AppPrefs;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.WeekReportPdf;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Phase 7b · Weekly report (Stitch weekly_report_teal) — your own week, not the community's.
 *
 * Weeks: this one (GET /weeks/current) and the closed ones (GET /weeks/hall-of-fame); it opens
 * on the last closed week, ‹ › move between them. Your numbers come from your logs in that
 * week (GET /submissions/me, paged until the week is covered): total, the daily rhythm, best
 * day, most recited. "Top 8% of the ummah": this week from the global board (around you),
 * closed weeks only if you made that week's top list. Streak (GET /statistics/me) and Circle
 * results (each circle's board, around you) are only for this week, which is the only week the
 * server has them for.
 * Share as image: the 9:16 story (view_week_story) as a 1080 × 1920 PNG. Download PDF:
 * WeekReportPdf with your recitations for the week.
 */
public class WeekReportActivity extends BaseActivity {

    private static final int PAGE = 100;
    private static final int HALL_LIMIT = 26;

    /** A week to report on. */
    private static final class Week {
        int number;
        String id;
        ZonedDateTime start, end;          // end = closed_at, or closes_at for this week
        boolean current;
        @Nullable List<dto.BoardEntry> top;
    }

    /** What the screen shows for one week, worked out from your logs. */
    private static final class Summary {
        long total;
        final long[] days = new long[7];
        final LocalDate[] dates = new LocalDate[7];
        int best = -1, active, elapsed = 7;
        @Nullable String most;
        final List<dto.RecitationStat> byRecitation = new ArrayList<>();
    }

    private final List<Week> weeks = new ArrayList<>();
    private int selected;
    private final List<dto.MySubmissionDto> logs = new ArrayList<>();
    @Nullable private String cursor;
    private boolean logsEnd, loadingLogs, busy;
    private int generation;
    @Nullable private Integer streak;
    @Nullable private Integer rankPct, rankPlace;    // this week, from the global board
    private final Map<String, View> circleRows = new LinkedHashMap<>();
    private String lang;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_week_report);
        EdgeInsets.padTop(findViewById(R.id.report_header));
        EdgeInsets.padBottom(findViewById(R.id.report_scroll), false);
        lang = new AppPrefs(this).lang();
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.report_retry).setOnClickListener(v -> loadWeeks());
        findViewById(R.id.report_prev).setOnClickListener(v -> move(1));
        findViewById(R.id.report_next).setOnClickListener(v -> move(-1));
        findViewById(R.id.report_share).setOnClickListener(v -> share());
        findViewById(R.id.report_pdf).setOnClickListener(v -> pdf());
        loadWeeks();
    }

    // ── Weeks ───────────────────────────────────────────────────────────────

    @Nullable
    private static ZonedDateTime when(@Nullable String iso) {
        if (iso == null) return null;
        try {
            return OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault());
        } catch (RuntimeException e) {
            try {
                return java.time.LocalDateTime.parse(iso).atZone(java.time.ZoneOffset.UTC)
                        .withZoneSameInstant(ZoneId.systemDefault());
            } catch (RuntimeException e2) {
                return null;
            }
        }
    }

    private void loadWeeks() {
        generation++;
        int gen = generation;
        weeks.clear();
        findViewById(R.id.report_state).setVisibility(View.GONE);
        findViewById(R.id.report_loading).setVisibility(View.VISIBLE);
        final int[] pending = {2};
        final Week[] current = {null};
        final List<Week> closed = new ArrayList<>();
        Runnable done = () -> {
            if (--pending[0] > 0 || gen != generation || isFinishing()) return;
            if (current[0] != null) weeks.add(current[0]);
            for (Week w : closed) {
                if (current[0] != null && w.number == current[0].number) continue;
                weeks.add(w);
            }
            if (weeks.isEmpty()) {
                findViewById(R.id.report_loading).setVisibility(View.GONE);
                findViewById(R.id.report_state).setVisibility(View.VISIBLE);
                return;
            }
            selected = weeks.size() > 1 && weeks.get(0).current ? 1 : 0;   // the last closed week
            show();
            loadThisWeekExtras();
        };
        ApiClient.get(this).currentWeek().enqueue(new Callback<dto.WeekInfo>() {
            @Override
            public void onResponse(@NonNull Call<dto.WeekInfo> call, @NonNull Response<dto.WeekInfo> r) {
                if (r.isSuccessful() && r.body() != null) {
                    Week w = new Week();
                    w.number = r.body().weekNumber;
                    w.id = r.body().id;
                    w.start = when(r.body().startsAt);
                    w.end = when(r.body().closesAt);
                    w.current = true;
                    if (w.start != null) current[0] = w;
                }
                done.run();
            }

            @Override
            public void onFailure(@NonNull Call<dto.WeekInfo> call, @NonNull Throwable t) { done.run(); }
        });
        ApiClient.get(this).hallOfFame(null, HALL_LIMIT).enqueue(new Callback<List<dto.HallOfFameWeek>>() {
            @Override
            public void onResponse(@NonNull Call<List<dto.HallOfFameWeek>> call, @NonNull Response<List<dto.HallOfFameWeek>> r) {
                if (r.isSuccessful() && r.body() != null) {
                    for (dto.HallOfFameWeek h : r.body()) {
                        Week w = new Week();
                        w.number = h.weekNumber;
                        w.id = h.weekId;
                        w.start = when(h.startsAt);
                        w.end = when(h.closedAt);
                        w.top = h.top;
                        if (w.start != null && w.end != null) closed.add(w);
                    }
                    closed.sort((a, b) -> b.start.compareTo(a.start));
                }
                done.run();
            }

            @Override
            public void onFailure(@NonNull Call<List<dto.HallOfFameWeek>> call, @NonNull Throwable t) { done.run(); }
        });
    }

    /** by > 0 → older, by < 0 → newer. */
    private void move(int by) {
        int next = selected + by;
        if (next < 0 || next >= weeks.size() || busy) return;
        Haptics.tick(this);
        selected = next;
        show();
    }

    // ── Your logs ───────────────────────────────────────────────────────────

    private boolean covered(@NonNull Week w) {
        if (logsEnd) return true;
        if (logs.isEmpty()) return false;
        ZonedDateTime oldest = when(logs.get(logs.size() - 1).createdAt);
        return oldest != null && oldest.isBefore(w.start);
    }

    private void show() {
        Week w = weeks.get(selected);
        bindWeekLabel(w);
        if (!covered(w)) {
            findViewById(R.id.report_loading).setVisibility(View.VISIBLE);
            fetchLogs();
            return;
        }
        findViewById(R.id.report_loading).setVisibility(View.GONE);
        findViewById(R.id.report_scroll).setVisibility(View.VISIBLE);
        bind(w, summarize(w));
    }

    private void fetchLogs() {
        if (loadingLogs) return;
        loadingLogs = true;
        int gen = generation;
        ApiClient.get(this).submissionsPage(PAGE, cursor).enqueue(new Callback<List<dto.MySubmissionDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<dto.MySubmissionDto>> call,
                                   @NonNull Response<List<dto.MySubmissionDto>> r) {
                if (gen != generation || isFinishing()) return;
                loadingLogs = false;
                if (!r.isSuccessful() || r.body() == null) {
                    logsFailed();
                    return;
                }
                logs.addAll(r.body());
                cursor = r.headers().get("X-Next-Cursor");
                logsEnd = TextUtils.isEmpty(cursor) || r.body().isEmpty();
                show();
            }

            @Override
            public void onFailure(@NonNull Call<List<dto.MySubmissionDto>> call, @NonNull Throwable t) {
                if (gen != generation || isFinishing()) return;
                loadingLogs = false;
                logsFailed();
            }
        });
    }

    private void logsFailed() {
        findViewById(R.id.report_loading).setVisibility(View.GONE);
        if (findViewById(R.id.report_scroll).getVisibility() != View.VISIBLE) {
            findViewById(R.id.report_state).setVisibility(View.VISIBLE);
        } else {
            Snackbar.make(findViewById(R.id.root), R.string.week_error_title, Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_try_again, v -> show()).show();
        }
    }

    @NonNull
    private String name(@NonNull dto.MySubmissionDto s) {
        if ("ur".equals(lang) && !TextUtils.isEmpty(s.urduName)) return s.urduName;
        return s.recitationName != null ? s.recitationName : getString(R.string.subs_recitation);
    }

    @NonNull
    private Summary summarize(@NonNull Week w) {
        Summary s = new Summary();
        ZonedDateTime end = w.end != null ? w.end : ZonedDateTime.now();
        LocalDate last = end.toLocalDate();
        for (int i = 0; i < 7; i++) s.dates[i] = last.minusDays(6 - i);
        if (w.current) {
            long sinceStart = ChronoUnit.DAYS.between(w.start.toLocalDate(), LocalDate.now()) + 1;
            s.elapsed = (int) Math.max(1, Math.min(7, sinceStart));
        }
        Map<String, dto.RecitationStat> by = new HashMap<>();
        for (dto.MySubmissionDto m : logs) {
            ZonedDateTime t = when(m.createdAt);
            if (t == null || t.isBefore(w.start) || !t.isBefore(end)) continue;
            s.total += m.count;
            // A log on the evening the week opened (before Saturday) counts with the first bar.
            int i = (int) Math.max(0, 6 - ChronoUnit.DAYS.between(t.toLocalDate(), last));
            s.days[Math.min(6, i)] += m.count;
            String key = m.recitationId != null ? m.recitationId : name(m);
            dto.RecitationStat r = by.get(key);
            if (r == null) {
                r = new dto.RecitationStat();
                r.recitationId = m.recitationId;
                r.name = m.recitationName != null ? m.recitationName : name(m);
                r.urduName = m.urduName;
                by.put(key, r);
            }
            r.count += m.count;
        }
        for (int i = 0; i < 7; i++) {
            if (s.days[i] > 0) s.active++;
            if (s.days[i] > 0 && (s.best < 0 || s.days[i] > s.days[s.best])) s.best = i;
        }
        s.byRecitation.addAll(by.values());
        s.byRecitation.sort((a, b) -> Integer.compare(b.count, a.count));
        if (!s.byRecitation.isEmpty()) {
            dto.RecitationStat top = s.byRecitation.get(0);
            s.most = "ur".equals(lang) && !TextUtils.isEmpty(top.urduName) ? top.urduName : top.name;
        }
        return s;
    }

    // ── Binding ─────────────────────────────────────────────────────────────

    @NonNull
    private Locale locale() {
        return getResources().getConfiguration().getLocales().get(0);
    }

    @NonNull
    private String weekLabel(@NonNull Week w) {
        LocalDate a = w.start.toLocalDate().plusDays(1), b = (w.end != null ? w.end : ZonedDateTime.now()).toLocalDate();
        if (w.end == null || b.isBefore(a)) b = a.plusDays(6);
        Locale loc = locale();
        String range;
        if (a.getMonth() == b.getMonth() && a.getYear() == b.getYear()) {
            range = a.getDayOfMonth() + "–" + b.format(DateTimeFormatter.ofPattern("d MMM yyyy", loc));
        } else if (a.getYear() == b.getYear()) {
            range = a.format(DateTimeFormatter.ofPattern("d MMM", loc)) + " – " + b.format(DateTimeFormatter.ofPattern("d MMM yyyy", loc));
        } else {
            range = a.format(DateTimeFormatter.ofPattern("d MMM yyyy", loc)) + " – " + b.format(DateTimeFormatter.ofPattern("d MMM yyyy", loc));
        }
        return getString(R.string.report_k_week, w.number, range);
    }

    private void bindWeekLabel(@NonNull Week w) {
        ((TextView) findViewById(R.id.report_week)).setText(weekLabel(w));
        View prev = findViewById(R.id.report_prev), next = findViewById(R.id.report_next);
        boolean older = selected < weeks.size() - 1, newer = selected > 0;
        prev.setEnabled(older);
        prev.setAlpha(older ? 1f : 0.3f);
        next.setEnabled(newer);
        next.setAlpha(newer ? 1f : 0.3f);
    }

    @NonNull
    private String firstName() {
        String n = new TokenStore(this).userName();
        if (TextUtils.isEmpty(n)) return getString(R.string.home_friend);
        String[] parts = n.trim().split("\\s+");
        // "Mohammad Ramiz" → "Ramiz" as Stitch does when the first name is Mohammad/Muhammad.
        if (parts.length > 1 && parts[0].toLowerCase(Locale.ROOT).matches("m(o|u)h?amm?[ae]d|md\\.?")) return parts[1];
        return parts[0];
    }

    /** "1.8k" above the best bar. */
    @NonNull
    private static String shortNumber(long n) {
        if (n < 1000) return String.valueOf(n);
        double k = n / 1000d;
        return (k >= 100 ? String.format(Locale.US, "%.0fk", k) : String.format(Locale.US, "%.1fk", k)).replace(".0k", "k");
    }

    private void bind(@NonNull Week w, @NonNull Summary s) {
        String who = firstName();
        ((TextView) findViewById(R.id.report_title)).setText(w.current
                ? getString(R.string.report_k_title_now, who)
                : s.total > 0 ? getString(R.string.report_k_title, who) : getString(R.string.report_k_title_quiet, who));
        ((TextView) findViewById(R.id.report_total)).setText(Numbers.format(s.total));

        String rank = rankFor(w, false);
        findViewById(R.id.report_rank).setVisibility(rank == null ? View.GONE : View.VISIBLE);
        ((TextView) findViewById(R.id.report_rank_text)).setText(rank);

        String[] initials = new String[7];
        for (int i = 0; i < 7; i++) initials[i] = s.dates[i].getDayOfWeek().getDisplayName(TextStyle.NARROW, locale());
        ((KDayBarsView) findViewById(R.id.report_bars)).setData(s.days, initials,
                s.best >= 0 ? shortNumber(s.days[s.best]) : null);
        ((TextView) findViewById(R.id.report_avg)).setText(getString(R.string.report_k_avg,
                Numbers.format(Math.round(s.total / (double) s.elapsed))));

        ((TextView) findViewById(R.id.report_best)).setText(s.best < 0 ? "—" : getString(R.string.report_k_best_value,
                s.dates[s.best].getDayOfWeek().getDisplayName(TextStyle.FULL, locale()), Numbers.format(s.days[s.best])));
        TextView streakLabel = findViewById(R.id.report_streak_label);
        TextView streakValue = findViewById(R.id.report_streak);
        if (w.current && streak != null) {
            streakLabel.setText(R.string.report_k_streak);
            streakValue.setText(getResources().getQuantityString(R.plurals.me_k_days, streak, streak));
        } else {
            streakLabel.setText(R.string.report_k_active);
            streakValue.setText(getString(R.string.report_k_active_value, s.active, w.current ? s.elapsed : 7));
        }
        ((TextView) findViewById(R.id.report_most)).setText(s.most != null ? s.most : "—");

        View circles = findViewById(R.id.report_circles_card);
        circles.setVisibility(w.current && !circleRows.isEmpty() ? View.VISIBLE : View.GONE);

        boolean any = s.total > 0;
        findViewById(R.id.report_share).setEnabled(any && !busy);
        findViewById(R.id.report_pdf).setEnabled(any && !busy);
    }

    /** "Top 8% of the ummah" / "#3 in the ummah", or null; short = the story's "Top 8%". */
    @Nullable
    private String rankFor(@NonNull Week w, boolean shortForm) {
        if (w.current) {
            if (rankPct != null) return getString(shortForm ? R.string.report_k_top_short : R.string.report_k_top, rankPct);
            if (rankPlace != null) return getString(R.string.report_k_rank, Numbers.format(rankPlace));
            return null;
        }
        if (w.top != null) {
            for (dto.BoardEntry e : w.top) {
                if (e.isMe && e.rank != null) return getString(R.string.report_k_rank, Numbers.format(e.rank));
            }
        }
        return null;
    }

    // ── This week's extras: streak, place in the ummah, circles ─────────────

    private void loadThisWeekExtras() {
        if (weeks.isEmpty() || !weeks.get(0).current) return;
        ApiClient.get(this).myStats().enqueue(new Callback<dto.MyStats>() {
            @Override
            public void onResponse(@NonNull Call<dto.MyStats> call, @NonNull Response<dto.MyStats> r) {
                if (isFinishing() || !r.isSuccessful() || r.body() == null) return;
                streak = r.body().currentStreak;
                refreshIfCurrent();
            }

            @Override
            public void onFailure(@NonNull Call<dto.MyStats> call, @NonNull Throwable t) { }
        });
        ApiClient.get(this).globalBoard("week", true, 1).enqueue(new Callback<dto.BoardResponse>() {
            @Override
            public void onResponse(@NonNull Call<dto.BoardResponse> call, @NonNull Response<dto.BoardResponse> r) {
                if (isFinishing() || !r.isSuccessful() || r.body() == null) return;
                dto.BoardResponse b = r.body();
                if (b.me == null || b.me.rank == null || b.me.total <= 0) return;
                rankPlace = b.me.rank;
                Integer people = b.memberCount;
                if (people != null && people > 0) {
                    int pct = (int) Math.max(1, Math.ceil(rankPlace * 100d / people));
                    rankPct = pct <= 50 ? pct : null;          // "Top 73%" reads oddly: use the place
                }
                refreshIfCurrent();
            }

            @Override
            public void onFailure(@NonNull Call<dto.BoardResponse> call, @NonNull Throwable t) { }
        });
        ApiClient.get(this).myGroups().enqueue(new Callback<List<dto.MyGroup>>() {
            @Override
            public void onResponse(@NonNull Call<List<dto.MyGroup>> call, @NonNull Response<List<dto.MyGroup>> r) {
                if (isFinishing() || !r.isSuccessful() || r.body() == null) return;
                for (dto.MyGroup g : r.body()) {
                    if (g.isGlobal || g.id == null) continue;
                    loadCircle(g);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<dto.MyGroup>> call, @NonNull Throwable t) { }
        });
    }

    private void loadCircle(@NonNull dto.MyGroup g) {
        ApiClient.get(this).groupBoard(g.id, "week", true, 1).enqueue(new Callback<dto.BoardResponse>() {
            @Override
            public void onResponse(@NonNull Call<dto.BoardResponse> call, @NonNull Response<dto.BoardResponse> r) {
                if (isFinishing() || !r.isSuccessful() || r.body() == null) return;
                addCircleRow(g, r.body());
            }

            @Override
            public void onFailure(@NonNull Call<dto.BoardResponse> call, @NonNull Throwable t) { }
        });
    }

    private void addCircleRow(@NonNull dto.MyGroup g, @NonNull dto.BoardResponse b) {
        LinearLayout list = findViewById(R.id.report_circles);
        View row = LayoutInflater.from(this).inflate(R.layout.item_report_circle, list, false);
        ((TextView) row.findViewById(R.id.rc_name)).setText(g.name);
        int members = b.memberCount != null ? b.memberCount : g.memberCount;
        ((TextView) row.findViewById(R.id.rc_meta)).setText(getString(R.string.report_k_circle_meta,
                in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.groups.Groups.members(this, members), Numbers.format(b.total)));
        TextView rank = row.findViewById(R.id.rc_rank);
        Integer place = b.me != null && b.me.total > 0 ? b.me.rank : null;
        rank.setText(place == null ? "—" : "#" + Numbers.format(place));
        rank.setTextColor(ContextCompat.getColor(this, place != null && place == 1 ? R.color.amber : R.color.text_primary));
        circleRows.put(g.id, row);
        list.addView(row);
        refreshIfCurrent();
    }

    private void refreshIfCurrent() {
        if (weeks.isEmpty() || selected >= weeks.size()) return;
        Week w = weeks.get(selected);
        if (w.current && covered(w)) bind(w, summarize(w));
    }

    // ── Share and PDF ───────────────────────────────────────────────────────

    private void setBusy(boolean b) {
        busy = b;
        findViewById(R.id.report_share).setEnabled(!b);
        findViewById(R.id.report_pdf).setEnabled(!b);
        ((MaterialButton) findViewById(R.id.report_share)).setText(b ? R.string.report_k_preparing : R.string.report_k_share);
    }

    private void snack(int text) {
        Snackbar.make(findViewById(R.id.root), text, Snackbar.LENGTH_LONG).show();
    }

    private void share() {
        if (busy || weeks.isEmpty()) return;
        Week w = weeks.get(selected);
        Summary s = summarize(w);
        setBusy(true);
        try {
            Bitmap bmp = renderStory(w, s);
            File dir = new File(getCacheDir(), "shared");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File file = new File(dir, "kasrat-week-" + w.number + ".png");
            try (FileOutputStream out = new FileOutputStream(file)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            bmp.recycle();
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_TEXT, getString(R.string.week_share_text, w.number, Numbers.format(s.total)))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            setBusy(false);
            startActivity(Intent.createChooser(send, getString(R.string.week_share_image)));
        } catch (Exception e) {
            setBusy(false);
            snack(R.string.week_share_failed);
        }
    }

    /** view_week_story laid out at 390 × 693dp, drawn into 1080 × 1920. */
    @NonNull
    private Bitmap renderStory(@NonNull Week w, @NonNull Summary s) {
        // The shared picture is always the Stitch ivory story, even in the dark theme.
        android.content.res.Configuration cfg = new android.content.res.Configuration(getResources().getConfiguration());
        cfg.uiMode = (cfg.uiMode & ~android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                | android.content.res.Configuration.UI_MODE_NIGHT_NO;
        Context light = new android.view.ContextThemeWrapper(createConfigurationContext(cfg), R.style.Theme_Kasrat);
        View story = LayoutInflater.from(light).inflate(R.layout.view_week_story, null, false);
        ((TextView) story.findViewById(R.id.story_week)).setText(weekLabel(w));
        String fullName = new TokenStore(this).userName();
        TextView name = story.findViewById(R.id.story_name);
        name.setText(fullName);
        name.setVisibility(TextUtils.isEmpty(fullName) ? View.GONE : View.VISIBLE);
        ((TextView) story.findViewById(R.id.story_total)).setText(Numbers.format(s.total));
        List<String> bits = new ArrayList<>();
        String rank = rankFor(w, true);
        if (rank != null) bits.add(rank);
        if (w.current && streak != null && streak > 0) bits.add(getString(R.string.report_k_streak_short, streak));
        else if (s.active > 0) bits.add(getString(R.string.report_k_active_value, s.active, w.current ? s.elapsed : 7));
        TextView pill = story.findViewById(R.id.story_pill);
        pill.setText(TextUtils.join(" · ", bits));
        pill.setVisibility(bits.isEmpty() ? View.GONE : View.VISIBLE);

        float d = getResources().getDisplayMetrics().density;
        int wPx = Math.round(390 * d), hPx = Math.round(693 * d);
        story.measure(View.MeasureSpec.makeMeasureSpec(wPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(hPx, View.MeasureSpec.EXACTLY));
        story.layout(0, 0, wPx, hPx);
        Bitmap bmp = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.scale(1080f / wPx, 1920f / hPx);
        story.draw(c);
        return bmp;
    }

    private void pdf() {
        if (busy || weeks.isEmpty()) return;
        Week w = weeks.get(selected);
        Summary s = summarize(w);
        setBusy(true);
        String title = getString(R.string.week_label_fmt, w.number);
        String period = weekLabel(w);
        Context app = getApplicationContext();
        List<dto.RecitationStat> rows = new ArrayList<>(s.byRecitation);
        int total = (int) Math.min(Integer.MAX_VALUE, s.total);
        new Thread(() -> {
            try {
                Uri uri = WeekReportPdf.generate(app, title, period, total, rows);
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    setBusy(false);
                    snack(R.string.report_saved);
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                    } catch (RuntimeException ignored) {
                        // No PDF viewer: the file is still in Downloads.
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    setBusy(false);
                    snack(R.string.report_failed);
                });
            }
        }).start();
    }
}
