package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.model.LeaderboardEntry;

public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.VH> {
    private final List<LeaderboardEntry> items;

    public LeaderboardAdapter(List<LeaderboardEntry> items) { this.items = items; }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_leaderboard, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        LeaderboardEntry e = items.get(position);
        h.rank.setText(String.valueOf(e.rank));
        h.name.setText(e.name);
        h.streak.setText(e.streak);
        h.total.setText(String.valueOf(e.total));
        h.avatar.setBackgroundResource(e.goldAvatar
                ? R.drawable.bg_avatar_gold : R.drawable.bg_avatar_green);
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView rank, name, streak, total;
        View avatar;
        VH(@NonNull View v) {
            super(v);
            rank = v.findViewById(R.id.lb_rank);
            name = v.findViewById(R.id.lb_name);
            streak = v.findViewById(R.id.lb_streak);
            total = v.findViewById(R.id.lb_total);
            avatar = v.findViewById(R.id.lb_avatar);
        }
    }
}
