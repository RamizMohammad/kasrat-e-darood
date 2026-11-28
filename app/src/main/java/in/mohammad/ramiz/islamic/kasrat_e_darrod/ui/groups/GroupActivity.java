package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.groups;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.cache.ApiCache;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.cache.Cached;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.style.TypefaceSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.MainActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiErrors;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.base.BaseActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.AuraAvatarView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.GroupAvatarView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.KTickBarView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.ranks.BoardAdapter;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.ranks.BoardPane;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.ranks.RankRows;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.EdgeInsets;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Numbers;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Phase 6b · Circle home (Stitch circle_home_board_teal / circle_home_activity_teal).
 *
 * The hero (emblem, name, "14 members · Private", the invite-code capsule and the weekly
 * goal card) scrolls away under a fixed top bar that then shows a small emblem + name; the
 * Board · Activity · About tabs stay pinned. Board is the shared BoardPane (podium + rows,
 * GET /groups/{id}/board). Activity is a day-grouped timeline. About lists the circle's
 * rules and last week's top reciters.
 * Not a member → JoinPreviewActivity. Banned, deleted or not found → a plain status.
 */
public class GroupActivity extends BaseActivity {

    public static final String EXTRA_ID = "group_id";
    private static final int ACTIVITY_PAGE = 30;

    @NonNull
    public static Intent intent(@NonNull Context ctx, @NonNull String groupId) {
        return new Intent(ctx, GroupActivity.class).putExtra(EXTRA_ID, groupId);
    }

    private String groupId;
    @Nullable private dto.GroupDetail group;
    private BoardPane board;
    private boolean boardStarted;
    private int tab;                      // 0 board · 1 activity · 2 about
    @Nullable private String closesAt;
    private long weekTotal;
    private AppBarLayout appbar;
    private View mini;
    @Nullable private Typeface serif;

