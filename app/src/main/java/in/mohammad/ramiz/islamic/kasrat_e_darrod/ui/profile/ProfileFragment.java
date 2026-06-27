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

import java.text.NumberFormat;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.auth.LoginActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.settings.SettingsActivity;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Profile: identity, submission stats, and a menu (Settings, Submissions, Dev, Logout). */
public class ProfileFragment extends Fragment {

    private TextView statWeek, statToday, statLifetime, streak;

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
        if (!TextUtils.isEmpty(store.userName())) name.setText(store.userName());

        statWeek = view.findViewById(R.id.stat_week);
        statToday = view.findViewById(R.id.stat_today);
        statLifetime = view.findViewById(R.id.stat_lifetime);
        streak = view.findViewById(R.id.profile_streak);

        view.findViewById(R.id.row_settings).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SettingsActivity.class)));
        view.findViewById(R.id.row_submissions).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SubmissionsActivity.class)));
        view.findViewById(R.id.row_developer).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), DeveloperInfoActivity.class)));
        view.findViewById(R.id.row_logout).setOnClickListener(v -> {
            store.clear();
            Intent intent = new Intent(requireContext(), LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (statWeek != null) loadStats();
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
                                          @NonNull Throwable t) { /* keep placeholders */ }
                });
    }

    private static String format(int value) {
        return NumberFormat.getIntegerInstance().format(value);
    }
}
