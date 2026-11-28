package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.groups;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.cache.ApiCache;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.cache.Cached;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.cache.Prefetch;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.MainActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.GroupAvatarView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.PopMenu;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Motion;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Numbers;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.Skeleton;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Circles tab (Stitch circles_teal / circles_empty_teal): the week's summary across your
 * circles, then a card per circle (emblem, role or "Goal reached", members and your number,
 * weekly-goal bar). With none: the empty state. The global circle isn't listed here — its
 * board is the Ranks tab. Older notes: The Global circle first (it opens
 * Ranks, where its board lives), then your groups by this week's total. With
 * no groups of your own, two big buttons: Create a group · Join with code.
 */
public class GroupsFragment extends Fragment implements MainActivity.NavSpaceAware {

    private static final int HEADER = 0, ROW = 1, EMPTY = 2, SKELETON = 3;
    private static final String PREFS = "kasrat_groups";

    private final List<dto.MyGroup> groups = new ArrayList<>();
    private final Adapter adapter = new Adapter();
    private final Gson gson = new Gson();
    private SwipeRefreshLayout refresh;
    private RecyclerView list;
    private boolean loaded;

    public GroupsFragment() {
        super(R.layout.fragment_groups);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        list = view.findViewById(R.id.groups_list);
        in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.EdgeInsets.padTop(view.findViewById(R.id.groups_header));
        view.findViewById(R.id.groups_join).setOnClickListener(v -> join());
        if (getActivity() instanceof MainActivity) onNavSpace(((MainActivity) getActivity()).navSpace());
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
        refresh = view.findViewById(R.id.groups_refresh);
        refresh.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.amber));
        refresh.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(requireContext(), R.color.surface_raised));
        refresh.setOnRefreshListener(this::load);

        view.findViewById(R.id.groups_fab).setOnClickListener(v -> create());

        List<dto.MyGroup> cached = readCache();
        if (cached != null) {
            groups.addAll(cached);
            loaded = true;
        }
        adapter.notifyDataSetChanged();
        if (savedInstanceState == null) Motion.enterList(list);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Creating / joining / leaving / logging are writes, which make the saved
        // copy stale; otherwise a copy from the last 30 s is good enough.
        if (!loaded || !ApiCache.isFresh(requireContext(), Prefetch.MY_GROUPS, Cached.FRESH_SHORT)) load();
    }

    private void create() {
        startActivity(new Intent(requireContext(), CreateGroupActivity.class));
    }

    private void join() {
        startActivity(new Intent(requireContext(), JoinCircleActivity.class));
    }

    @Override
    public void onNavSpace(int px) {
        if (list == null) return;
        list.setPadding(list.getPaddingLeft(), list.getPaddingTop(), list.getPaddingRight(),
                px + (int) (16 * getResources().getDisplayMetrics().density));
    }

    /** Your circles, without the global one (that board lives on Ranks). */
    @NonNull
    private List<dto.MyGroup> own() {
        List<dto.MyGroup> out = new ArrayList<>();
        for (dto.MyGroup g : groups) if (!g.isGlobal) out.add(g);
        return out;
    }

    private void load() {
        ApiClient.get(requireContext()).myGroups().enqueue(new Callback<List<dto.MyGroup>>() {
            @Override
            public void onResponse(@NonNull Call<List<dto.MyGroup>> call, @NonNull Response<List<dto.MyGroup>> r) {
                if (!isAdded()) return;
                refresh.setRefreshing(false);
                if (r.isSuccessful() && r.body() != null) {
                    groups.clear();
                    groups.addAll(r.body());
                    loaded = true;
                    writeCache(r.body());
                    adapter.notifyDataSetChanged();
                } else if (!loaded) {
                    failed();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<dto.MyGroup>> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                refresh.setRefreshing(false);
                if (!loaded) failed();
                else Snackbar.make(requireView(), R.string.error_network, Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    private void failed() {
        loaded = true;          // stop the skeleton; the empty state offers the ways in
        adapter.notifyDataSetChanged();
        Snackbar.make(requireView(), R.string.error_network, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_try_again, v -> load()).show();
    }

    // ── List ────────────────────────────────────────────────────────────────

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemCount() {
            if (!loaded) return 1;
            int n = own().size();
            return n == 0 ? 1 : 1 + n;
        }

        @Override
        public int getItemViewType(int position) {
            if (!loaded) return SKELETON;
            if (own().isEmpty()) return EMPTY;
            return position == 0 ? HEADER : ROW;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            int layout = type == HEADER ? R.layout.view_groups_header
                    : type == SKELETON ? R.layout.view_groups_skeleton
                    : type == EMPTY ? R.layout.view_groups_empty : R.layout.item_group_row;
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false)) { };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            View v = h.itemView;
            switch (h.getItemViewType()) {
                case SKELETON: Skeleton.shimmer(v); break;
                case EMPTY:
                    v.findViewById(R.id.groups_empty_create).setOnClickListener(x -> create());
                    v.findViewById(R.id.groups_empty_join).setOnClickListener(x -> join());
                    break;
                case HEADER: bindSummary(v); break;
                case ROW: bindRow(v, own().get(position - 1)); break;
                default: break;
            }
        }
    }

    /** "Your circles recited 1,48,320 together" (the number in Fraunces title-md). */
    private void bindSummary(@NonNull View v) {
        long sum = 0;
        for (dto.MyGroup g : own()) sum += g.weekTotal;
        String num = Numbers.format(sum);
        String text = getString(R.string.circles_summary, num);
        android.text.SpannableString sp = new android.text.SpannableString(text);
        int at = text.indexOf(num);
        android.graphics.Typeface fr = androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.fraunces);
        if (at >= 0 && fr != null) {
            sp.setSpan(new android.text.style.TypefaceSpan(android.graphics.Typeface.create(fr, 700, false)),
                    at, at + num.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sp.setSpan(new android.text.style.AbsoluteSizeSpan(16, true), at, at + num.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        ((TextView) v.findViewById(R.id.groups_summary)).setText(sp);
    }

    private void bindRow(@NonNull View v, @NonNull dto.MyGroup g) {
        Context ctx = requireContext();
        ((GroupAvatarView) v.findViewById(R.id.gr_avatar)).setGroup(g.avatar, g.id, false);
        ((TextView) v.findViewById(R.id.gr_name)).setText(g.name);
        String members = Groups.members(ctx, g.memberCount);
        ((TextView) v.findViewById(R.id.gr_meta)).setText(
                getString(R.string.groups_meta_you, members, Numbers.format(g.myWeekTotal)));

        boolean hasGoal = g.weeklyGoal != null && g.weeklyGoal > 0;
        boolean reached = hasGoal && g.weekTotal >= g.weeklyGoal;
        TextView tag = v.findViewById(R.id.gr_privacy);
        if (reached) {
            tag.setText(R.string.groups_goal_reached);
            tag.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary));
            tag.setBackgroundResource(R.drawable.bg_k_goal_pill);
            int px = (int) (10 * getResources().getDisplayMetrics().density);
            tag.setPadding(px, px / 5, px, px / 5);
        } else {
            tag.setText(Groups.roleLabel(g.role));
            tag.setTextColor(ContextCompat.getColor(ctx, R.color.text_muted));
            tag.setBackground(null);
            tag.setPadding(0, 0, 0, 0);
        }
        v.findViewById(R.id.gr_card).setBackgroundResource(reached ? R.drawable.bg_k_card_gold : R.drawable.bg_k_card);

        View goalBox = v.findViewById(R.id.gr_goal_box);
        TextView total = v.findViewById(R.id.gr_total);
        com.google.android.material.progressindicator.LinearProgressIndicator bar = v.findViewById(R.id.gr_goal);
        TextView goalLabel = v.findViewById(R.id.gr_goal_label);
        if (hasGoal) {
            goalLabel.setText(R.string.groups_goal_row);
            total.setText(getString(R.string.groups_goal_numbers, Numbers.format(g.weekTotal), Numbers.format(g.weeklyGoal)));
            total.setTextColor(ContextCompat.getColor(ctx, reached ? R.color.amber : R.color.text_primary));
            bar.setVisibility(View.VISIBLE);
            bar.setProgressCompat((int) Math.min(1000, g.weekTotal * 1000 / g.weeklyGoal), false);
        } else {
            goalLabel.setText(R.string.ranks_tab_week);
            total.setText(Numbers.format(g.weekTotal));
            total.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary));
            bar.setVisibility(View.GONE);
        }
        goalBox.setVisibility(View.VISIBLE);
        v.setContentDescription(getString(R.string.groups_row_cd, g.name, Numbers.format(g.weekTotal), members));
        v.findViewById(R.id.gr_card).setOnClickListener(x -> startActivity(GroupActivity.intent(ctx, g.id)));
    }

    // ── Cache ───────────────────────────────────────────────────────────────

    @Nullable
    private List<dto.MyGroup> readCache() {
        return ApiCache.get(requireContext(), Prefetch.MY_GROUPS,
                new TypeToken<List<dto.MyGroup>>() { }.getType());
    }

    private void writeCache(@NonNull List<dto.MyGroup> list) {
        ApiCache.put(requireContext(), Prefetch.MY_GROUPS, list);
    }
}
