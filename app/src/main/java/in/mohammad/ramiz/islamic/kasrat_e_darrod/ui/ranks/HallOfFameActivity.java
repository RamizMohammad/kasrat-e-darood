package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.ranks;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.base.BaseActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.AuraAvatarView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.PodiumView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.EdgeInsets;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Numbers;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.Skeleton;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Hall of Fame (Stitch hall_of_fame_teal): every closed week, newest first. The newest
 * gets the gold-framed card (big winner, #2 · #3 under a hairline); older weeks get a
 * compact card (winner row, then "Name · total" for #2 and #3). Pages of 10 follow the
 * X-Next-Cursor header as you scroll.
 */
public class HallOfFameActivity extends BaseActivity {

    private static final int PAGE = 10;

    private final List<dto.HallOfFameWeek> weeks = new ArrayList<>();
    private final Adapter adapter = new Adapter();
    private SwipeRefreshLayout refresh;
    private View skeleton, state;
    @Nullable private String cursor;
    private boolean loading, endReached, pageFailed;
    private int generation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hall_of_fame);
        EdgeInsets.padTop(findViewById(R.id.root));
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        RecyclerView list = findViewById(R.id.hall_list);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        list.setLayoutManager(lm);
        list.setAdapter(adapter);
        EdgeInsets.padBottom(list, false);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 0 && lm.findLastVisibleItemPosition() >= adapter.getItemCount() - 3) fetch(false);
            }
        });
        refresh = findViewById(R.id.hall_refresh);
        refresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.amber));
        refresh.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.surface_raised));
        refresh.setOnRefreshListener(this::reload);
        skeleton = findViewById(R.id.hall_skeleton);
        state = findViewById(R.id.hall_state);

        skeleton.setVisibility(View.VISIBLE);
        Skeleton.shimmer(skeleton);
        reload();
    }

    private void reload() {
        generation++;
        cursor = null;
        endReached = false;
        loading = false;
        fetch(true);
    }

    private void fetch(boolean first) {
        if (loading || (!first && (endReached || pageFailed))) return;
        loading = true;
        int gen = generation;
        ApiClient.get(this).hallOfFame(first ? null : cursor, PAGE).enqueue(new Callback<List<dto.HallOfFameWeek>>() {
            @Override
            public void onResponse(@NonNull Call<List<dto.HallOfFameWeek>> call,
                                   @NonNull Response<List<dto.HallOfFameWeek>> r) {
                if (isFinishing() || gen != generation) return;
                loading = false;
                refresh.setRefreshing(false);
                if (!r.isSuccessful() || r.body() == null) {
                    failed(first);
                    return;
                }
                if (first) weeks.clear();
                weeks.addAll(r.body());
                cursor = r.headers().get("X-Next-Cursor");
                endReached = cursor == null;
                pageFailed = false;
                Skeleton.reveal(skeleton, null);
                adapter.notifyDataSetChanged();
                if (weeks.isEmpty()) showState(false);
                else state.setVisibility(View.GONE);
            }

            @Override
            public void onFailure(@NonNull Call<List<dto.HallOfFameWeek>> call, @NonNull Throwable t) {
                if (isFinishing() || gen != generation) return;
                loading = false;
                refresh.setRefreshing(false);
                failed(first);
            }
        });
    }

    private void failed(boolean first) {
        Skeleton.reveal(skeleton, null);
        if (first && weeks.isEmpty()) {
            showState(true);
        } else if (!first) {
            pageFailed = true;
            adapter.notifyItemChanged(weeks.size());
        }
    }

    private void showState(boolean error) {
        state.setVisibility(View.VISIBLE);
        ((TextView) state.findViewById(R.id.state_title)).setText(error ? R.string.hall_error_title : R.string.hall_empty_title);
        ((TextView) state.findViewById(R.id.state_hint)).setText(error ? R.string.ranks_error_hint : R.string.hall_empty_hint);
        MaterialButton action = state.findViewById(R.id.state_action);
        action.setVisibility(error ? View.VISIBLE : View.GONE);
        action.setText(R.string.action_try_again);
        action.setOnClickListener(v -> {
            state.setVisibility(View.GONE);
            skeleton.setVisibility(View.VISIBLE);
            Skeleton.shimmer(skeleton);
            reload();
        });
    }

    // ── List ────────────────────────────────────────────────────────────────

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int WEEK = 0, LOADER = 1, FEATURED = 2;

        @Override
        public int getItemCount() {
            return weeks.size() + (weeks.isEmpty() || endReached ? 0 : 1);
        }

        @Override
        public int getItemViewType(int position) {
            if (position >= weeks.size()) return LOADER;
            return position == 0 ? FEATURED : WEEK;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            int layout = type == FEATURED ? R.layout.item_hall_featured
                    : type == WEEK ? R.layout.item_hall_week : R.layout.item_page_loader;
            View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
            return new RecyclerView.ViewHolder(v) { };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            if (getItemViewType(position) == LOADER) {
                View retry = h.itemView.findViewById(R.id.page_retry);
                h.itemView.findViewById(R.id.page_spinner).setVisibility(pageFailed ? View.GONE : View.VISIBLE);
                retry.setVisibility(pageFailed ? View.VISIBLE : View.GONE);
                retry.setOnClickListener(v -> {
                    pageFailed = false;
                    notifyItemChanged(weeks.size());
                    fetch(false);
                });
                return;
            }
            bindWeek(h.itemView, weeks.get(position), position == 0);
        }
    }

    private void bindWeek(@NonNull View v, @NonNull dto.HallOfFameWeek w, boolean featured) {
        String range = dates(w.startsAt, w.closedAt);
        ((TextView) v.findViewById(R.id.hall_week)).setText(range.isEmpty()
                ? getString(R.string.ranks_week_n, w.weekNumber)
                : getString(R.string.hall_week_dates, w.weekNumber, range));

        List<dto.BoardEntry> people = w.top != null ? w.top : new ArrayList<>();
        boolean nobody = people.isEmpty();
        v.findViewById(R.id.hall_nobody).setVisibility(nobody ? View.VISIBLE : View.GONE);
        View winnerName = v.findViewById(R.id.hall_winner_name);
        View winnerTotal = v.findViewById(R.id.hall_winner_total);
        View winnerAvatar = v.findViewById(R.id.hall_winner_avatar);
        View winnerRow = v.findViewById(R.id.hall_winner_row);              // compact card only
        if (winnerRow != null) winnerRow.setVisibility(nobody ? View.GONE : View.VISIBLE);
        if (featured) {
            v.findViewById(R.id.hall_winner_ring).setVisibility(nobody ? View.GONE : View.VISIBLE);
            winnerName.setVisibility(nobody ? View.GONE : View.VISIBLE);
            winnerTotal.setVisibility(nobody ? View.GONE : View.VISIBLE);
        }

        StringBuilder cd = new StringBuilder(getString(R.string.hall_week_cd, w.weekNumber, Numbers.format(w.total)));
        if (!nobody) {
            dto.BoardEntry first = people.get(0);
            RankRows.avatar((AuraAvatarView) winnerAvatar, first);
            String name = RankRows.name(this, first);
            ((TextView) winnerName).setText(name);
            ((TextView) winnerTotal).setText(Numbers.format(first.total));
            cd.append(". ").append(getString(R.string.ranks_row_cd, RankRows.rankText(first), name, Numbers.format(first.total)));
        }

        LinearLayout top = v.findViewById(R.id.hall_top);
        top.removeAllViews();
        float dp = getResources().getDisplayMetrics().density;
        for (int i = 1; i < Math.min(3, people.size()); i++) {
            dto.BoardEntry e = people.get(i);
            View row = LayoutInflater.from(this).inflate(R.layout.item_hall_person, top, false);
            AuraAvatarView av = row.findViewById(R.id.hp_avatar);
            RankRows.avatar(av, e);
            String name = RankRows.name(this, e);
            TextView nameView = row.findViewById(R.id.hp_name);
            TextView totalView = row.findViewById(R.id.hp_total);
            View ring = row.findViewById(R.id.hp_ring);
            if (featured) {
                nameView.setText(name);
                totalView.setText(Numbers.format(e.total));
            } else {
                // Older weeks: 24dp avatar and one warm-grey line, "Ayesha · 39,400".
                ViewGroup.LayoutParams lp = ring.getLayoutParams();
                lp.width = lp.height = (int) (24 * dp);
                ring.setLayoutParams(lp);
                nameView.setText(getString(R.string.hall_person_line, name, Numbers.format(e.total)));
                nameView.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
                totalView.setVisibility(View.GONE);
                ((ViewGroup.MarginLayoutParams) ((View) nameView.getParent()).getLayoutParams())
                        .setMarginStart((int) (8 * dp));
            }
            top.addView(row);
            cd.append(". ").append(getString(R.string.ranks_row_cd, RankRows.rankText(e), name, Numbers.format(e.total)));
        }
        boolean runnersUp = top.getChildCount() > 0;
        top.setVisibility(runnersUp ? View.VISIBLE : View.GONE);
        v.findViewById(R.id.hall_rule).setVisibility(runnersUp ? View.VISIBLE : View.GONE);
        v.setContentDescription(cd);
        v.setFocusable(true);
    }

    @NonNull
    private String dates(@Nullable String startIso, @Nullable String endIso) {
        try {
            long start = OffsetDateTime.parse(startIso).toInstant().toEpochMilli();
            long end = endIso != null ? OffsetDateTime.parse(endIso).toInstant().toEpochMilli() : start;
            return DateUtils.formatDateRange(this, start, end,
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_NO_YEAR);
        } catch (RuntimeException e) {
            return "";
        }
    }
}
