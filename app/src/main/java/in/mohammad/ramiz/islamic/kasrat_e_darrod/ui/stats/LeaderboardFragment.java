package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.stats;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.model.LeaderboardEntry;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter.LeaderboardAdapter;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter.LoaderAdapter;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.Anims;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.Avatars;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Full community leaderboard: an awesome top-3 podium plus the rest of the standings. */
public class LeaderboardFragment extends Fragment {

    private View podium, restHeader, empty;
    private RecyclerView recycler;
    private View root;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_leaderboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        root = view;
        podium = view.findViewById(R.id.lb_podium);
        restHeader = view.findViewById(R.id.lb_rest_header);
        empty = view.findViewById(R.id.leaderboard_empty);
        recycler = view.findViewById(R.id.recycler_leaderboard);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(new LoaderAdapter());
        load();
    }

    private void load() {
        ApiClient.get(requireContext()).leaderboard(null, "weekly")
                .enqueue(new Callback<dto.LeaderboardResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<dto.LeaderboardResponse> call,
                                           @NonNull Response<dto.LeaderboardResponse> response) {
                        if (!isAdded()) return;
                        bind(response.isSuccessful() ? response.body() : null);
                    }

                    @Override
                    public void onFailure(@NonNull Call<dto.LeaderboardResponse> call,
                                          @NonNull Throwable t) {
                        if (isAdded()) bind(null);
                    }
                });
    }

    private void bind(@Nullable dto.LeaderboardResponse data) {
        List<dto.LeaderboardEntryDto> entries =
                data != null && data.entries != null ? data.entries : new ArrayList<>();

        if (entries.isEmpty()) {
            podium.setVisibility(View.GONE);
            restHeader.setVisibility(View.GONE);
            recycler.setVisibility(View.GONE);
            recycler.setAdapter(new LeaderboardAdapter(new ArrayList<>()));
            empty.setVisibility(View.VISIBLE);
            return;
        }
        empty.setVisibility(View.GONE);

        // --- Podium: top 3 ---
        podium.setVisibility(View.VISIBLE);
        bindPodium(1, entries, R.id.lb_p1_col, R.id.lb_p1_avatar, R.id.lb_p1_name, R.id.lb_p1_total);
        bindPodium(2, entries, R.id.lb_p2_col, R.id.lb_p2_avatar, R.id.lb_p2_name, R.id.lb_p2_total);
        bindPodium(3, entries, R.id.lb_p3_col, R.id.lb_p3_avatar, R.id.lb_p3_name, R.id.lb_p3_total);

        // --- The rest: rank 4+ ---
        List<LeaderboardEntry> rest = new ArrayList<>();
        for (int i = 3; i < entries.size(); i++) {
            dto.LeaderboardEntryDto e = entries.get(i);
            rest.add(toEntry(e));
        }
        recycler.setAdapter(new LeaderboardAdapter(rest));
        boolean hasRest = !rest.isEmpty();
        restHeader.setVisibility(hasRest ? View.VISIBLE : View.GONE);
        recycler.setVisibility(hasRest ? View.VISIBLE : View.GONE);
        if (hasRest) Anims.fallDown(recycler);
    }

    /** Fills one podium column, hiding it when there's no entry at that position. */
    private void bindPodium(int rank, List<dto.LeaderboardEntryDto> entries,
                            int colId, int avatarId, int nameId, int totalId) {
        View col = root.findViewById(colId);
        if (entries.size() < rank) {
            col.setVisibility(View.INVISIBLE);   // keep layout balanced
            return;
        }
        col.setVisibility(View.VISIBLE);
        dto.LeaderboardEntryDto e = entries.get(rank - 1);
        String name = e.user != null && e.user.displayName != null ? e.user.displayName : "Member";
        String photo = e.user != null ? e.user.photoUrl : null;
        Avatars.loadUrl(root.findViewById(avatarId), photo);
        ((TextView) root.findViewById(nameId)).setText(name);
        ((TextView) root.findViewById(totalId))
                .setText(NumberFormat.getIntegerInstance().format(e.total));
    }

    private LeaderboardEntry toEntry(dto.LeaderboardEntryDto e) {
        String name = e.user != null && e.user.displayName != null ? e.user.displayName : "Member";
        String photo = e.user != null ? e.user.photoUrl : null;
        String streak = e.streak == 1 ? "1 day streak" : e.streak + " days streak";
        return new LeaderboardEntry(e.rank, name, streak, e.total, false, photo);
    }
}
