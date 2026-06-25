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
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.Cart;

/** Renders the lines in the cart review sheet, with a remove action. */
public class CartLineAdapter extends RecyclerView.Adapter<CartLineAdapter.VH> {

    public interface OnRemove { void onRemove(Cart.Line line); }

    private final List<Cart.Line> lines;
    private final OnRemove onRemove;

    public CartLineAdapter(List<Cart.Line> lines, OnRemove onRemove) {
        this.lines = new ArrayList<>(lines);
        this.onRemove = onRemove;
    }

    public void setLines(List<Cart.Line> newLines) {
        lines.clear();
        lines.addAll(newLines);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cart_line, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Cart.Line line = lines.get(position);
        h.urdu.setText(line.urduName);
        h.english.setText(line.englishName);
        h.count.setText("×" + line.count);
        h.remove.setOnClickListener(v -> {
            if (onRemove != null) onRemove.onRemove(line);
        });
    }

    @Override
    public int getItemCount() { return lines.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView urdu, english, count, remove;
        VH(@NonNull View v) {
            super(v);
            urdu = v.findViewById(R.id.line_urdu);
            english = v.findViewById(R.id.line_english);
            count = v.findViewById(R.id.line_count);
            remove = v.findViewById(R.id.line_remove);
        }
    }
}
