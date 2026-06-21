package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.stats;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.SampleData;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter.LeaderboardAdapter;

/** Weekly insights + community standings leaderboard. */
public class StatsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        RecyclerView recycler = view.findViewById(R.id.recycler_leaderboard);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(new LeaderboardAdapter(SampleData.leaderboard()));
    }
}
