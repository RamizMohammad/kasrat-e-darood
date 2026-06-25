package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
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
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter.SkeletonAdapter;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter.SubmissionAdapter;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.auth.LoginActivity;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Profile screen: identity, submission stats and the user's recent submissions. */
public class ProfileFragment extends Fragment {

    private TextView statWeek, statToday, statLifetime, streak;
    private RecyclerView submissions;
    private TextView submissionsEmpty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        TokenStore store = new TokenStore(requireContext());

        TextView name = view.findViewById(R.id.profile_name);
        if (name != null && !TextUtils.isEmpty(store.userName())) {
            name.setText(store.userName());
        }

        statWeek = view.findViewById(R.id.stat_week);
        statToday = view.findViewById(R.id.stat_today);
        statLifetime = view.findViewById(R.id.stat_lifetime);
        streak = view.findViewById(R.id.profile_streak);
        submissionsEmpty = view.findViewById(R.id.submissions_empty);

        submissions = view.findViewById(R.id.recycler_submissions);
        submissions.setLayoutManager(new LinearLayoutManager(getContext()));
        submissions.setAdapter(new SkeletonAdapter(4));

        view.findViewById(R.id.btn_logout).setOnClickListener(v -> {
            store.clear();
            Intent intent = new Intent(requireContext(), LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        loadStats();
        loadSubmissions();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh after the user may have submitted from the Library.
        if (statWeek != null) {
            loadStats();
            loadSubmissions();
        }
    }

    private void loadStats() {
        ApiClient.get(requireContext()).dashboard(null)
                .enqueue(new Callback<dto.DashboardResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<dto.DashboardResponse> call,
                                           @NonNull Response<dto.DashboardResponse> response) {
                        if (!isAdded() || !response.isSuccessful() || response.body() == null) return;
                        dto.DashboardResponse d = response.body();
                        statWeek.setText(format(d.weeklyTotal));
                        statToday.setText(format(d.todayTotal));
                        statLifetime.setText(format(d.lifetimeTotal));
                        streak.setText(getString(R.string.streak_days_label, d.streak));
                    }

                    @Override
                    public void onFailure(@NonNull Call<dto.DashboardResponse> call,
                                          @NonNull Throwable t) { /* leave placeholders */ }
                });
    }

    private void loadSubmissions() {
        ApiClient.get(requireContext()).mySubmissions()
                .enqueue(new Callback<List<dto.MySubmissionDto>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<dto.MySubmissionDto>> call,
                                           @NonNull Response<List<dto.MySubmissionDto>> response) {
                        if (!isAdded()) return;
                        List<dto.MySubmissionDto> list = response.isSuccessful() && response.body() != null
                                ? response.body() : new ArrayList<>();
                        submissions.setAdapter(new SubmissionAdapter(list));
                        submissionsEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<dto.MySubmissionDto>> call,
                                          @NonNull Throwable t) {
                        if (!isAdded()) return;
                        submissions.setAdapter(new SubmissionAdapter(new ArrayList<>()));
                        submissionsEmpty.setVisibility(View.VISIBLE);
                    }
                });
    }

    private static String format(int value) {
        return NumberFormat.getIntegerInstance().format(value);
    }
}
