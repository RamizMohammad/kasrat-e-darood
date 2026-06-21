package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.model.Recitation;

public class RecitationAdapter extends RecyclerView.Adapter<RecitationAdapter.VH> {
    public interface OnClick { void onClick(Recitation r); }

    private final List<Recitation> items;
    private final OnClick listener;

    public RecitationAdapter(List<Recitation> items, OnClick listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recitation, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Recitation r = items.get(position);
        h.arabic.setText(r.arabicName);
        h.english.setText(r.englishName);
        h.translation.setText(r.translation);
        h.tag.setText(r.tag);
        h.icon.setImageResource(r.iconRes);
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(r);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView arabic, english, translation, tag;
        ImageView icon;
        VH(@NonNull View v) {
            super(v);
            arabic = v.findViewById(R.id.rec_arabic);
            english = v.findViewById(R.id.rec_english);
            translation = v.findViewById(R.id.rec_translation);
            tag = v.findViewById(R.id.rec_tag);
            icon = v.findViewById(R.id.rec_icon);
        }
    }
}
