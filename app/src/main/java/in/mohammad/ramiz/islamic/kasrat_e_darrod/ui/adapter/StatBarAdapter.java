package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;

/**
 * Per-recitation horizontal bars, optionally grouped under category headers.
 * Bar fill is proportional to the highest single recitation count.
 */
public class StatBarAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_BAR = 1;

    /** A row is either a category header (label set) or a recitation bar (stat set). */
    private static final class Row {
        final String header;            // category header text, or null
        final int headerCount;          // total count for that category
        final dto.RecitationStat stat;  // bar data, or null
        Row(String header, int headerCount) {
            this.header = header; this.headerCount = headerCount; this.stat = null;
        }
        Row(dto.RecitationStat stat) {
            this.header = null; this.headerCount = 0; this.stat = stat;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private int max = 1;

    /** Flat list (no grouping). */
    public void submit(List<dto.RecitationStat> data) {
        rows.clear();
        max = 1;
        if (data != null) {
            for (dto.RecitationStat r : data) {
                rows.add(new Row(r));
                max = Math.max(max, r.count);
            }
        }
        notifyDataSetChanged();
    }

    /** Grouped by category: a header per category (ordered by total desc), bars within. */
    public void submitGrouped(List<dto.RecitationStat> data) {
        rows.clear();
        max = 1;
        if (data == null || data.isEmpty()) {
            notifyDataSetChanged();
            return;
        }

        // Bucket by category (preserve first-seen order), summing totals.
        Map<String, List<dto.RecitationStat>> buckets = new LinkedHashMap<>();
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (dto.RecitationStat r : data) {
            String cat = (r.category != null && !r.category.isEmpty()) ? r.category : "Other";
            buckets.computeIfAbsent(cat, k -> new ArrayList<>()).add(r);
            totals.put(cat, totals.getOrDefault(cat, 0) + r.count);
            max = Math.max(max, r.count);
        }

        // Order categories by total descending.
        List<String> cats = new ArrayList<>(buckets.keySet());
        cats.sort((a, b) -> Integer.compare(totals.get(b), totals.get(a)));

        for (String cat : cats) {
            rows.add(new Row(cat, totals.get(cat)));
            List<dto.RecitationStat> items = buckets.get(cat);
            items.sort((a, b) -> Integer.compare(b.count, a.count));
            for (dto.RecitationStat r : items) rows.add(new Row(r));
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).header != null ? TYPE_HEADER : TYPE_BAR;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderVH(inf.inflate(R.layout.item_stat_header, parent, false));
        }
        return new BarVH(inf.inflate(R.layout.item_stat_bar, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof HeaderVH) {
            HeaderVH h = (HeaderVH) holder;
            h.title.setText(row.header);
            h.count.setText(NumberFormat.getIntegerInstance().format(row.headerCount));
            return;
        }
        BarVH h = (BarVH) holder;
        dto.RecitationStat r = row.stat;
        h.name.setText(r.name != null ? r.name : "Recitation");
        h.count.setText(NumberFormat.getIntegerInstance().format(r.count));

        float fill = Math.max(0.02f, (float) r.count / (float) max);
        setWeight(h.fill, fill);
        setWeight(h.empty, 1f - fill);
    }

    private void setWeight(View v, float weight) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
        lp.weight = weight;
        v.setLayoutParams(lp);
    }

    @Override
    public int getItemCount() { return rows.size(); }

    static class HeaderVH extends RecyclerView.ViewHolder {
        final TextView title, count;
        HeaderVH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.header_title);
            count = v.findViewById(R.id.header_count);
        }
    }

    static class BarVH extends RecyclerView.ViewHolder {
        final TextView name, count;
        final View fill, empty;
        BarVH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.bar_name);
            count = v.findViewById(R.id.bar_count);
            fill = v.findViewById(R.id.bar_fill);
            empty = v.findViewById(R.id.bar_empty);
        }
    }
}
