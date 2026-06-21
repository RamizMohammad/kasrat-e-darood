package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.model.FeedPost;

public class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.VH> {
    private final List<FeedPost> items;

    public FeedAdapter(List<FeedPost> items) { this.items = items; }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_feed, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        FeedPost p = items.get(position);
        h.avatar.setText(p.initial);
        h.name.setText(p.name);
        h.time.setText(p.time);
        h.title.setText(p.title);
        h.react1.setText(String.valueOf(p.reactionFire));
        h.react2.setText(String.valueOf(p.reactionHeart));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView avatar, name, time, title, react1, react2;
        VH(@NonNull View v) {
            super(v);
            avatar = v.findViewById(R.id.feed_avatar);
            name = v.findViewById(R.id.feed_name);
            time = v.findViewById(R.id.feed_time);
            title = v.findViewById(R.id.feed_title);
            react1 = v.findViewById(R.id.feed_react1);
            react2 = v.findViewById(R.id.feed_react2);
        }
    }
}
