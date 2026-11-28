package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.ranks;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.CountRollTextView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.PodiumView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Numbers;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.Skeleton;

/**
 * The Global board as one list: header (total, week, Week / All-time chips),
 * then the podium (top 3), then a row per person from 4th down. While the
 * first answer loads there's a skeleton; an empty week or an error shows a
 * state card under the header.
 */
public final class BoardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    static final int TYPE_HEADER = 0, TYPE_PODIUM = 1, TYPE_ROW = 2, TYPE_SKELETON = 3, TYPE_STATE = 4;

    public interface Listener {
        void onScope(@NonNull String scope);
        void onStateAction(boolean error);
    }

    /** The words that differ between the global board and a group's. */
    public static final class Labels {
        /** "The ummah recited" · "darood this week" / "darood all time" around the total. */
        @StringRes final int prefix, week, all, metaWeek, metaAll;
        /** A circle's board: its home already shows the week's total, so the card shows only for All time. */
        final boolean compact;

        Labels(@StringRes int prefix, @StringRes int week, @StringRes int all,
               @StringRes int metaWeek, @StringRes int metaAll) {
            this(prefix, week, all, metaWeek, metaAll, false);
        }

        Labels(@StringRes int prefix, @StringRes int week, @StringRes int all,
               @StringRes int metaWeek, @StringRes int metaAll, boolean compact) {
            this.compact = compact;
            this.prefix = prefix;
            this.week = week;
            this.all = all;
            this.metaWeek = metaWeek;
            this.metaAll = metaAll;
        }

        public static final Labels GLOBAL = new Labels(R.string.ranks_prefix_global, R.string.ranks_suffix_week,
                R.string.ranks_suffix_all, R.string.ranks_week_everyone, R.string.ranks_all_everyone);
        public static final Labels GROUP = new Labels(R.string.ranks_prefix_group, R.string.ranks_suffix_week,
                R.string.ranks_suffix_all, R.string.groups_board_meta_week, R.string.groups_board_meta_all, true);
    }

    private final Listener listener;
    private final Labels labels;
    private String scope = "week";
    @Nullable private dto.BoardResponse board;
    private boolean loading = true, error;
    private boolean animateTotal;

    BoardAdapter(@NonNull Listener listener, @NonNull Labels labels) {
        this.listener = listener;
        this.labels = labels;
    }

    void showLoading(@NonNull String scope) {
        this.scope = scope;
        board = null;
        loading = true;
        error = false;
        notifyDataSetChanged();
    }

    void show(@NonNull String scope, @NonNull dto.BoardResponse b, boolean animate) {
        this.scope = scope;
        board = b;
        loading = false;
        error = false;
        animateTotal = animate;
        notifyDataSetChanged();
    }

    /** Couldn't load: keep what's shown if anything, else an error card. */
    void showError() {
        loading = false;
        if (board == null) {
            error = true;
            notifyDataSetChanged();
        }
    }

    @Nullable dto.BoardResponse board() { return board; }

    @NonNull
    private List<dto.BoardEntry> entries() {
        return board != null && board.entries != null ? board.entries : Collections.emptyList();
    }

    /** Adapter position of the viewer's own row (the podium if they're top 3), or -1. */
    int myPosition() {
        List<dto.BoardEntry> e = entries();
        for (int i = 0; i < e.size(); i++) {
            if (e.get(i).isMe) return i < 3 ? 1 : 2 + (i - 3);
        }
        return -1;
    }

    @Override
    public int getItemCount() {
        if (loading || error || entries().isEmpty()) return 2;
        return 2 + Math.max(0, entries().size() - 3);
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) return TYPE_HEADER;
        if (loading && board == null) return TYPE_SKELETON;
        if (error || entries().isEmpty()) return TYPE_STATE;
        return position == 1 ? TYPE_PODIUM : TYPE_ROW;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        View v;
        switch (type) {
            case TYPE_HEADER: v = inf.inflate(R.layout.view_ranks_header, parent, false); break;
            case TYPE_PODIUM:
                PodiumView p = new PodiumView(parent.getContext());
                RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                int m = parent.getResources().getDimensionPixelSize(R.dimen.k_page_margin);
                lp.setMargins(m, 0, m, parent.getResources().getDimensionPixelSize(R.dimen.k_gutter_lg));
                p.setLayoutParams(lp);
                v = p;
                break;
            case TYPE_SKELETON: v = inf.inflate(R.layout.view_ranks_skeleton, parent, false); break;
            case TYPE_STATE: v = inf.inflate(R.layout.view_ranks_state, parent, false); break;
            default: v = inf.inflate(R.layout.item_rank_row, parent, false);
        }
        return new RecyclerView.ViewHolder(v) { };
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
        switch (h.getItemViewType()) {
            case TYPE_HEADER: bindHeader(h.itemView); break;
            case TYPE_PODIUM: {
                List<dto.BoardEntry> e = entries();
                ((PodiumView) h.itemView).bind(e.subList(0, Math.min(3, e.size())));
                break;
            }
            case TYPE_ROW: {
                RankRows.bind(h.itemView, entries().get(position + 1), false);
                // One hairline card split by dividers: first / middle / last segment.
                int rows = Math.max(0, entries().size() - 3), i = position - 2;
                h.itemView.setBackgroundResource(rows == 1 ? R.drawable.bg_k_row_single
                        : i == 0 ? R.drawable.bg_k_row_top
                        : i == rows - 1 ? R.drawable.bg_k_row_bottom : R.drawable.bg_k_row_mid);
                break;
            }
            case TYPE_SKELETON: Skeleton.shimmer(h.itemView); break;
            case TYPE_STATE: bindState(h.itemView); break;
            default: break;
        }
    }

    private void bindHeader(@NonNull View v) {
        boolean week = "week".equals(scope);
        v.findViewById(R.id.board_card).setVisibility(labels.compact && week ? View.GONE : View.VISIBLE);
        ((TextView) v.findViewById(R.id.board_prefix)).setText(labels.prefix);
        ((TextView) v.findViewById(R.id.board_label)).setText(week ? labels.week : labels.all);
        CountRollTextView total = v.findViewById(R.id.board_total);
        if (board != null) {
            total.setCount(board.total, animateTotal && total.hasCount());
            animateTotal = false;
        } else if (!total.hasCount()) {
            total.setText("–");
        }

        TextView meta = v.findViewById(R.id.board_meta);
        if (week && board != null && board.week != null) {
            String closes = closesText(v, board.week.closesAt);
            String left = leftText(v, board.week.closesAt);
            meta.setText(closes == null
                    ? v.getContext().getString(R.string.ranks_week_n, board.week.weekNumber)
                    : left == null ? v.getContext().getString(R.string.ranks_week_closes, board.week.weekNumber, closes)
                    : v.getContext().getString(R.string.ranks_closes_k, closes, left));
        } else {
            meta.setText(week ? labels.metaWeek : labels.metaAll);
        }

        // Stitch text tabs: the current scope is ink with a 1dp teal underline.
        TextView tWeek = v.findViewById(R.id.board_scope_week), tAll = v.findViewById(R.id.board_scope_all);
        int on = androidx.core.content.ContextCompat.getColor(v.getContext(), R.color.text_primary);
        int off = androidx.core.content.ContextCompat.getColor(v.getContext(), R.color.text_secondary);
        tWeek.setTextColor(week ? on : off);
        tAll.setTextColor(week ? off : on);
        tWeek.setBackgroundResource(week ? R.drawable.bg_k_underline : 0);
        tAll.setBackgroundResource(week ? 0 : R.drawable.bg_k_underline);
        tWeek.setSelected(week);
        tAll.setSelected(!week);
        tWeek.setOnClickListener(x -> { if (!"week".equals(scope)) listener.onScope("week"); });
        tAll.setOnClickListener(x -> { if (!"all".equals(scope)) listener.onScope("all"); });
    }

    /** "1d 4h" / "4h 12m" / "12m" until the week closes, or null once it's past. */
    @Nullable
    private static String leftText(@NonNull View v, @Nullable String iso) {
        if (iso == null) return null;
        try {
            long ms = OffsetDateTime.parse(iso).toInstant().toEpochMilli() - System.currentTimeMillis();
            if (ms <= 0) return null;
            long mins = ms / 60_000L;
            long d = mins / (24 * 60), h = (mins / 60) % 24, m = mins % 60;
            android.content.Context c = v.getContext();
            if (d > 0) return c.getString(R.string.ranks_left_dh, (int) d, (int) h);
            if (h > 0) return c.getString(R.string.ranks_left_hm, (int) h, (int) m);
            return c.getString(R.string.ranks_left_m, (int) Math.max(1, m));
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static String closesText(@NonNull View v, @Nullable String iso) {
        if (iso == null) return null;
        try {
            long ms = OffsetDateTime.parse(iso).toInstant().toEpochMilli();
            return DateUtils.formatDateTime(v.getContext(), ms,
                    DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_WEEKDAY | DateUtils.FORMAT_SHOW_TIME);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void bindState(@NonNull View v) {
        boolean week = "week".equals(scope);
        MaterialButton action = v.findViewById(R.id.state_action);
        if (!error && board != null && board.totalOnly) {
            // Total-only group: members see the group total (header) and their own number.
            ((TextView) v.findViewById(R.id.state_title)).setText(R.string.groups_total_only_title);
            long mine = board.me != null ? board.me.total : 0;
            ((TextView) v.findViewById(R.id.state_hint)).setText(v.getContext().getString(
                    week ? R.string.groups_total_only_mine_week : R.string.groups_total_only_mine_all,
                    Numbers.format(mine)));
            action.setVisibility(View.GONE);
            return;
        }
        action.setVisibility(View.VISIBLE);
        @StringRes int title = error ? R.string.ranks_error_title
                : week ? R.string.ranks_empty_week_title : R.string.ranks_empty_all_title;
        @StringRes int hint = error ? R.string.ranks_error_hint : R.string.ranks_empty_hint;
        ((TextView) v.findViewById(R.id.state_title)).setText(title);
        ((TextView) v.findViewById(R.id.state_hint)).setText(hint);
        action.setText(error ? R.string.action_try_again : R.string.me_empty_action);
        boolean wasError = error;
        action.setOnClickListener(x -> listener.onStateAction(wasError));
    }
}
