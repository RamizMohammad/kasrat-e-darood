package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.ranks;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.MainActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.KasratDialog;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Numbers;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Ranks tab (screen 26).
 *
 * Global (P4): everyone's total for the week or all time, the podium and a row
 * per person, your own row pinned while it's off screen (see BoardPane).
 * Groups (P5): groups ranked by this week's total or average per member.
 *
 * Super members and admins get a button to close the week early, with a
 * summary of what will be archived before they confirm.
 */
public class RanksFragment extends Fragment implements MainActivity.NavSpaceAware {

    private BoardPane board;
    private View closeWeek;

    public RanksFragment() {
        super(R.layout.fragment_ranks);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        String scope = savedInstanceState != null ? savedInstanceState.getString("scope", "week") : "week";
        closeWeek = view.findViewById(R.id.ranks_close_week);
        in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.EdgeInsets.padTop(view.findViewById(R.id.ranks_header));

        board = new BoardPane(view.findViewById(R.id.ranks_board), new BoardPane.Source() {
            @NonNull @Override
            public Call<dto.BoardResponse> load(@NonNull String s, @Nullable Boolean aroundMe, int limit) {
                return ApiClient.get(requireContext()).globalBoard(s, aroundMe, limit);
            }

            @NonNull @Override
            public String key() { return "global"; }
        }, BoardAdapter.Labels.GLOBAL, this::openRecite, this::isAdded);
        board.start(scope);
        if (getActivity() instanceof MainActivity) onNavSpace(((MainActivity) getActivity()).navSpace());

        view.findViewById(R.id.ranks_hall).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), HallOfFameActivity.class)));
        closeWeek.setOnClickListener(v -> confirmCloseWeek());
        checkCanManage();
    }

    @Override
    public void onNavSpace(int px) {
        if (board != null) board.setBottomSpace(px);
    }

    @Override
    public void onResume() {
        super.onResume();
        board.resume();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (board != null) out.putString("scope", board.scope());
    }

    private void openRecite() {
        if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).openTab(MainActivity.TAB_RECITE);
    }

    // ── Close the week (super members / admins) ────────────────────────────

    private void checkCanManage() {
        ApiClient.get(requireContext()).communityStats(null).enqueue(new Callback<dto.CommunityStats>() {
            @Override
            public void onResponse(@NonNull Call<dto.CommunityStats> call, @NonNull Response<dto.CommunityStats> r) {
                if (isAdded() && r.body() != null) {
                    closeWeek.setVisibility(r.body().canManage ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onFailure(@NonNull Call<dto.CommunityStats> call, @NonNull Throwable t) { }
        });
    }

    /** Fetch this week's board, then show what closing will archive. */
    private void confirmCloseWeek() {
        closeWeek.setEnabled(false);
        ApiClient.get(requireContext()).globalBoard("week", null, 200).enqueue(new Callback<dto.BoardResponse>() {
            @Override
            public void onResponse(@NonNull Call<dto.BoardResponse> call, @NonNull Response<dto.BoardResponse> r) {
                if (!isAdded()) return;
                closeWeek.setEnabled(true);
                if (r.body() == null || r.body().week == null) {
                    Snackbar.make(requireView(), R.string.ranks_close_failed, Snackbar.LENGTH_LONG).show();
                    return;
                }
                showCloseDialog(r.body());
            }

            @Override
            public void onFailure(@NonNull Call<dto.BoardResponse> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                closeWeek.setEnabled(true);
                Snackbar.make(requireView(), R.string.error_network, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void showCloseDialog(@NonNull dto.BoardResponse week) {
        Context ctx = requireContext();
        List<dto.BoardEntry> e = week.entries;
        int people = e == null ? 0 : e.size();
        StringBuilder top = new StringBuilder();
        for (int i = 0; e != null && i < Math.min(3, e.size()); i++) {
            if (top.length() > 0) top.append(", ");
            top.append(RankRows.name(ctx, e.get(i)));
        }
        String peopleText = people >= 200 ? "200+" : String.valueOf(people);
        String body = getString(R.string.ranks_close_summary, week.week.weekNumber,
                Numbers.format(week.total), peopleText)
                + (top.length() > 0 ? "\n" + getString(R.string.ranks_close_top, top) : "")
                + "\n\n" + getString(R.string.ranks_close_body);
        String weekId = week.week.id;
        int number = week.week.weekNumber;
        KasratDialog.with(ctx)
                .icon(R.drawable.ic_archive)
                .title(R.string.ranks_close_title)
                .body(body)
                .danger()
                .primary(R.string.ranks_close_confirm, d -> {
                    d.busy(true);
                    ApiClient.get(ctx).closeWeek(weekId).enqueue(new Callback<dto.OkResponse>() {
                        @Override
                        public void onResponse(@NonNull Call<dto.OkResponse> call, @NonNull Response<dto.OkResponse> r) {
                            if (!r.isSuccessful()) {
                                d.busy(false);
                                d.error(ctx.getString(R.string.ranks_close_failed));
                                return;
                            }
                            d.dismiss();
                            if (!isAdded()) return;
                            Haptics.click(ctx);
                            board.reload();
                            Snackbar.make(requireView(), getString(R.string.ranks_closed, number), Snackbar.LENGTH_LONG)
                                    .setAction(R.string.hall_title, v ->
                                            startActivity(new Intent(ctx, HallOfFameActivity.class)))
                                    .show();
                        }

                        @Override
                        public void onFailure(@NonNull Call<dto.OkResponse> call, @NonNull Throwable t) {
                            d.busy(false);
                            d.error(ctx.getString(R.string.error_network));
                        }
                    });
                })
                .secondary(R.string.cancel, null)
                .show();
    }
}
