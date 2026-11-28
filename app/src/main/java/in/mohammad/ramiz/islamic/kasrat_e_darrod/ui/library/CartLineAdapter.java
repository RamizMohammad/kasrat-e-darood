package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.library;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.Cart;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.CountRollTextView;

/** Cart lines in the review sheet: name, Arabic, − count + and remove. Edits go straight to the Cart. */
public class CartLineAdapter extends RecyclerView.Adapter<CartLineAdapter.Holder> {

    private List<Cart.Line> lines = new ArrayList<>();

    public void setLines(@NonNull List<Cart.Line> next) {
        List<Cart.Line> prev = lines;
        lines = next;
        if (prev.size() != next.size()) {
            notifyDataSetChanged();
            return;
        }
        for (int i = 0; i < next.size(); i++) {
            if (!prev.get(i).id.equals(next.get(i).id)) {
                notifyDataSetChanged();
                return;
            }
        }
        notifyItemRangeChanged(0, next.size(), RecitationCardAdapter.CART);   // counts only
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cart_line, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        bind(h, lines.get(position), false);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position, @NonNull List<Object> payloads) {
        bind(h, lines.get(position), !payloads.isEmpty());
    }

    private void bind(@NonNull Holder h, @NonNull Cart.Line l, boolean animate) {
        h.name.setText(l.englishName);
        h.arabic.setText(l.arabicName);
        h.arabic.setVisibility(l.arabicName == null || l.arabicName.isEmpty() ? View.GONE : View.VISIBLE);
        h.count.setCount(l.count, animate && h.count.hasCount());
        h.plus.setOnClickListener(v -> {
            Haptics.tick(v.getContext());
            Cart.get().add(l.id, l.arabicName, l.englishName, l.step);
        });
        h.minus.setOnClickListener(v -> {
            Haptics.tick(v.getContext());
            Cart.get().subtract(l.id);
        });
        h.remove.setOnClickListener(v -> Cart.get().remove(l.id));
    }

    @Override
    public int getItemCount() { return lines.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name, arabic;
        final CountRollTextView count;
        final View plus, minus, remove;

        Holder(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.line_name);
            arabic = v.findViewById(R.id.line_arabic);
            count = v.findViewById(R.id.line_count);
            plus = v.findViewById(R.id.line_plus);
            minus = v.findViewById(R.id.line_minus);
            remove = v.findViewById(R.id.line_remove);
        }
    }
}
