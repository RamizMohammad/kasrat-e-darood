package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter.LoaderAdapter;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter.WeekReportAdapter;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.Anims;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.WeekReportPdf;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Lists archived weeks; tapping one generates and opens a PDF report on the device. */
public class WeekReportActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private TextView empty;
    private View progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_week_report);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        empty = findViewById(R.id.weeks_empty);
        progress = findViewById(R.id.weeks_progress);
        recycler = findViewById(R.id.recycler_weeks);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(new LoaderAdapter());
        loadWeeks();
    }

    private void loadWeeks() {
        ApiClient.get(this).weekHistory().enqueue(new Callback<List<dto.WeekSummary>>() {
            @Override
            public void onResponse(@NonNull Call<List<dto.WeekSummary>> call,
                                   @NonNull Response<List<dto.WeekSummary>> response) {
                List<dto.WeekSummary> list = response.isSuccessful() && response.body() != null
                        ? response.body() : new ArrayList<>();
                bind(list);
            }

            @Override
            public void onFailure(@NonNull Call<List<dto.WeekSummary>> call, @NonNull Throwable t) {
                bind(new ArrayList<>());
            }
        });
    }

    private void bind(List<dto.WeekSummary> weeks) {
        // Newest week first.
        java.util.Collections.sort(weeks, (a, b) -> Integer.compare(b.weekNumber, a.weekNumber));
        recycler.setAdapter(new WeekReportAdapter(weeks, this::downloadReport));
        Anims.fallDown(recycler);
        boolean isEmpty = weeks.isEmpty();
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recycler.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    // --- Download a single week's report -------------------------------------

    private void downloadReport(dto.WeekSummary week) {
        setBusy(true);
        ApiClient.get(this).communityStats(week.weekId)
                .enqueue(new Callback<dto.CommunityStats>() {
                    @Override
                    public void onResponse(@NonNull Call<dto.CommunityStats> call,
                                           @NonNull Response<dto.CommunityStats> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            buildPdf(week, response.body());
                        } else {
                            setBusy(false);
                            toast(getString(R.string.report_failed));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<dto.CommunityStats> call,
                                          @NonNull Throwable t) {
                        setBusy(false);
                        toast(getString(R.string.report_failed));
                    }
                });
    }

    private void buildPdf(dto.WeekSummary week, dto.CommunityStats stats) {
        final String title = getString(R.string.week_label_fmt, week.weekNumber);
        final String period = stats.label != null ? stats.label : week.label;
        final Context appCtx = getApplicationContext();
        new Thread(() -> {
            try {
                Uri uri = WeekReportPdf.generate(appCtx, title, period,
                        stats.total, stats.byRecitation);
                runOnUiThread(() -> {
                    setBusy(false);
                    onPdfReady(uri);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    toast(getString(R.string.report_failed));
                });
            }
        }).start();
    }

    private void onPdfReady(Uri uri) {
        toast(getString(R.string.report_saved));
        try {
            Intent view = new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(uri, "application/pdf");
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(view);
        } catch (Exception ignored) {
            // No PDF viewer installed — the file is still saved in Downloads.
        }
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
