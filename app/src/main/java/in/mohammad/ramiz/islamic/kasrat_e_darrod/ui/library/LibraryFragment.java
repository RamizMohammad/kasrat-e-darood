package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.library;

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
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.model.Recitation;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter.RecitationAdapter;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter.SkeletonAdapter;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.RecitationIcons;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Recitation library, loaded live from the server. */
public class LibraryFragment extends Fragment {

    private RecyclerView recycler;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        recycler = view.findViewById(R.id.recycler_library);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(new SkeletonAdapter(6));   // shimmer while loading
        load();
    }

    private void load() {
        ApiClient.get(requireContext()).recitations(null, null)
                .enqueue(new Callback<List<dto.RecitationDto>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<dto.RecitationDto>> call,
                                           @NonNull Response<List<dto.RecitationDto>> response) {
                        if (!isAdded()) return;
                        if (response.isSuccessful() && response.body() != null) {
                            bind(response.body());
                        } else {
                            recycler.setAdapter(new RecitationAdapter(new ArrayList<>(), null));
                            toast(getString(R.string.error_load_failed));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<dto.RecitationDto>> call,
                                          @NonNull Throwable t) {
                        if (!isAdded()) return;
                        recycler.setAdapter(new RecitationAdapter(new ArrayList<>(), null));
                        toast(getString(R.string.error_network));
                    }
                });
    }

    private void bind(List<dto.RecitationDto> dtos) {
        List<Recitation> items = new ArrayList<>();
        for (dto.RecitationDto d : dtos) {
            String tag = d.reference != null ? d.reference
                    : (d.translation != null ? d.translation : "");
            items.add(new Recitation(
                    d.id, d.arabicName, d.englishName, d.translation, tag,
                    RecitationIcons.forRecitation(d.icon, d.englishName)));
        }
        recycler.setAdapter(new RecitationAdapter(items, r ->
                Toast.makeText(getContext(), r.englishName, Toast.LENGTH_SHORT).show()));
    }

    private void toast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
