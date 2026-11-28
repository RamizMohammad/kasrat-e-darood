package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.Skeleton;

/** Shows a fixed number of shimmering placeholder rows while content loads. */
public class SkeletonAdapter extends RecyclerView.Adapter<SkeletonAdapter.VH> {
    private final int count;

    public SkeletonAdapter(int count) { this.count = count; }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_skeleton_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Skeleton.shimmer(holder.itemView);
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        holder.itemView.clearAnimation();
    }

    @Override
    public int getItemCount() { return count; }

    static class VH extends RecyclerView.ViewHolder {
        VH(@NonNull View v) { super(v); }
    }
}
