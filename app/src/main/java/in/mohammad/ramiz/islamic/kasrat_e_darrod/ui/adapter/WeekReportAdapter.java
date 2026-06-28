package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.RelativeTime;

/** Archived weeks, each tappable to download a PDF report. */
public class WeekReportAdapter extends RecyclerView.Adapter<WeekReportAdapter.VH> {

    public interface OnWeekClick {
        void onClick(dto.WeekSummary week);
    }

    private final List<dto.WeekSummary> items = new ArrayList<>();
    private final OnWeekClick listener;

    public WeekReportAdapter(List<dto.WeekSummary> data, OnWeekClick listener) {
        this.listener = listener;
        if (data != null) items.addAll(data);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_week_report, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        dto.WeekSummary w = items.get(position);

        h.title.setText(h.itemView.getContext().getString(R.string.week_label_fmt, w.weekNumber));

        String date = RelativeTime.asDate(w.closedAt);
        String label = w.label != null ? w.label : "";
        String meta;
        if (!date.isEmpty()) {
            String closed = h.itemView.getContext().getString(R.string.closed_on_fmt, date);
            meta = label.isEmpty() ? closed : label + "  ·  " + closed;
        } else {
            meta = label;
        }
        h.meta.setText(meta);
        h.meta.setVisibility(meta.isEmpty() ? View.GONE : View.VISIBLE);

        h.total.setText(NumberFormat.getIntegerInstance().format(w.total));

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(w);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title, meta, total;
        VH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.week_title);
            meta = v.findViewById(R.id.week_meta);
            total = v.findViewById(R.id.week_total);
        }
    }
}
