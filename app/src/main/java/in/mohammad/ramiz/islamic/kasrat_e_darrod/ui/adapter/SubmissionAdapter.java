package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.RelativeTime;

/** Renders the current user's recent submissions on the profile screen. */
public class SubmissionAdapter extends RecyclerView.Adapter<SubmissionAdapter.VH> {

    private final List<dto.MySubmissionDto> items;

    public SubmissionAdapter(List<dto.MySubmissionDto> items) { this.items = items; }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_submission, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        dto.MySubmissionDto s = items.get(position);
        h.name.setText(s.recitationName != null ? s.recitationName : "Recitation");
        h.time.setText(RelativeTime.from(s.createdAt));
        h.count.setText("×" + s.count);
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name, time, count;
        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.sub_name);
            time = v.findViewById(R.id.sub_time);
            count = v.findViewById(R.id.sub_count);
        }
    }
}
