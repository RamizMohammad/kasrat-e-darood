package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.stats;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.model.LeaderboardEntry;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter.LeaderboardAdapter;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter.LoaderAdapter;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.Anims;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Weekly insights + community standings, loaded live from the server. */
public class StatsFragment extends Fragment {

    private RecyclerView recycler;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        recycler = view.findViewById(R.id.recycler_leaderboard);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(new LoaderAdapter());   // branded GIF while loading
        load();
    }

    private void load() {
        ApiClient.get(requireContext()).leaderboard(null, "weekly")
                .enqueue(new Callback<dto.LeaderboardResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<dto.LeaderboardResponse> call,
                                           @NonNull Response<dto.LeaderboardResponse> response) {
                        if (!isAdded()) return;
                        if (response.isSuccessful() && response.body() != null) {
                            bind(response.body());
                        } else {
                            recycler.setAdapter(new LeaderboardAdapter(new ArrayList<>()));
                            toast(getString(R.string.error_load_failed));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<dto.LeaderboardResponse> call,
                                          @NonNull Throwable t) {
                        if (!isAdded()) return;
                        recycler.setAdapter(new LeaderboardAdapter(new ArrayList<>()));
                        toast(getString(R.string.error_network));
                    }
                });
    }

    private void bind(dto.LeaderboardResponse data) {
        List<LeaderboardEntry> items = new ArrayList<>();
        if (data.entries != null) {
            for (dto.LeaderboardEntryDto e : data.entries) {
                String name = e.user != null && e.user.displayName != null
                        ? e.user.displayName : "Member";
                String streak = e.streak == 1 ? "1 day streak" : e.streak + " days streak";
                items.add(new LeaderboardEntry(e.rank, name, streak, e.total, e.rank <= 3));
            }
        }
        recycler.setAdapter(new LeaderboardAdapter(items));
        Anims.fallDown(recycler);
    }

    private void toast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
