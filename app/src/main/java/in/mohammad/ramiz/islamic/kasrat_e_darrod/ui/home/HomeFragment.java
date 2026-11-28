package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.home;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.cache.Cached;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.cache.Prefetch;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.MainActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.RecitationStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.Totals;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.aladhan.AlAdhanClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.aladhan.AlAdhanDto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.AuraAvatarView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.ChoiceSheet;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.CountRollTextView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.GroupAvatarView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.KRingView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.PrayerTimelineView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.groups.GroupActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.library.TasbihActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Dates;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Motion;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Numbers;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.AppPrefs;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.LocationHelper;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.Skeleton;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Home (Stitch home_teal): date + greeting with streak and avatar, the hero card
 * (today's count, change vs yesterday, daily-intention ring), the verse card, the
 * prayer timeline, "Your circles" and "Continue reciting".
 *
 * Totals come from /dashboard and from {@link Totals}, which every log publishes,
 * so today's number rolls wherever you logged. "vs yesterday" is worked out from
 * your recent submissions, and only shown when they reach back past yesterday.
 * Prayer times come from AlAdhan for the phone's location; today's copy is kept so
 * the timeline shows at once next time. The daily intention is a phone-only goal.
 */
public class HomeFragment extends Fragment implements Totals.Listener, MainActivity.NavSpaceAware {

    private static final String PRAYER_PREFS = "kasrat_prayer";
    private static final String TASBIH_PREFS = "kasrat_tasbih";
    private static final int[] INTENTIONS = {100, 313, 500, 1000, 1500, 3000, 5000};

    private View root;
    private SwipeRefreshLayout refresh;
    private CountRollTextView today;
    private KRingView ring;
    private TextView intentionText, trend;
    private long todayCount = -1;
    /** Yesterday's total from recent submissions, or -1 when they don't reach back that far. */
    private long yesterdayCount = -1;
    @Nullable private String continueId;
    private int pending;
    private String lang;

    private final ActivityResultLauncher<String[]> locationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> loadPrayers());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        root = view;
        lang = new AppPrefs(requireContext()).lang();
        refresh = view.findViewById(R.id.home_refresh);
        refresh.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.amber));
        refresh.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(requireContext(), R.color.surface_raised));
        refresh.setOnRefreshListener(() -> { forceNext = true; loadAll(); });
        in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.EdgeInsets.padTop(view.findViewById(R.id.home_header));

        TokenStore store = new TokenStore(requireContext());
        String name = store.userName();
        ((TextView) view.findViewById(R.id.home_name)).setText(getString(R.string.home_greeting,
                name != null && !name.isEmpty() ? firstName(name) : getString(R.string.home_friend)));
        AuraAvatarView avatar = view.findViewById(R.id.home_avatar);
        avatar.setAvatar(store.avatar() != null ? store.avatar() : store.photoUrl());
        avatar.setOnClickListener(v -> openTab(MainActivity.TAB_ME));
        view.findViewById(R.id.home_streak).setOnClickListener(v -> openTab(MainActivity.TAB_ME));

        view.findViewById(R.id.home_totals_retry).setOnClickListener(v -> { pending++; loadDashboard(true); });
        view.findViewById(R.id.home_circles_manage).setOnClickListener(v -> openTab(MainActivity.TAB_GROUPS));
        view.findViewById(R.id.home_circles_empty_action).setOnClickListener(v -> openTab(MainActivity.TAB_GROUPS));
        view.findViewById(R.id.home_open_tasbih).setOnClickListener(v -> {
            if (continueId != null) startActivity(TasbihActivity.intent(requireContext(), continueId));
            else openTab(MainActivity.TAB_RECITE);
        });

        today = view.findViewById(R.id.home_today);
        ring = view.findViewById(R.id.home_ring);
        intentionText = view.findViewById(R.id.home_intention);
        trend = view.findViewById(R.id.home_trend);
        view.findViewById(R.id.home_intention_box).setOnClickListener(v -> pickIntention());
        bindIntention();

        bindDates();

        // Start from what Home showed last time, then roll to anything newer.
        Totals.Snapshot shown = Totals.shown();
        if (shown != null) showTotals(shown, false);
        Totals.Snapshot latest = Totals.latest();
        if (latest != null && latest != shown) view.post(() -> showTotals(latest, true));

        if (getActivity() instanceof MainActivity) onNavSpace(((MainActivity) getActivity()).navSpace());

        bindCachedPrayers();
        loadAll();
        if (savedInstanceState == null) Motion.enterStack(view.findViewById(R.id.home_content));
    }

    /** Room for the floating bottom bar under the last card. */
    @Override
    public void onNavSpace(int px) {
        if (root == null) return;
        NestedScrollView sv = root.findViewById(R.id.home_scroll);
        sv.setPadding(0, 0, 0, px);
    }

    @Override
    public void onStart() {
        super.onStart();
        Totals.addListener(this);
        if (root != null) {
            TokenStore store = new TokenStore(requireContext());
            ((AuraAvatarView) root.findViewById(R.id.home_avatar))
                    .setAvatar(store.avatar() != null ? store.avatar() : store.photoUrl());
            bindContinue();                                 // the tasbih count may have moved
        }
    }

    @Override
    public void onStop() {
        Totals.removeListener(this);
        super.onStop();
    }

    @Override
    public void onTotals(@NonNull Totals.Snapshot s) {
        if (root != null) showTotals(s, true);
    }

    /** Pull to refresh: skip the "fresh enough" shortcut once. */
    private boolean forceNext;

    private void loadAll() {
        pending = 3;
        boolean force = forceNext;
        forceNext = false;
        loadDashboard(force);
        loadRecent(force);
        loadPrayers();
    }

    private void doneOne() {
        if (--pending <= 0 && refresh != null) refresh.setRefreshing(false);
    }

    // ── Totals + hero ───────────────────────────────────────────────────────

    /**
     * The saved dashboard paints at once (totals, circles); the server's copy
     * follows unless the saved one is only seconds old (Cached.FRESH_SHORT).
     */
    private void loadDashboard(boolean force) {
        final int[] version = {Totals.version()};
        Cached.load(getViewLifecycleOwner(), requireContext(), Prefetch.DASHBOARD,
                dto.DashboardResponse.class, Cached.FRESH_SHORT, force,
                () -> ApiClient.get(requireContext()).dashboard(null),
                new Cached.Result<dto.DashboardResponse>() {
                    @Override
                    public void onData(@NonNull dto.DashboardResponse d, boolean fromCache) {
                        if (!isAdded()) return;
                        if (fromCache) {
                            // Only fill in when nothing newer is in memory (a log this session).
                            if (Totals.latest() == null) {
                                Totals.publish(d);
                                version[0] = Totals.version();
                                // Not yet a Totals listener (that's onStart): paint directly.
                                Totals.Snapshot s = Totals.latest();
                                if (s != null) showTotals(s, false);
                            }
                        } else {
                            totalsError(false);
                            // A log that finished while this was loading is newer: keep it.
                            if (Totals.version() == version[0]) Totals.publish(d);
                        }
                        bindCircles(d.groupsThisWeek);
                    }

                    @Override
                    public void onError(boolean hadCache, boolean offline) {
                        if (isAdded()) totalsError(!hadCache && Totals.latest() == null);
                    }

                    @Override
                    public void onDone() {
                        if (isAdded()) doneOne();
                    }
                });
    }

    private void totalsError(boolean show) {
        root.findViewById(R.id.home_totals_error).setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showTotals(@NonNull Totals.Snapshot s, boolean animate) {
        today.setCount(s.today, animate && today.hasCount());
        todayCount = s.today;
        showStreak(s.streak);
        bindIntention();
        bindTrend();
        Totals.markShown(s);
    }

    private void showStreak(long streak) {
        TextView pill = root.findViewById(R.id.home_streak);
        pill.setVisibility(streak > 0 ? View.VISIBLE : View.GONE);
        pill.setText(Numbers.format(streak));
        startIcon(pill, R.drawable.ic_ph_flame, 16, ContextCompat.getColor(requireContext(), R.color.amber));
        pill.setContentDescription(getString(R.string.home_streak_cd, (int) streak));
    }

    private void bindIntention() {
        int goal = new AppPrefs(requireContext()).dailyIntention();
        intentionText.setText(Numbers.format(goal));
        long done = Math.max(0, todayCount);
        ring.setProgress(goal > 0 ? (float) done / goal : 0f);
        root.findViewById(R.id.home_intention_box).setContentDescription(
                getString(R.string.home_intention_cd, Numbers.format(goal), Numbers.format(done)));
    }

    private void pickIntention() {
        Haptics.tick(requireContext());
        AppPrefs prefs = new AppPrefs(requireContext());
        int current = prefs.dailyIntention();
        ChoiceSheet sheet = ChoiceSheet.with(requireContext())
                .title(R.string.home_intention_title)
                .subtitle(R.string.home_intention_sub);
        for (int n : INTENTIONS) {
            sheet.option(R.drawable.ic_ph_heart, Numbers.format(n), null, n == current, () -> {
                prefs.setDailyIntention(n);
                bindIntention();
            });
        }
        sheet.show();
    }

    /** "+18% vs yesterday" once both days are known (and yesterday wasn't zero). */
    private void bindTrend() {
        if (todayCount < 0 || yesterdayCount <= 0) {
            trend.setVisibility(View.GONE);
            return;
        }
        long pct = Math.round((todayCount - yesterdayCount) * 100.0 / yesterdayCount);
        trend.setVisibility(View.VISIBLE);
        int icon;
        if (pct > 0) {
            trend.setText(getString(R.string.home_vs_yesterday_up, (int) Math.min(999, pct)));
            icon = R.drawable.ic_ph_trend_up;
        } else if (pct < 0) {
            trend.setText(getString(R.string.home_vs_yesterday_down, (int) Math.min(999, -pct)));
            icon = R.drawable.ic_trend_down;
        } else {
            trend.setText(R.string.home_vs_yesterday_same);
            icon = R.drawable.ic_ph_trend_up;
        }
        int color = ContextCompat.getColor(requireContext(), pct < 0 ? R.color.brick : R.color.amber);
        trend.setTextColor(color);
        startIcon(trend, icon, 14, color);
    }

    /** A compound start icon at {@code sizeDp}, tinted (Stitch: 14–16dp Phosphor next to a label). */
    private void startIcon(@NonNull TextView v, int icon, int sizeDp, int color) {
        android.graphics.drawable.Drawable d = ContextCompat.getDrawable(requireContext(), icon);
        if (d == null) return;
        d = d.mutate();
        int px = (int) (sizeDp * getResources().getDisplayMetrics().density);
        d.setBounds(0, 0, px, px);
        d.setTint(color);
        v.setCompoundDrawablesRelative(d, null, null, null);
    }

    /** "Mohammad Ramiz" → "Mohammad" for the greeting. */
    @NonNull
    private static String firstName(@NonNull String name) {
        String t = name.trim();
        int sp = t.indexOf(' ');
        return sp > 0 ? t.substring(0, sp) : t;
    }

    // ── Your circles ────────────────────────────────────────────────────────

    private void bindCircles(@Nullable List<dto.GroupThisWeek> groups) {
        List<String> names = new ArrayList<>();
        if (groups != null) for (dto.GroupThisWeek g : groups) if (g.name != null) names.add(g.name);
        in.mohammad.ramiz.islamic.kasrat_e_darrod.data.CircleNames.set(names);
        LinearLayout row = root.findViewById(R.id.home_groups_row);
        row.removeAllViews();
        boolean empty = groups == null || groups.isEmpty();
        root.findViewById(R.id.home_circles_scroll).setVisibility(empty ? View.GONE : View.VISIBLE);
        root.findViewById(R.id.home_circles_empty).setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) return;
        LayoutInflater inf = LayoutInflater.from(requireContext());
        boolean single = groups.size() == 1;
        // One circle fills the row; several scroll sideways at a fixed width.
        row.getLayoutParams().width = single ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT;
        row.setLayoutParams(row.getLayoutParams());
        android.widget.HorizontalScrollView scroll = root.findViewById(R.id.home_circles_scroll);
        scroll.setFillViewport(single);
        for (int i = 0; i < groups.size(); i++) {
            dto.GroupThisWeek g = groups.get(i);
            View card = inf.inflate(R.layout.item_home_circle, row, false);
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) card.getLayoutParams();
            if (single) lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            if (i == groups.size() - 1) lp.setMarginEnd(0);
            ((TextView) card.findViewById(R.id.circle_name)).setText(g.name);
            ((GroupAvatarView) card.findViewById(R.id.circle_avatar)).setGroup(g.avatar, g.id, false);
            ((TextView) card.findViewById(R.id.circle_members)).setText(getResources()
                    .getQuantityString(R.plurals.home_members, g.memberCount, g.memberCount));
            ((TextView) card.findViewById(R.id.circle_total)).setText(Numbers.format(g.weekTotal));
            TextView progress = card.findViewById(R.id.circle_progress_text);
            TextView pct = card.findViewById(R.id.circle_pct);
            in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.KTickBarView bar = card.findViewById(R.id.circle_bar);
            boolean hasGoal = g.weeklyGoal != null && g.weeklyGoal > 0;
            pct.setVisibility(hasGoal ? View.VISIBLE : View.GONE);
            bar.setVisibility(hasGoal ? View.VISIBLE : View.GONE);
            if (hasGoal) {
                progress.setText(getString(R.string.home_circle_of_week, Numbers.format(g.weeklyGoal)));
                int p = (int) Math.min(100, g.weekTotal * 100 / g.weeklyGoal);
                pct.setText(getString(R.string.home_circle_pct, p));
                bar.setFraction(g.weekTotal / (float) g.weeklyGoal);
            } else {
                progress.setText(R.string.home_circle_this_week);
            }
            card.setOnClickListener(v -> startActivity(GroupActivity.intent(requireContext(), g.id)));
            row.addView(card);
        }
    }

    // ── Recent submissions: yesterday's total + what to continue ───────────

    private void loadRecent(boolean force) {
        Cached.load(getViewLifecycleOwner(), requireContext(), Prefetch.RECENT,
                new com.google.gson.reflect.TypeToken<List<dto.MySubmissionDto>>() { }.getType(),
                Cached.FRESH_SHORT, force,
                () -> ApiClient.get(requireContext()).recentSubmissions(100),
                new Cached.Result<List<dto.MySubmissionDto>>() {
            @Override
            public void onDone() {
                if (isAdded()) doneOne();
            }

            @Override
            public void onData(@NonNull List<dto.MySubmissionDto> subs, boolean fromCache) {
                if (!isAdded()) return;
                yesterdayCount = yesterdayTotal(subs);
                bindTrend();
                List<String> top = mostLogged(subs);
                RecitationStore.get(requireContext()).load(new RecitationStore.Callback() {
                    @Override public void onList(@NonNull List<dto.RecitationDto> list, boolean fromCache) {
                        if (!isAdded()) return;
                        if (continueId == null) {
                            continueId = !top.isEmpty() ? top.get(0) : !list.isEmpty() ? list.get(0).id : null;
                        }
                        bindContinue();
                    }

                    @Override public void onError(boolean offline) { }
                });
            }
        });
    }

    /** Sum of yesterday's submissions, or -1 if the list doesn't reach back before yesterday. */
    private static long yesterdayTotal(@NonNull List<dto.MySubmissionDto> subs) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long todayStart = c.getTimeInMillis();
        c.add(Calendar.DAY_OF_MONTH, -1);
        long yStart = c.getTimeInMillis();
        long sum = 0, oldest = Long.MAX_VALUE;
        for (dto.MySubmissionDto s : subs) {
            long t = parseTime(s.createdAt);
            if (t <= 0) continue;
            oldest = Math.min(oldest, t);
            if (t >= yStart && t < todayStart) sum += s.count;
        }
        // Fewer than a full page means we have everything; otherwise we need to see past yesterday.
        boolean complete = subs.size() < 100 || oldest < yStart;
        return complete ? sum : -1;
    }

    private static long parseTime(@Nullable String iso) {
        if (iso == null) return -1;
        String[] patterns = {"yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSSSS", "yyyy-MM-dd'T'HH:mm:ss"};
        for (String p : patterns) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(p, Locale.US);
                if (!p.endsWith("XXX")) f.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date d = f.parse(iso);
                if (d != null) return d.getTime();
            } catch (ParseException ignored) {
                // try the next shape
            }
        }
        return -1;
    }

    @NonNull
    private static List<String> mostLogged(@NonNull List<dto.MySubmissionDto> subs) {
        Map<String, Integer> times = new HashMap<>();
        Map<String, Integer> firstSeen = new HashMap<>();
        for (int i = 0; i < subs.size(); i++) {
            String id = subs.get(i).recitationId;
            if (id == null) continue;
            times.merge(id, 1, Integer::sum);
            firstSeen.putIfAbsent(id, i);
        }
        List<String> ids = new ArrayList<>(times.keySet());
        // Most logged first; ties go to the one logged most recently.
        Collections.sort(ids, (a, b) -> {
            int c = Integer.compare(times.get(b), times.get(a));
            return c != 0 ? c : Integer.compare(firstSeen.get(a), firstSeen.get(b));
        });
        return ids;
    }

    /** "Continue reciting": your most-logged recitation and where its tasbih stands. */
    private void bindContinue() {
        View card = root.findViewById(R.id.home_continue);
        dto.RecitationDto r = continueId != null ? RecitationStore.get(requireContext()).byId(continueId) : null;
        if (r == null) {
            card.setVisibility(View.GONE);
            return;
        }
        card.setVisibility(View.VISIBLE);
        ((TextView) root.findViewById(R.id.home_continue_title)).setText(RecitationStore.title(r, lang));
        ((TextView) root.findViewById(R.id.home_continue_ar)).setText(r.arabicName);
        SharedPreferences t = requireContext().getSharedPreferences(TASBIH_PREFS, Context.MODE_PRIVATE);
        int count = t.getInt(r.id + ":count", 0);
        int target = t.getInt(r.id + ":target", 33);
        ((TextView) root.findViewById(R.id.home_continue_progress)).setText(getString(
                R.string.home_continue_progress, Numbers.format(count), Numbers.format(target)));
    }

    // ── Dates ───────────────────────────────────────────────────────────────

    private void bindDates() {
        Date now = new Date();
        ((TextView) root.findViewById(R.id.home_date)).setText(getString(R.string.home_date_line,
                Dates.gregorianShort(now), Dates.hijri(now)));
        TextView ramadan = root.findViewById(R.id.home_ramadan);
        if (Dates.hijriMonth(now) == 9) {
            ramadan.setVisibility(View.VISIBLE);
            ramadan.setText(getString(R.string.home_ramadan_day, Dates.hijriDay(now)));
        } else {
            ramadan.setVisibility(View.GONE);
        }
    }

    // ── Prayer times ────────────────────────────────────────────────────────

    private static final int[] PRAYER_NAMES = {
            R.string.prayer_fajr, R.string.prayer_dhuhr, R.string.prayer_asr,
            R.string.prayer_maghrib, R.string.prayer_isha};

    private SharedPreferences prayerPrefs() {
        return requireContext().getSharedPreferences(PRAYER_PREFS, Context.MODE_PRIVATE);
    }

    /** Today + the chosen method/school: changing either in Settings refetches. */
    private String todayKey() {
        AppPrefs app = new AppPrefs(requireContext());
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date())
                + "|" + app.prayerMethod() + "|" + app.prayerSchool();
    }

    /** Today's saved times, if any, so the card appears at once. */
    private void bindCachedPrayers() {
        SharedPreferences p = prayerPrefs();
        String times = p.getString("times", null);
        if (todayKey().equals(p.getString("day", null)) && times != null) {
            showPrayers(times.split(","), p.getString("zone", null));
        } else {
            Skeleton.shimmer(root.findViewById(R.id.prayer_skeleton));
        }
    }

    private void loadPrayers() {
        if (!isAdded()) return;
        if (!LocationHelper.hasPermission(requireContext())) {
            doneOne();
            if (!hasPrayersToday()) {
                prayerState(R.string.prayer_need_location_title, R.string.prayer_need_location_body,
                        R.string.prayer_allow_location, v -> askLocation());
            }
            return;
        }
        LocationHelper.current(requireContext(), latLon -> {
            if (!isAdded()) return;
            if (latLon == null) {
                doneOne();
                if (!hasPrayersToday()) {
                    prayerState(R.string.prayer_no_fix_title, R.string.prayer_no_fix_body,
                            R.string.action_try_again, v -> {
                                pending = 1;
                                loadPrayers();
                            });
                }
                return;
            }
            fetchPrayers(latLon);
        });
    }

    private boolean hasPrayersToday() {
        return todayKey().equals(prayerPrefs().getString("day", null));
    }

    private void askLocation() {
        boolean canAsk = shouldShowRequestPermissionRationale(android.Manifest.permission.ACCESS_FINE_LOCATION)
                || !prayerPrefs().getBoolean("asked", false);
        if (canAsk) {
            prayerPrefs().edit().putBoolean("asked", true).apply();
            locationPermission.launch(new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION});
        } else {
            // "Don't ask again" was chosen: only Settings can turn it on now.
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", requireContext().getPackageName(), null)));
        }
    }

    private void fetchPrayers(@NonNull double[] latLon) {
        String date = new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(new Date());
        AppPrefs app = new AppPrefs(requireContext());
        AlAdhanClient.get().timings(date, latLon[0], latLon[1], app.prayerMethod(), app.prayerSchool())
                .enqueue(new Callback<AlAdhanDto.Response>() {
                    @Override
                    public void onResponse(@NonNull Call<AlAdhanDto.Response> call,
                                           @NonNull Response<AlAdhanDto.Response> r) {
                        if (!isAdded()) return;
                        doneOne();
                        AlAdhanDto.Data d = r.isSuccessful() && r.body() != null ? r.body().data : null;
                        if (d == null || d.timings == null) {
                            prayersFailed();
                            return;
                        }
                        AlAdhanDto.Timings t = d.timings;
                        String[] times = {clean(t.fajr), clean(t.dhuhr), clean(t.asr), clean(t.maghrib), clean(t.isha)};
                        String zone = d.meta != null ? d.meta.timezone : null;
                        prayerPrefs().edit()
                                .putString("day", todayKey())
                                .putString("times", String.join(",", times))
                                .putString("zone", zone)
                                .apply();
                        showPrayers(times, zone);
                    }

                    @Override
                    public void onFailure(@NonNull Call<AlAdhanDto.Response> call, @NonNull Throwable t) {
                        if (!isAdded()) return;
                        doneOne();
                        prayersFailed();
                    }
                });
    }

    private void prayersFailed() {
        if (hasPrayersToday()) return;                     // keep today's saved times
        prayerState(R.string.prayer_failed_title, R.string.prayer_failed_body,
                R.string.action_try_again, v -> {
                    pending = 1;
                    loadPrayers();
                });
    }

    private void prayerState(int title, int body, int action, View.OnClickListener onAction) {
        Skeleton.reveal(root.findViewById(R.id.prayer_skeleton), null);
        root.findViewById(R.id.prayer_card).setVisibility(View.GONE);
        root.findViewById(R.id.prayer_state).setVisibility(View.VISIBLE);
        ((TextView) root.findViewById(R.id.prayer_state_title)).setText(title);
        ((TextView) root.findViewById(R.id.prayer_state_body)).setText(body);
        MaterialButton btn = root.findViewById(R.id.prayer_state_action);
        btn.setText(action);
        btn.setOnClickListener(onAction);
    }

    private void showPrayers(@NonNull String[] times, @Nullable String zone) {
        if (times.length != 5) return;
        root.findViewById(R.id.prayer_state).setVisibility(View.GONE);
        Skeleton.reveal(root.findViewById(R.id.prayer_skeleton), root.findViewById(R.id.prayer_card));

        Calendar now = Calendar.getInstance();
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int next = -1;
        for (int i = 0; i < 5; i++) {
            int m = minutes(times[i]);
            if (m >= 0 && m > nowMin) {
                next = i;
                break;
            }
        }
        boolean tomorrow = next < 0;          // after Isha: next is tomorrow's Fajr
        int nextIdx = tomorrow ? 0 : next;

        int target = minutes(times[nextIdx]) + (tomorrow ? 24 * 60 : 0);
        int left = Math.max(0, target - nowMin);
        String leftText = left >= 60
                ? getString(R.string.prayer_in_hm, left / 60, left % 60)
                : getString(R.string.prayer_in_m, left);

        String[] names = new String[5];
        for (int i = 0; i < 5; i++) names[i] = getString(PRAYER_NAMES[i]);
        ((PrayerTimelineView) root.findViewById(R.id.prayer_card))
                .setPrayers(names, times, nextIdx, tomorrow, leftText);
    }

    /** "HH:mm" → minutes after midnight, or -1. */
    private static int minutes(@Nullable String hhmm) {
        if (hhmm == null) return -1;
        String[] p = hhmm.split(":");
        if (p.length < 2) return -1;
        try {
            return Integer.parseInt(p[0].trim()) * 60 + Integer.parseInt(p[1].trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** "06:03 (IST)" → "06:03". */
    @NonNull
    private static String clean(@Nullable String time) {
        if (time == null) return "--:--";
        int space = time.indexOf(' ');
        return space > 0 ? time.substring(0, space) : time;
    }

    private void openTab(int tab) {
        if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).openTab(tab);
    }
}