    // Activity tab
    private final List<dto.GroupActivity> activity = new ArrayList<>();
    private final ActivityAdapter activityAdapter = new ActivityAdapter();
    @Nullable private String activityCursor;
    private boolean activityLoaded, activityLoading, activityEnd, activityError;
    private SwipeRefreshLayout activityRefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group);
        EdgeInsets.padSystemBars(findViewById(R.id.root), false);
        groupId = getIntent().getStringExtra(EXTRA_ID);
        if (groupId == null) {
            finish();
            return;
        }
        if (savedInstanceState != null) tab = savedInstanceState.getInt("tab", 0);
        try {
            Typeface base = ResourcesCompat.getFont(this, R.font.fraunces);    // Playfair Display
            serif = base == null ? null : Typeface.create(base, 700, false);  // names in bold
        } catch (RuntimeException e) {
            serif = null;
        }
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.group_settings_btn).setOnClickListener(v -> {
            if (group != null) startActivity(GroupSettingsActivity.intent(this, groupId));
        });

        // The small emblem + name fades in as the hero's name scrolls under the top bar.
        appbar = findViewById(R.id.group_appbar);
        mini = findViewById(R.id.group_mini);
        View hero = findViewById(R.id.group_hero);
        float fade = 40 * getResources().getDisplayMetrics().density;
        appbar.addOnOffsetChangedListener((bar, offset) -> {
            float gone = -offset - (hero.getHeight() - fade * 3);
            mini.setAlpha(Math.max(0f, Math.min(1f, gone / fade)));
        });

        board = new BoardPane(findViewById(R.id.group_board), new BoardPane.Source() {
            @NonNull @Override
            public Call<dto.BoardResponse> load(@NonNull String scope, @Nullable Boolean aroundMe, int limit) {
                return ApiClient.get(GroupActivity.this).groupBoard(groupId, scope, aroundMe, limit);
            }

            @NonNull @Override
            public String key() { return "group:" + groupId; }
        }, BoardAdapter.Labels.GROUP, this::openRecite, () -> !isFinishing());
        board.setOnLoaded(this::onBoardLoaded);

        findViewById(R.id.group_tab_board).setOnClickListener(v -> pickTab(0));
        findViewById(R.id.group_tab_activity).setOnClickListener(v -> pickTab(1));
        findViewById(R.id.group_tab_about).setOnClickListener(v -> pickTab(2));

        RecyclerView list = findViewById(R.id.group_activity_list);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        list.setLayoutManager(lm);
        list.setAdapter(activityAdapter);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 0 && lm.findLastVisibleItemPosition() >= activityAdapter.getItemCount() - 4) loadActivity(false);
            }
        });
        activityRefresh = findViewById(R.id.group_activity_refresh);
        activityRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.amber));
        activityRefresh.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.surface_lowest));
        activityRefresh.setOnRefreshListener(() -> loadActivity(true));
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();                                  // settings may have changed it
        if (boardStarted) board.resume();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
    }

    private void openRecite() {
        startActivity(new Intent(this, MainActivity.class)
                .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_RECITE)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    // ── Loading the circle ──────────────────────────────────────────────────

    private String cacheKey() { return "group:" + groupId; }

    private boolean loading;

    private void load() {
        // The saved copy paints at once; skip the request if it's only seconds old
        // (any change in settings is a write, which makes it stale).
        if (group == null) {
            dto.GroupDetail saved = ApiCache.get(this, cacheKey(), dto.GroupDetail.class);
            if (saved != null && saved.isMember()) {
                group = saved;
                showMember(saved);
            }
        }
        if (group != null && ApiCache.isFresh(this, cacheKey(), Cached.FRESH_SHORT)) return;
        if (loading) return;                       // one request at a time (onResume can repeat)
        loading = true;
        ApiClient.get(this).group(groupId).enqueue(new Callback<dto.GroupDetail>() {
            @Override
            public void onResponse(@NonNull Call<dto.GroupDetail> call, @NonNull Response<dto.GroupDetail> r) {
                loading = false;
                if (isFinishing() || isDestroyed()) return;
                if (r.isSuccessful() && r.body() != null) {
                    if (r.body().isMember()) {
                        group = r.body();
                        ApiCache.put(GroupActivity.this, cacheKey(), group);
                        showMember(group);
                    } else {
                        // Someone else's (public) circle: the invite preview joins it.
                        startActivity(JoinPreviewActivity.intentPublic(GroupActivity.this, r.body()));
                        finish();
                    }
                    return;
                }
                ApiErrors.Failure f = ApiErrors.of(r);
                if (f.status == 410 || f.status == 404 || f.status == 403) {
                    ApiCache.remove(GroupActivity.this, cacheKey());
                    group = null;
                }
                if (f.status == 410) showStatus(R.drawable.ic_ph_trash, R.string.groups_deleted_title, R.string.groups_deleted_body, false);
                else if ("banned".equals(f.code)) showStatus(R.drawable.ic_ph_lock, R.string.groups_banned_title, R.string.groups_banned_body, false);
                else if (f.status == 404) showStatus(R.drawable.ic_ph_users_three, R.string.groups_missing_title, R.string.groups_missing_body, false);
                else if (group == null) showStatus(R.drawable.ic_cloud_off, R.string.groups_load_error, R.string.ranks_error_hint, true);
            }

            @Override
            public void onFailure(@NonNull Call<dto.GroupDetail> call, @NonNull Throwable t) {
                loading = false;
                if (isFinishing() || isDestroyed()) return;
                if (group == null) showStatus(R.drawable.ic_cloud_off, R.string.groups_load_error, R.string.ranks_error_hint, true);
            }
        });
    }

    private void only(@NonNull View visible) {
        int[] ids = {R.id.group_member, R.id.group_status, R.id.group_skeleton};
        for (int id : ids) {
            View v = findViewById(id);
            v.setVisibility(v == visible ? View.VISIBLE : View.GONE);
        }
    }

    private void showStatus(@DrawableRes int icon, @StringRes int title, @StringRes int body, boolean retry) {
        View s = findViewById(R.id.group_status);
        only(s);
        findViewById(R.id.group_settings_btn).setVisibility(View.INVISIBLE);
        ((ImageView) s.findViewById(R.id.state_icon)).setImageResource(icon);
        ((TextView) s.findViewById(R.id.state_title)).setText(title);
        ((TextView) s.findViewById(R.id.state_hint)).setText(body);
        MaterialButton action = s.findViewById(R.id.state_action);
        action.setVisibility(View.VISIBLE);
        action.setText(retry ? R.string.action_try_again : R.string.groups_back_to_groups);
        action.setOnClickListener(v -> {
            if (retry) load();
            else finish();
        });
    }

    // ── Member view ─────────────────────────────────────────────────────────

    private void showMember(@NonNull dto.GroupDetail g) {
        only(findViewById(R.id.group_member));
        findViewById(R.id.group_settings_btn).setVisibility(View.VISIBLE);
        ((GroupAvatarView) findViewById(R.id.group_avatar)).setGroup(g.avatar, g.id, g.isGlobal);
        ((GroupAvatarView) findViewById(R.id.group_mini_avatar)).setGroup(g.avatar, g.id, g.isGlobal);
        ((TextView) findViewById(R.id.group_name)).setText(g.name);
        ((TextView) findViewById(R.id.group_mini_name)).setText(g.name);
        ((TextView) findViewById(R.id.group_meta)).setText(getString(R.string.circles_members_privacy,
                Groups.members(this, g.memberCount), getString(Groups.privacyLabel(g.privacy))));

        bindCode(g);
        if (g.week != null && g.week.closesAt != null) closesAt = g.week.closesAt;
        if (!boardStarted || board.board() == null || !"week".equals(board.board().scope)) weekTotal = g.weekTotal;
        bindGoal();
        if (!boardStarted) {
            boardStarted = true;
            board.start("week");
            board.resume();
        }
        showTab(tab, false);
        bindAbout(g);
    }

    /** The invite-code capsule (copy · share), when you can see the code. */
    private void bindCode(@NonNull dto.GroupDetail g) {
        View row = findViewById(R.id.group_code_row);
        boolean has = g.inviteCode != null && !g.inviteCode.isEmpty();
        row.setVisibility(has ? View.VISIBLE : View.GONE);
        if (!has) return;
        ((TextView) findViewById(R.id.group_code)).setText(g.inviteCode);
        findViewById(R.id.group_code).setOnClickListener(v -> copyCode(g.inviteCode));
        findViewById(R.id.group_copy).setOnClickListener(v -> copyCode(g.inviteCode));
        findViewById(R.id.group_share).setOnClickListener(v -> shareCode(this, g.name, g.inviteCode));
    }

    /** Share text with the code and a /j/ link; also used by Circle settings. */
    static void shareCode(@NonNull Context ctx, @NonNull String name, @NonNull String code) {
        android.net.Uri api = android.net.Uri.parse(ApiClient.BASE_URL);
        String link = api.getScheme() + "://" + api.getAuthority() + "/j/" + code;
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, ctx.getString(R.string.group_share_text, name, code, link));
        ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.action_share)));
    }

    /** "84,250 of 1,00,000 this week · 84%", the ticked bar, "2 days left · closes Friday after Maghrib". */
    private void bindGoal() {
        if (group == null) return;
        boolean has = group.weeklyGoal != null && group.weeklyGoal > 0;
        ((TextView) findViewById(R.id.group_goal_total)).setText(Numbers.format(weekTotal));
        TextView of = findViewById(R.id.group_goal_of);
        TextView pct = findViewById(R.id.group_goal_pct);
        KTickBarView bar = findViewById(R.id.group_goal_bar);
        pct.setVisibility(has ? View.VISIBLE : View.GONE);
        bar.setVisibility(has ? View.VISIBLE : View.GONE);
        if (has) {
            long goal = group.weeklyGoal;
            of.setText(getString(R.string.circle_goal_of, Numbers.format(goal)));
            int p = (int) Math.min(100, Math.floor(weekTotal * 100d / goal));
            pct.setText(String.format(Locale.getDefault(), "%d%%", p));
            bar.setFraction(weekTotal / (float) goal);
        } else {
            of.setText(R.string.circle_goal_this_week);
        }
        int days = daysLeft(closesAt);
        TextView left = findViewById(R.id.group_goal_left);
        left.setText(days > 0
                ? getResources().getQuantityString(R.plurals.circle_days_left, days, days) + " · " + getString(R.string.circle_closes_friday)
                : getString(R.string.group_closes));
    }

    /** Whole days until the week closes, or -1 when unknown. */
    private static int daysLeft(@Nullable String iso) {
        if (iso == null) return -1;
        try {
            long ms = OffsetDateTime.parse(iso).toInstant().toEpochMilli() - System.currentTimeMillis();
            return ms < 0 ? -1 : (int) Math.ceil(ms / 86_400_000d);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** A fresh week board also refreshes the goal card. */
    private void onBoardLoaded() {
        dto.BoardResponse b = board.board();
        if (b != null && "week".equals(b.scope)) {
            if (b.week != null && b.week.closesAt != null) closesAt = b.week.closesAt;
            weekTotal = b.total;
            bindGoal();
        }
    }

    private void pickTab(int t) {
        if (t == tab) return;
        Haptics.tick(this);
        showTab(t, true);
    }

    private void showTab(int t, boolean byUser) {
        tab = t;
        int[] ids = {R.id.group_tab_board, R.id.group_tab_activity, R.id.group_tab_about};
        int on = ContextCompat.getColor(this, R.color.text_primary);
        int off = ContextCompat.getColor(this, R.color.text_secondary);
        for (int i = 0; i < ids.length; i++) {
            TextView tv = findViewById(ids[i]);
            tv.setTextColor(i == t ? on : off);
            tv.setBackgroundResource(i == t ? R.drawable.bg_k_tab_underline : 0);
            tv.setSelected(i == t);
        }
        board.setShown(t == 0);
        activityRefresh.setVisibility(t == 1 ? View.VISIBLE : View.GONE);
        findViewById(R.id.group_about).setVisibility(t == 2 ? View.VISIBLE : View.GONE);
        // Stitch: Activity opens with the hero folded into the top bar; Board shows it.
        if (byUser) appbar.setExpanded(t == 0, true);
        if (t == 1 && !activityLoaded) loadActivity(true);
    }

    // ── About ───────────────────────────────────────────────────────────────

    private void bindAbout(@NonNull dto.GroupDetail g) {
        LinearLayout col = findViewById(R.id.group_about_column);
        col.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(this);
        if (g.description != null && !g.description.isEmpty()) {
            TextView d = new TextView(this);
            d.setTextAppearance(R.style.TextAppearance_K_SerifItalic);
            d.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            d.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
            d.setText(g.description);
            d.setPadding(dp(4), 0, dp(4), dp(24));
            col.addView(d);
        }
        col.addView(eyebrow(R.string.circle_section_about));
        LinearLayout card = card(col);
        aboutRow(inf, card, R.string.circle_privacy, getString(Groups.privacyLabel(g.privacy)));
        aboutRow(inf, card, R.string.circle_who_joins, getString("approval".equals(g.joinRule)
                ? R.string.circle_join_approval : R.string.circle_join_code));
        aboutRow(inf, card, R.string.groups_members_label, g.memberLimit > 0
                ? getString(R.string.groups_members_of, Numbers.format(g.memberCount), Numbers.format(g.memberLimit))
                : Numbers.format(g.memberCount));
        aboutRow(inf, card, R.string.circle_weekly_goal,
                g.weeklyGoal != null && g.weeklyGoal > 0 ? Numbers.format(g.weeklyGoal) : getString(R.string.groups_goal_none));
        aboutRow(inf, card, R.string.circle_board, getString(g.totalOnly ? R.string.circle_board_total : R.string.circle_board_full));
        aboutRow(inf, card, R.string.groups_your_role, getString(Groups.roleLabel(g.role)));
        if (g.createdAt != null) {
            String started = dateText(g.createdAt);
            if (!started.isEmpty()) aboutRow(inf, card, R.string.groups_created, started);
        }

        if (g.lastWeek != null && g.lastWeek.top != null && !g.lastWeek.top.isEmpty()) {
            TextView h = eyebrow(0);
            h.setText(getString(R.string.circle_last_week_top, g.lastWeek.weekNumber));
            ((LinearLayout.LayoutParams) h.getLayoutParams()).topMargin = dp(24);
            col.addView(h);
            int n = g.lastWeek.top.size();
            for (int i = 0; i < n; i++) {
                View row = inf.inflate(R.layout.item_rank_row, col, false);
                RankRows.bind(row, g.lastWeek.top.get(i), false);
                row.setBackgroundResource(n == 1 ? R.drawable.bg_k_row_single
                        : i == 0 ? R.drawable.bg_k_row_top
                        : i == n - 1 ? R.drawable.bg_k_row_bottom : R.drawable.bg_k_row_mid);
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) row.getLayoutParams();
                lp.setMarginStart(0);
                lp.setMarginEnd(0);
                col.addView(row);
            }
        }
    }

    @NonNull
    private TextView eyebrow(@StringRes int label) {
        TextView t = new TextView(this, null, 0, R.style.K_SectionEyebrow);
        t.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        if (label != 0) t.setText(label);
        return t;
    }

    @NonNull
    private LinearLayout card(@NonNull LinearLayout parent) {
        LinearLayout c = new LinearLayout(this, null, 0, R.style.K_Card);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setDividerDrawable(ContextCompat.getDrawable(this, R.drawable.k_divider));
        c.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        parent.addView(c, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return c;
    }

    private void aboutRow(@NonNull LayoutInflater inf, @NonNull LinearLayout card, @StringRes int label,
                          @NonNull String value) {
        View row = inf.inflate(R.layout.item_k_setting_row, card, false);
        TextView l = row.findViewById(R.id.row_label);
        l.setText(label);
        l.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
        ((TextView) row.findViewById(R.id.row_value)).setText(value);
        row.findViewById(R.id.row_caret).setVisibility(View.GONE);
        row.setBackground(null);
        row.setClickable(false);
        card.addView(row);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void copyCode(@NonNull String code) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.groups_invite_code), code));
        Haptics.click(this);
        Snackbar.make(findViewById(R.id.root), getString(R.string.groups_code_copied, code), Snackbar.LENGTH_SHORT).show();
    }

    @NonNull
    private String dateText(@NonNull String iso) {
        try {
            long ms = OffsetDateTime.parse(iso).toInstant().toEpochMilli();
            return DateUtils.formatDateTime(this, ms, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR);
        } catch (RuntimeException e) {
            return "";
        }
    }

    // ── Activity tab ────────────────────────────────────────────────────────

    private void loadActivity(boolean first) {
        if (activityLoading || (!first && (activityEnd || activityError))) return;
        activityLoading = true;
        ApiClient.get(this).groupActivity(groupId, first ? null : activityCursor, ACTIVITY_PAGE)
                .enqueue(new Callback<List<dto.GroupActivity>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<dto.GroupActivity>> call,
                                           @NonNull Response<List<dto.GroupActivity>> r) {
                        if (isFinishing()) return;
                        activityLoading = false;
                        activityRefresh.setRefreshing(false);
                        if (!r.isSuccessful() || r.body() == null) {
                            activityError = true;
                            activityAdapter.rebuild();
                            return;
                        }
                        if (first) activity.clear();
                        activity.addAll(r.body());
                        activityCursor = r.headers().get("X-Next-Cursor");
                        activityEnd = activityCursor == null;
                        activityLoaded = true;
                        activityError = false;
                        activityAdapter.rebuild();
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<dto.GroupActivity>> call, @NonNull Throwable t) {
                        if (isFinishing()) return;
                        activityLoading = false;
                        activityRefresh.setRefreshing(false);
                        activityError = true;
                        activityAdapter.rebuild();
                    }
                });
    }

    /** A day heading, or an item (and whether it closes its day, so the rail stops there). */
    private static final class Line {
        @Nullable final LocalDate day;
        @Nullable final dto.GroupActivity item;
        boolean lastOfDay;

        Line(@Nullable LocalDate day, @Nullable dto.GroupActivity item) {
            this.day = day;
            this.item = item;
        }
    }

    private static final int T_DAY = 0, T_EVENT = 1, T_NOTE = 2, T_MILESTONE = 3, T_STATE = 4;

    @Nullable
    private static Instant instant(@Nullable String iso) {
        if (iso == null) return null;
        try {
            return OffsetDateTime.parse(iso).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private final class ActivityAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<Line> lines = new ArrayList<>();

        void rebuild() {
            lines.clear();
            ZoneId zone = ZoneId.systemDefault();
            LocalDate current = null;
            for (dto.GroupActivity a : activity) {
                Instant at = instant(a.createdAt);
                LocalDate d = at != null ? at.atZone(zone).toLocalDate() : current;
                if (d != null && !d.equals(current)) {
                    if (!lines.isEmpty()) lines.get(lines.size() - 1).lastOfDay = true;
                    lines.add(new Line(d, null));
                    current = d;
                }
                lines.add(new Line(null, a));
            }
            if (!lines.isEmpty()) lines.get(lines.size() - 1).lastOfDay = true;
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return lines.isEmpty() ? (activityLoaded || activityError ? 1 : 0) : lines.size();
        }

        @Override
        public int getItemViewType(int position) {
            if (lines.isEmpty()) return T_STATE;
            Line l = lines.get(position);
            if (l.item == null) return T_DAY;
            String type = l.item.type == null ? "" : l.item.type;
            switch (type) {
                case "group.announcement": return T_NOTE;
                case "group.goal_reached":
                case "week.winners": return T_MILESTONE;
                default: return T_EVENT;
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            int layout;
            switch (type) {
                case T_DAY: layout = R.layout.item_circle_day; break;
                case T_NOTE: layout = R.layout.item_circle_note; break;
                case T_MILESTONE: layout = R.layout.item_circle_milestone; break;
                case T_STATE: layout = R.layout.view_ranks_state; break;
                default: layout = R.layout.item_circle_event;
            }
            View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
            if (type == T_STATE) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                lp.setMarginStart(0);
                lp.setMarginEnd(0);
            }
            return new RecyclerView.ViewHolder(v) { };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            View v = h.itemView;
            int type = getItemViewType(position);
            if (type == T_STATE) {
                bindActivityState(v);
                return;
            }
            Line l = lines.get(position);
            if (type == T_DAY) {
                TextView t = (TextView) v;
                t.setText(dayLabel(l.day));
                t.setPadding(t.getPaddingLeft(), position == 0 ? 0 : dp(32), t.getPaddingRight(), dp(16));
                return;
            }
            // The rail runs through the 24dp gap to the next item, but stops at a day's last one.
            View body = v.findViewById(R.id.ev_body);
            body.setPadding(body.getPaddingLeft(), 0, body.getPaddingRight(), l.lastOfDay ? 0 : dp(24));
            bindActivity(v, type, l.item);
        }
    }

    private void bindActivityState(@NonNull View v) {
        ((ImageView) v.findViewById(R.id.state_icon)).setImageResource(R.drawable.ic_ph_clock_counter_clockwise);
        ((TextView) v.findViewById(R.id.state_title)).setText(activityError ? R.string.groups_load_error : R.string.groups_activity_empty);
        ((TextView) v.findViewById(R.id.state_hint)).setText(activityError ? R.string.ranks_error_hint : R.string.groups_activity_empty_hint);
        MaterialButton b = v.findViewById(R.id.state_action);
        b.setVisibility(activityError ? View.VISIBLE : View.GONE);
        b.setText(R.string.action_try_again);
        b.setOnClickListener(x -> {
            activityError = false;
            loadActivity(true);
        });
    }

    @NonNull
    private String dayLabel(@Nullable LocalDate d) {
        if (d == null) return "";
        LocalDate today = LocalDate.now();
        if (d.equals(today)) return getString(R.string.circle_today);
        if (d.equals(today.minusDays(1))) return getString(R.string.circle_yesterday);
        long ms = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        int flags = DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_WEEKDAY
                | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH;
        if (d.getYear() != today.getYear()) flags |= DateUtils.FORMAT_SHOW_YEAR;
        return DateUtils.formatDateTime(this, ms, flags);
    }

    /** Today: "22m" / "4h"; earlier days: the clock time ("8:15 PM"). */
    @NonNull
    private String timeLabel(@Nullable Instant at, boolean withDay) {
        if (at == null) return "";
        long ms = at.toEpochMilli();
        String clock = DateFormat.getTimeFormat(this).format(new java.util.Date(ms));
        boolean today = at.atZone(ZoneId.systemDefault()).toLocalDate().equals(LocalDate.now());
        if (withDay) return (today ? getString(R.string.circle_today) : dayLabel(at.atZone(ZoneId.systemDefault()).toLocalDate())) + ", " + clock;
        if (!today) return clock;
        long mins = Math.max(0, (System.currentTimeMillis() - ms) / 60_000L);
        if (mins < 1) return getString(R.string.circle_now);
        if (mins < 60) return getString(R.string.circle_mins, (int) mins);
        return getString(R.string.circle_hours, (int) (mins / 60));
    }

    /** "<b>Zainab</b> joined the circle": the name set in Fraunces. */
    @NonNull
    private CharSequence withName(@StringRes int res, @NonNull String name, @NonNull Object... rest) {
        Object[] args = new Object[rest.length + 1];
        args[0] = name;
        System.arraycopy(rest, 0, args, 1, rest.length);
        String text = getString(res, args);
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        int i = text.indexOf(name);
        if (serif != null && i >= 0) {
            sb.setSpan(new TypefaceSpan(serif), i, i + name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    private void bindActivity(@NonNull View v, int type, @NonNull dto.GroupActivity a) {
        String actor = a.actor != null && a.actor.displayName != null ? a.actor.displayName : getString(R.string.ranks_hidden_name);
        Instant at = instant(a.createdAt);
        JsonObject p = a.payload;
        AuraAvatarView face = v.findViewById(R.id.ev_avatar);
        if (face != null) face.setAvatar(a.actor == null ? null : a.actor.avatar != null ? a.actor.avatar : a.actor.photoUrl);
        TextView text = v.findViewById(R.id.ev_text);
        TextView time = v.findViewById(R.id.ev_time);

        if (type == T_NOTE) {
            String role = p != null && p.has("role") && !p.get("role").isJsonNull() ? p.get("role").getAsString() : "admin";
            ((TextView) v.findViewById(R.id.ev_author)).setText(getString(R.string.circles_members_privacy,
                    actor, getString(Groups.roleLabel(role))));
            text.setText(getString(R.string.circle_quote, a.text == null ? "" : a.text));
            time.setText(timeLabel(at, true));
            return;
        }
        if (type == T_MILESTONE) {
            TextView title = v.findViewById(R.id.ev_title);
            ImageView icon = v.findViewById(R.id.ev_icon);
            if ("week.winners".equals(a.type)) {
                int week = p != null && p.has("week_number") ? p.get("week_number").getAsInt() : 0;
                long total = p != null && p.has("total") ? p.get("total").getAsLong() : 0;
                title.setText(getString(R.string.circle_week_closed, week));
                text.setText(getString(R.string.circle_salawat_together, Numbers.format(total)));
                icon.setImageResource(R.drawable.ic_ph_trophy);
            } else {
                long goal = p != null && p.has("goal") ? p.get("goal").getAsLong() : 0;
                title.setText(R.string.circle_goal_reached);
                text.setText(getString(R.string.circle_salawat_completed, Numbers.format(goal)));
                icon.setImageResource(R.drawable.ic_ph_sparkle);
            }
            time.setText(timeLabel(at, false));
            return;
        }

        CharSequence line;
        boolean log = false;
        switch (a.type == null ? "" : a.type) {
            case "group.join":
                line = withName(R.string.circle_act_joined, actor);
                break;
            case "group.role": {
                String role = p != null && p.has("role") && !p.get("role").isJsonNull() ? p.get("role").getAsString() : "member";
                int as = "owner".equals(role) ? R.string.circle_as_owner : "admin".equals(role) ? R.string.circle_as_admin
                        : "moderator".equals(role) ? R.string.circle_as_moderator : R.string.circle_as_member;
                line = withName(R.string.circle_act_role, actor, getString(as));
                break;
            }
            case "submission.created":
            case "recitation.logged": {
                long count = p != null && p.has("count") ? p.get("count").getAsLong() : 0;
                line = withName(R.string.circle_act_logged, actor, Numbers.format(count));
                log = true;
                break;
            }
            default:
                line = a.text != null ? a.text : "";
        }
        text.setText(line);
        time.setText(timeLabel(at, false));
        // Logs get the teal-ringed dot; joins, roles and the rest a quiet hairline one.
        v.findViewById(R.id.ev_dot).setBackgroundResource(log ? R.drawable.bg_k_dot_open : R.drawable.bg_k_dot_quiet);
    }
}
