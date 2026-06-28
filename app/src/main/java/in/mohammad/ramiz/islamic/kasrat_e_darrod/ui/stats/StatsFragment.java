package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.stats;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter.StatBarAdapter;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.Anims;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Stats page: a community-total widget, a category pie chart, and a per-recitation
 * breakdown (filling bars). For super members / super admins it also shows a button
 * that files the current submissions as a consideration, archives the week and starts
 * a new one. The full leaderboard lives on its own bottom-nav destination.
 */
public class StatsFragment extends Fragment {

    private TextView total, weekLabel, empty;
    private RecyclerView bars;
    private MaterialButton closeWeek;
    private final StatBarAdapter barAdapter = new StatBarAdapter();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        in.mohammad.ramiz.islamic.kasrat_e_darrod.util.Avatars.load(view.findViewById(R.id.stats_avatar));

        total = view.findViewById(R.id.stats_total);
        weekLabel = view.findViewById(R.id.stats_week_label);
        empty = view.findViewById(R.id.stats_empty);
        closeWeek = view.findViewById(R.id.btn_close_week);

        bars = view.findViewById(R.id.stats_bars);
        bars.setLayoutManager(new LinearLayoutManager(getContext()));
        bars.setAdapter(barAdapter);

        closeWeek.setOnClickListener(v -> confirmCloseWeek());

        loadCommunity();
    }

    // --- Community total + per-recitation breakdown ---------------------------

    private void loadCommunity() {
        ApiClient.get(requireContext()).communityStats(null)
                .enqueue(new Callback<dto.CommunityStats>() {
                    @Override
                    public void onResponse(@NonNull Call<dto.CommunityStats> call,
                                           @NonNull Response<dto.CommunityStats> response) {
                        if (!isAdded()) return;
                        if (response.isSuccessful() && response.body() != null) {
                            bindCommunity(response.body());
                        } else {
                            toast(getString(R.string.error_load_failed));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<dto.CommunityStats> call,
                                          @NonNull Throwable t) {
                        if (isAdded()) toast(getString(R.string.error_network));
                    }
                });
    }

    private void bindCommunity(dto.CommunityStats data) {
        total.setText(NumberFormat.getIntegerInstance().format(data.total));

        if (data.weekNumber != null) {
            String label = getString(R.string.week_label_fmt, data.weekNumber);
            if (data.label != null && !data.label.isEmpty()) label += "  ·  " + data.label;
            weekLabel.setText(label);
            weekLabel.setVisibility(View.VISIBLE);
        } else {
            weekLabel.setVisibility(View.GONE);
        }

        List<dto.RecitationStat> list = data.byRecitation != null
                ? data.byRecitation : new ArrayList<>();
        barAdapter.submitGrouped(list);   // grouped + sorted by category
        empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        bars.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        if (!list.isEmpty()) Anims.fallDown(bars);

        // Only super members / super admins may file the consideration.
        closeWeek.setVisibility(data.canManage ? View.VISIBLE : View.GONE);
    }

    // --- Close the week (file consideration) ---------------------------------

    private void confirmCloseWeek() {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_close_week, null, false);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(content)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        content.findViewById(R.id.dialog_cancel).setOnClickListener(v -> dialog.dismiss());
        content.findViewById(R.id.dialog_confirm).setOnClickListener(v -> {
            dialog.dismiss();
            doCloseWeek();
        });
        dialog.show();
    }

    private void doCloseWeek() {
        closeWeek.setEnabled(false);
        ApiClient.get(requireContext()).closeWeek()
                .enqueue(new Callback<dto.OkResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<dto.OkResponse> call,
                                           @NonNull Response<dto.OkResponse> response) {
                        if (!isAdded()) return;
                        closeWeek.setEnabled(true);
                        if (response.isSuccessful()) {
                            toast(getString(R.string.close_week_success));
                            loadCommunity();   // refresh into the new (empty) week
                            // Take the manager straight to the archive to grab the report.
                            startActivity(new Intent(requireContext(),
                                    in.mohammad.ramiz.islamic.kasrat_e_darrod
                                            .ui.profile.WeekReportActivity.class));
                        } else {
                            toast(getString(R.string.close_week_failed));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<dto.OkResponse> call,
                                          @NonNull Throwable t) {
                        if (!isAdded()) return;
                        closeWeek.setEnabled(true);
                        toast(getString(R.string.close_week_failed));
                    }
                });
    }

    private void toast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
