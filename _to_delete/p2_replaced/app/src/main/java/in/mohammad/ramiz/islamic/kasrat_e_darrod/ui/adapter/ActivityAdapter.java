package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.model.ActivityItem;

public class ActivityAdapter extends RecyclerView.Adapter<ActivityAdapter.VH> {
    private final List<ActivityItem> items;

    public ActivityAdapter(List<ActivityItem> items) { this.items = items; }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_activity, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ActivityItem a = items.get(position);
        h.avatar.setText(a.initial);
        h.text.setText(a.text);
        h.meta.setText(a.meta);
        h.time.setText(a.time);
        h.avatar.setBackgroundResource(a.goldAvatar
                ? R.drawable.bg_avatar_gold : R.drawable.bg_avatar_green);
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView avatar, text, meta, time;
        VH(@NonNull View v) {
            super(v);
            avatar = v.findViewById(R.id.activity_avatar);
            text = v.findViewById(R.id.activity_text);
            meta = v.findViewById(R.id.activity_meta);
            time = v.findViewById(R.id.activity_time);
        }
    }
}
