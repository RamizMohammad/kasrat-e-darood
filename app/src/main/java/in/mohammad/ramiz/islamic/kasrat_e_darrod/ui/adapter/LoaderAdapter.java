package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.GifLoader;

/** A single centered branded GIF, used as the loading state for any list. */
public class LoaderAdapter extends RecyclerView.Adapter<LoaderAdapter.VH> {

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_loader, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        GifLoader.show(holder.gif);
    }

    @Override
    public int getItemCount() { return 1; }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView gif;
        VH(@NonNull View v) {
            super(v);
            gif = v.findViewById(R.id.loader_gif);
        }
    }
}
