package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.groups;

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
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.model.FeedPost;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter.FeedAdapter;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter.LoaderAdapter;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.Anims;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.RelativeTime;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Community feed, loaded live from the server. */
public class GroupsFragment extends Fragment {

    private RecyclerView recycler;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_groups, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        recycler = view.findViewById(R.id.recycler_feed);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(new LoaderAdapter());   // branded GIF while loading
        load();
    }

    private void load() {
        ApiClient.get(requireContext()).feed(null)
                .enqueue(new Callback<List<dto.ActivityDto>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<dto.ActivityDto>> call,
                                           @NonNull Response<List<dto.ActivityDto>> response) {
                        if (!isAdded()) return;
                        if (response.isSuccessful() && response.body() != null) {
                            bind(response.body());
                        } else {
                            recycler.setAdapter(new FeedAdapter(new ArrayList<>()));
                            toast(getString(R.string.error_load_failed));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<dto.ActivityDto>> call,
                                          @NonNull Throwable t) {
                        if (!isAdded()) return;
                        recycler.setAdapter(new FeedAdapter(new ArrayList<>()));
                        toast(getString(R.string.error_network));
                    }
                });
    }

    private void bind(List<dto.ActivityDto> dtos) {
        List<FeedPost> items = new ArrayList<>();
        for (dto.ActivityDto a : dtos) {
            items.add(new FeedPost(
                    a.initial != null ? a.initial : "•",
                    a.actorName != null ? a.actorName : "Member",
                    RelativeTime.from(a.createdAt),
                    a.text,
                    a.reaction("fire"),
                    a.reaction("heart")));
        }
        recycler.setAdapter(new FeedAdapter(items));
        Anims.fallDown(recycler);
    }

    private void toast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
