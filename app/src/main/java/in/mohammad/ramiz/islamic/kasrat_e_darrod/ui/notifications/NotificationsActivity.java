package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.notifications;

import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.base.BaseActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.profile.WeekReportActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.EdgeInsets;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.AppPrefs;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.Skeleton;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Phase 7c · Notifications (Stitch notifications_teal). Your notifications and closed weeks
 * (GET /notifications?category=notifications and =considerations), newest first, in Today and
 * Earlier. Read state lives on the phone: anything newer than the last one you saw has the teal
 * dot until you tap "Mark all read" or leave the screen. A closed week opens its report.
 */
public class NotificationsActivity extends BaseActivity {

    private static final List<String> CATEGORIES = Arrays.asList("notifications", "considerations");

    private RecyclerView list;
    private SwipeRefreshLayout refresh;
    private View skeleton, state, markAll;
    private final List<dto.ActivityDto> items = new ArrayList<>();
    private final List<Object> rows = new ArrayList<>();           // String heading or ActivityDto
    private final Adapter adapter = new Adapter();
    private AppPrefs app;
    @Nullable private ZonedDateTime seen;
    private int request;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);
        EdgeInsets.padTop(findViewById(R.id.notif_header));
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        app = new AppPrefs(this);
        seen = when(app.notificationsSeen());

        list = findViewById(R.id.notif_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        EdgeInsets.padBottom(list, false);
        refresh = findViewById(R.id.notif_refresh);
        refresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.amber));
        refresh.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.surface_lowest));
        refresh.setOnRefreshListener(() -> load(false));
        skeleton = findViewById(R.id.notif_skeleton);
        state = findViewById(R.id.notif_state);
        markAll = findViewById(R.id.notif_mark_all);
        markAll.setOnClickListener(v -> markAllRead(true));
        findViewById(R.id.notif_state_action).setOnClickListener(v -> load(true));
        load(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Leaving the screen counts as having seen what was on it.
        if (isFinishing()) markAllRead(false);
    }

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

    private boolean unread(@NonNull dto.ActivityDto a) {
        ZonedDateTime t = when(a.createdAt);
        return t != null && (seen == null || t.isAfter(seen));
    }

    private void markAllRead(boolean byTap) {
        if (items.isEmpty()) return;
        String newest = items.get(0).createdAt;
        if (newest == null) return;
        app.setNotificationsSeen(newest);
        seen = when(newest);
        if (byTap) {
            Haptics.tick(this);
            adapter.notifyDataSetChanged();
            markAll.setVisibility(View.INVISIBLE);
        }
    }

    // ── Loading ─────────────────────────────────────────────────────────────

    private void load(boolean showSkeleton) {
        int mine = ++request;
        state.setVisibility(View.GONE);
        if (showSkeleton) {
            skeleton.setVisibility(View.VISIBLE);
            Skeleton.shimmer(skeleton);
        }
        List<dto.ActivityDto> merged = new ArrayList<>();
        int[] left = {CATEGORIES.size()};
        boolean[] failed = {false};
        for (String c : CATEGORIES) {
            ApiClient.get(this).notifications(c).enqueue(new Callback<List<dto.ActivityDto>>() {
                @Override
                public void onResponse(@NonNull Call<List<dto.ActivityDto>> call,
                                       @NonNull Response<List<dto.ActivityDto>> r) {
                    if (r.isSuccessful() && r.body() != null) merged.addAll(r.body());
                    else failed[0] = true;
                    if (--left[0] == 0) show(mine, merged, failed[0]);
                }

                @Override
                public void onFailure(@NonNull Call<List<dto.ActivityDto>> call, @NonNull Throwable t) {
                    failed[0] = true;
                    if (--left[0] == 0) show(mine, merged, true);
                }
            });
        }
    }

    private void show(int mine, @NonNull List<dto.ActivityDto> got, boolean failed) {
        if (mine != request || isFinishing()) return;
        refresh.setRefreshing(false);
        Skeleton.reveal(skeleton, null);
        skeleton.setVisibility(View.GONE);
        got.sort((a, b) -> {
            ZonedDateTime ta = when(a.createdAt), tb = when(b.createdAt);
            if (ta == null || tb == null) return String.valueOf(b.createdAt).compareTo(String.valueOf(a.createdAt));
            return tb.compareTo(ta);
        });
        items.clear();
        items.addAll(got);
        rows.clear();
        LocalDate today = LocalDate.now();
        boolean todayHead = false, earlierHead = false;
        for (dto.ActivityDto a : items) {
            ZonedDateTime t = when(a.createdAt);
            boolean isToday = t != null && t.toLocalDate().equals(today);
            if (isToday && !todayHead) {
                rows.add(getString(R.string.subs_today));
                todayHead = true;
            } else if (!isToday && !earlierHead) {
                rows.add(getString(R.string.notif_k_earlier));
                earlierHead = true;
            }
            rows.add(a);
        }
        adapter.notifyDataSetChanged();
        boolean anyUnread = false;
        for (dto.ActivityDto a : items) anyUnread |= unread(a);
        markAll.setVisibility(anyUnread ? View.VISIBLE : View.INVISIBLE);
        if (!items.isEmpty()) return;

        state.setVisibility(View.VISIBLE);
        TextView title = findViewById(R.id.notif_state_title);
        TextView hint = findViewById(R.id.notif_state_hint);
        View action = findViewById(R.id.notif_state_action);
        title.setText(failed ? R.string.notif_error_title : R.string.notif_k_empty_title);
        hint.setText(failed ? R.string.notif_error_hint : R.string.notif_k_empty_hint);
        action.setVisibility(failed ? View.VISIBLE : View.GONE);
    }

    // ── Rows ────────────────────────────────────────────────────────────────

    /** Icon and disc for a notification type (Stitch: teal-soft, gold-soft for milestones). */
    private static final class Look {
        @DrawableRes final int icon;
        final boolean gold;
        final int tint;

        Look(@DrawableRes int icon, boolean gold, int tint) {
            this.icon = icon;
            this.gold = gold;
            this.tint = tint;
        }
    }

    @NonNull
    private static Look look(@Nullable String type) {
        String t = type == null ? "" : type;
        if (t.startsWith("week.")) return new Look(R.drawable.ic_ph_certificate, false, R.color.on_secondary_container);
        if (t.contains("rank")) return new Look(R.drawable.ic_ph_trend_up, false, R.color.amber);
        if (t.contains("goal")) return new Look(R.drawable.ic_ph_trophy, true, R.color.text_primary);
        if (t.contains("streak") || t.contains("achievement") || t.contains("milestone"))
            return new Look(R.drawable.ic_ph_sparkle, true, R.color.text_primary);
        if (t.contains("join") || t.contains("member")) return new Look(R.drawable.ic_ph_user_plus, false, R.color.amber);
        if (t.contains("reminder")) return new Look(R.drawable.ic_ph_moon, false, R.color.on_secondary_container);
        if (t.contains("announcement")) return new Look(R.drawable.ic_ph_quotes, false, R.color.amber);
        return new Look(R.drawable.ic_ph_bell, false, R.color.amber);
    }

    /** Today: "18 min ago"; yesterday: "Yesterday · 4:10 PM"; earlier: "22 Nov". */
    @NonNull
    private String timeLabel(@Nullable String iso) {
        ZonedDateTime t = when(iso);
        if (t == null) return "";
        LocalDate d = t.toLocalDate(), today = LocalDate.now();
        if (d.equals(today)) {
            return DateUtils.getRelativeTimeSpanString(t.toInstant().toEpochMilli(), System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS).toString();
        }
        String clock = t.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT));
        if (d.equals(today.minusDays(1))) return getString(R.string.subs_yesterday) + " · " + clock;
        Locale loc = getResources().getConfiguration().getLocales().get(0);
        return t.format(DateTimeFormatter.ofPattern(d.getYear() == today.getYear() ? "d MMM" : "d MMM yyyy", loc));
    }

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int HEAD = 0, ROW = 1;

        @Override
        public int getItemViewType(int position) { return rows.get(position) instanceof String ? HEAD : ROW; }

        @Override
        public int getItemCount() { return rows.size(); }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            int layout = type == HEAD ? R.layout.item_k_notif_group : R.layout.item_k_notification;
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false)) { };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            View v = h.itemView;
            if (getItemViewType(position) == HEAD) {
                ((TextView) v).setText((String) rows.get(position));
                v.setPadding(v.getPaddingLeft(), position == 0 ? dp(8) : dp(24), v.getPaddingRight(), v.getPaddingBottom());
                return;
            }
            dto.ActivityDto a = (dto.ActivityDto) rows.get(position);
            boolean firstInGroup = position > 0 && rows.get(position - 1) instanceof String;
            v.setBackgroundResource(firstInGroup ? R.drawable.bg_k_line_both : R.drawable.bg_k_line_bottom);
            Look l = look(a.type);
            v.findViewById(R.id.nt_disc).setBackgroundResource(l.gold ? R.drawable.bg_k_notif_disc_gold : R.drawable.bg_k_notif_disc_teal);
            ImageView icon = v.findViewById(R.id.nt_icon);
            icon.setImageResource(l.icon);
            icon.setImageTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(v.getContext(), l.tint)));
            ((TextView) v.findViewById(R.id.nt_text)).setText(a.text);
            ((TextView) v.findViewById(R.id.nt_time)).setText(timeLabel(a.createdAt));
            boolean isUnread = unread(a);
            v.findViewById(R.id.nt_dot).setVisibility(isUnread ? View.VISIBLE : View.INVISIBLE);
            v.setContentDescription((isUnread ? getString(R.string.notif_k_unread) + ". " : "") + a.text
                    + ". " + timeLabel(a.createdAt));
            boolean week = a.type != null && a.type.startsWith("week.");
            v.setClickable(week);
            v.setOnClickListener(week ? x -> startActivity(new Intent(NotificationsActivity.this, WeekReportActivity.class)) : null);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
