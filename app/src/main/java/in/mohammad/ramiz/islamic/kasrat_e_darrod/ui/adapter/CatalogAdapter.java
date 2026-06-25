package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter;

import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.Cart;

/** Category-grouped recitation catalog with a per-item quantity stepper. */
public class CatalogAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    /** A recitation displayed in the catalog. */
    public static final class Item {
        public final String id, urduName, englishName, sub;
        public final int iconRes, step;
        public Item(String id, String urduName, String englishName, String sub,
                    int iconRes, int step) {
            this.id = id; this.urduName = urduName; this.englishName = englishName;
            this.sub = sub; this.iconRes = iconRes; this.step = Math.max(1, step);
        }
    }

    /** A row is either a category header or a recitation item. */
    public static final class Row {
        final boolean header;
        final String title;     // header label
        final int headerCount;  // header item count
        final Item item;        // item payload
        private Row(boolean header, String title, int headerCount, Item item) {
            this.header = header; this.title = title; this.headerCount = headerCount; this.item = item;
        }
        public static Row header(String title, int count) { return new Row(true, title, count, null); }
        public static Row item(Item it) { return new Row(false, null, 0, it); }
    }

    private final List<Row> rows = new ArrayList<>();

    public void submit(List<Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).header ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderVH(inf.inflate(R.layout.item_catalog_header, parent, false));
        }
        return new ItemVH(inf.inflate(R.layout.item_catalog, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (row.header) {
            HeaderVH h = (HeaderVH) holder;
            h.title.setText(row.title);
            h.count.setText(String.valueOf(row.headerCount));
        } else {
            ((ItemVH) holder).bind(row.item);
        }
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

    class ItemVH extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView urdu, english, sub, count, minus, plus;

        ItemVH(@NonNull View v) {
            super(v);
            icon = v.findViewById(R.id.cat_icon);
            urdu = v.findViewById(R.id.cat_urdu);
            english = v.findViewById(R.id.cat_english);
            sub = v.findViewById(R.id.cat_sub);
            count = v.findViewById(R.id.cat_count);
            minus = v.findViewById(R.id.btn_minus);
            plus = v.findViewById(R.id.btn_plus);
        }

        void bind(Item it) {
            icon.setImageResource(it.iconRes);
            urdu.setText(it.urduName);
            english.setText(it.englishName);
            sub.setText(it.sub);
            refresh(it);

            minus.setOnClickListener(v -> {
                int c = Cart.get().countFor(it.id);
                setCount(it, Math.max(0, c - it.step));
            });
            plus.setOnClickListener(v -> {
                int c = Cart.get().countFor(it.id);
                setCount(it, c + it.step);
            });
            count.setOnClickListener(v -> promptExact(it));
        }

        private void refresh(Item it) {
            count.setText(String.valueOf(Cart.get().countFor(it.id)));
        }

        private void setCount(Item it, int value) {
            Cart.get().set(it.id, it.urduName, it.englishName, value);
            refresh(it);
        }

        private void promptExact(Item it) {
            EditText input = new EditText(itemView.getContext());
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            int current = Cart.get().countFor(it.id);
            if (current > 0) input.setText(String.valueOf(current));
            input.setHint("0");

            new AlertDialog.Builder(itemView.getContext())
                    .setTitle(it.englishName)
                    .setMessage(R.string.enter_times_recited)
                    .setView(input)
                    .setPositiveButton(R.string.set, (d, w) -> {
                        int value = 0;
                        try { value = Integer.parseInt(input.getText().toString().trim()); }
                        catch (NumberFormatException ignored) {}
                        setCount(it, Math.max(0, value));
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
    }
}
