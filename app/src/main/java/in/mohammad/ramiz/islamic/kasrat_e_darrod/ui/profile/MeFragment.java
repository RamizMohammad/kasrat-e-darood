package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.profile;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.cache.ApiCache;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.cache.Prefetch;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.MainActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.Totals;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.avatar.AvatarStudioActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.AuraAvatarView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.KHeatmapView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.notifications.NotificationsActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.settings.SettingsActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.EdgeInsets;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Motion;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Numbers;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Phase 7a · Me (Stitch me_teal). The numbers come from one call, GET /statistics/me, so
 * they always agree; "Member since" from GET /auth/me and the Circles count from
 * GET /groups/mine. The last answers are kept on the phone, so the tab opens with your
 * numbers at once and refreshes behind them; a log or undo elsewhere marks them stale.
 * Tapping the avatar opens the avatar studio.
 */
public class MeFragment extends Fragment implements MainActivity.NavSpaceAware {

    static final String PREFS = "kasrat_me";
    private static final String KEY_STATS = "stats", KEY_SINCE = "since", KEY_CIRCLES = "circles";

    private View root;
    private SwipeRefreshLayout refresh;
    @Nullable private dto.MyStats stats;
    private int loadedAtVersion = -1;

    private final ActivityResultLauncher<Intent> avatarStudio = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != android.app.Activity.RESULT_OK || result.getData() == null) return;
                String spec = result.getData().getStringExtra(AvatarStudioActivity.EXTRA_SPEC);
                if (spec != null) saveAvatar(spec);
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_me, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        root = view;
        EdgeInsets.padTop(view.findViewById(R.id.me_hero));
        refresh = view.findViewById(R.id.me_refresh);
        refresh.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.amber));
        refresh.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(requireContext(), R.color.surface_lowest));
        refresh.setOnRefreshListener(this::load);
        view.findViewById(R.id.me_retry).setOnClickListener(v -> load());
        view.findViewById(R.id.me_avatar).setOnClickListener(v -> openAvatarStudio());
        if (getActivity() instanceof MainActivity) onNavSpace(((MainActivity) getActivity()).navSpace());

        bindIdentity();
        bindMenu();
        bindCircles(prefs().getInt(KEY_CIRCLES + ":" + uid(), -1));
        dto.MyStats cached = readCache();
        if (cached != null) bind(cached);
        else bindWeek(null);
        if (savedInstanceState == null) Motion.enterStack(view.findViewById(R.id.me_column));
    }

    @Override
    public void onNavSpace(int px) {
        View scroll = root == null ? null : root.findViewById(R.id.me_scroll);
        if (scroll == null) return;
        scroll.setPadding(scroll.getPaddingLeft(), scroll.getPaddingTop(), scroll.getPaddingRight(),
                px + (int) (16 * getResources().getDisplayMetrics().density));
    }

    @Override
    public void onResume() {
        super.onResume();
        bindIdentity();
        // Reload when anything was logged or undone since we last loaded.
        if (loadedAtVersion != Totals.version()) load();
    }

    // ── Loading ─────────────────────────────────────────────────────────────

    private void load() {
        int version = Totals.version();
        ApiClient.get(requireContext()).myStats().enqueue(new Callback<dto.MyStats>() {
            @Override
            public void onResponse(@NonNull Call<dto.MyStats> call, @NonNull Response<dto.MyStats> r) {
                if (!isAdded()) return;
                refresh.setRefreshing(false);
                if (r.isSuccessful() && r.body() != null) {
                    loadedAtVersion = version;
                    root.findViewById(R.id.me_error).setVisibility(View.GONE);
                    writeCache(r.body());
                    bind(r.body());
                } else if (stats == null) {
                    root.findViewById(R.id.me_error).setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(@NonNull Call<dto.MyStats> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                refresh.setRefreshing(false);
                if (stats == null) root.findViewById(R.id.me_error).setVisibility(View.VISIBLE);
            }
        });
        ApiClient.get(requireContext()).myGroups().enqueue(new Callback<List<dto.MyGroup>>() {
            @Override
            public void onResponse(@NonNull Call<List<dto.MyGroup>> call, @NonNull Response<List<dto.MyGroup>> r) {
                if (!isAdded() || !r.isSuccessful() || r.body() == null) return;
                ApiCache.put(requireContext(), Prefetch.MY_GROUPS, r.body());
                int n = 0;
                for (dto.MyGroup g : r.body()) if (!g.isGlobal) n++;
                prefs().edit().putInt(KEY_CIRCLES + ":" + uid(), n).apply();
                bindCircles(n);
            }

            @Override
            public void onFailure(@NonNull Call<List<dto.MyGroup>> call, @NonNull Throwable t) { }
        });
        if (prefs().getString(KEY_SINCE + ":" + uid(), null) == null) {
            ApiClient.get(requireContext()).me().enqueue(new Callback<dto.UserDto>() {
                @Override
                public void onResponse(@NonNull Call<dto.UserDto> call, @NonNull Response<dto.UserDto> r) {
                    if (!isAdded() || !r.isSuccessful() || r.body() == null || r.body().createdAt == null) return;
                    prefs().edit().putString(KEY_SINCE + ":" + uid(), r.body().createdAt).apply();
                    bindIdentity();
                }

                @Override
                public void onFailure(@NonNull Call<dto.UserDto> call, @NonNull Throwable t) { }
            });
        }
    }

    // ── Binding ─────────────────────────────────────────────────────────────

    private void bind(@NonNull dto.MyStats s) {
        stats = s;
        TextView streak = root.findViewById(R.id.me_streak);
        streak.setText(getResources().getQuantityString(R.plurals.me_k_days, s.currentStreak, s.currentStreak));
        TextView label = root.findViewById(R.id.me_streak_label);
        label.setText(s.currentStreak > 0 ? R.string.me_k_active_streak : R.string.me_k_start_streak);
        label.setTextColor(ContextCompat.getColor(requireContext(), s.currentStreak > 0 ? R.color.amber : R.color.text_muted));
        ((TextView) root.findViewById(R.id.me_lifetime)).setText(Numbers.format(s.lifetime));
        ((TextView) root.findViewById(R.id.me_week_total)).setText(Numbers.format(s.week));
        ((TextView) root.findViewById(R.id.me_best)).setText(Numbers.format(Math.max(s.bestStreak, s.currentStreak)));
        bindWeek(s.days);
        ((KHeatmapView) root.findViewById(R.id.me_heatmap)).setDays(s.days);
    }

    private void bindCircles(int n) {
        ((TextView) root.findViewById(R.id.me_circles)).setText(n < 0 ? "—" : Numbers.format(n));
    }

    /** M T W T F S S: teal where you logged, today ringed, the rest ivory. */
    private void bindWeek(@Nullable List<dto.DayTotal> days) {
        LinearLayout week = root.findViewById(R.id.me_week);
        week.removeAllViews();
        Set<LocalDate> logged = new HashSet<>();
        if (days != null) {
            for (dto.DayTotal d : days) {
                try {
                    if (d.total > 0) logged.add(LocalDate.parse(d.date));
                } catch (RuntimeException ignored) { }
            }
        }
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Locale loc = getResources().getConfiguration().getLocales().get(0);
        int teal = ContextCompat.getColor(requireContext(), R.color.amber);
        int grey = ContextCompat.getColor(requireContext(), R.color.text_muted);
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < 7; i++) {
            LocalDate day = monday.plusDays(i);
            boolean isToday = day.equals(today), done = logged.contains(day);
            LinearLayout col = new LinearLayout(requireContext());
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            View dot = new View(requireContext());
            dot.setBackgroundResource(isToday ? (done ? R.drawable.bg_k_wdot_today : R.drawable.bg_k_wdot_today_empty)
                    : done ? R.drawable.bg_k_wdot_done : R.drawable.bg_k_wdot_empty);
            col.addView(dot, new LinearLayout.LayoutParams((int) (22 * d), (int) (22 * d)));
            TextView t = new TextView(requireContext());
            t.setTextAppearance(R.style.TextAppearance_K_LabelSm);
            t.setTextColor(isToday ? teal : grey);
            String full = day.getDayOfWeek().getDisplayName(TextStyle.FULL, loc);
            t.setText(day.getDayOfWeek().getDisplayName(TextStyle.NARROW, loc));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            tl.topMargin = (int) (4 * d);
            col.addView(t, tl);
            col.setContentDescription(getString(isToday ? (done ? R.string.me_k_day_today_done : R.string.me_k_day_today)
                    : done ? R.string.me_k_day_done : day.isAfter(today) ? R.string.me_k_day_upcoming : R.string.me_k_day_missed, full));
            week.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    private void bindIdentity() {
        if (root == null) return;
        TokenStore store = new TokenStore(requireContext());
        String name = store.userName();
        ((TextView) root.findViewById(R.id.me_name)).setText(
                TextUtils.isEmpty(name) ? getString(R.string.home_friend) : name);
        ((AuraAvatarView) root.findViewById(R.id.me_avatar))
                .setAvatar(store.avatar() != null ? store.avatar() : store.photoUrl());
        TextView since = root.findViewById(R.id.me_since);
        String iso = prefs().getString(KEY_SINCE + ":" + uid(), null);
        String text = null;
        if (iso != null) {
            try {
                Locale loc = getResources().getConfiguration().getLocales().get(0);
                text = getString(R.string.me_k_since, OffsetDateTime.parse(iso)
                        .format(DateTimeFormatter.ofPattern("MMM yyyy", loc)));
            } catch (RuntimeException ignored) { }
        }
        since.setText(text);
        since.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    // ── Menu ────────────────────────────────────────────────────────────────

    private void bindMenu() {
        LinearLayout menu = root.findViewById(R.id.me_menu);
        menu.removeAllViews();
        addRow(menu, R.drawable.ic_ph_clock_counter_clockwise, R.string.me_k_history,
                v -> startActivity(new Intent(requireContext(), SubmissionsActivity.class)));
        addRow(menu, R.drawable.ic_ph_chart_line_up, R.string.me_k_reports,
                v -> startActivity(new Intent(requireContext(), WeekReportActivity.class)));
        addRow(menu, R.drawable.ic_ph_bell, R.string.me_k_notifications,
                v -> startActivity(new Intent(requireContext(), NotificationsActivity.class)));
        addRow(menu, R.drawable.ic_ph_gear, R.string.me_k_settings,
                v -> startActivity(new Intent(requireContext(), SettingsActivity.class)));
    }

    private void addRow(@NonNull LinearLayout parent, @DrawableRes int icon, @StringRes int label,
                        @NonNull View.OnClickListener click) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_k_icon_row, parent, false);
        ((ImageView) row.findViewById(R.id.row_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.row_label)).setText(label);
        row.setOnClickListener(click);
        parent.addView(row);
    }

    private void openAvatarStudio() {
        avatarStudio.launch(AvatarStudioActivity.intent(requireContext(),
                new TokenStore(requireContext()).avatar(), false));
    }

    private void saveAvatar(@NonNull String spec) {
        Context app = requireContext().getApplicationContext();
        ApiClient.get(app).updateMe(dto.UserUpdateRequest.avatar(spec)).enqueue(new Callback<dto.UserDto>() {
            @Override
            public void onResponse(@NonNull Call<dto.UserDto> call, @NonNull Response<dto.UserDto> r) {
                if (r.isSuccessful()) {
                    new TokenStore(app).setAvatar(spec);
                    if (isAdded()) bindIdentity();
                } else if (isAdded()) {
                    snack(R.string.onb_avatar_failed);
                }
            }

            @Override
            public void onFailure(@NonNull Call<dto.UserDto> call, @NonNull Throwable t) {
                if (isAdded()) snack(R.string.onb_avatar_failed);
            }
        });
    }

    private void snack(@StringRes int text) {
        Snackbar s = Snackbar.make(root, text, Snackbar.LENGTH_LONG);
        View bar = requireActivity().findViewById(R.id.bottom_bar);
        if (bar != null) s.setAnchorView(bar);
        s.show();
    }

    // ── Saved copy ──────────────────────────────────────────────────────────

    @NonNull
    private SharedPreferences prefs() {
        return requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Nullable
    private String uid() {
        return new TokenStore(requireContext()).userId();
    }

    @Nullable
    private dto.MyStats readCache() {
        return ApiCache.get(requireContext(), Prefetch.MY_STATS, dto.MyStats.class);
    }

    private void writeCache(@NonNull dto.MyStats s) {
        ApiCache.put(requireContext(), Prefetch.MY_STATS, s);
    }
}
