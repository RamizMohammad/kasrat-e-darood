package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.library;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Objects;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.Cart;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.RecitationStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.CountRollTextView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Numbers;

/** Library list of RecitationCards. Card tap → detail; chip / stepper → cart. */
public class RecitationCardAdapter extends ListAdapter<dto.RecitationDto, RecitationCardAdapter.Holder> {

    public interface OnOpen { void onOpen(@NonNull dto.RecitationDto r); }

    /** Heart tapped: the fragment refilters when the Favourites tab is open. */
    public interface OnFavourite { void onFavourite(@NonNull dto.RecitationDto r, boolean on); }

    /** Payload for "only the cart count changed": rebinds the stepper, not the text. */
    static final Object CART = new Object();

    private final String lang;
    private final OnOpen onOpen;
    @Nullable private OnFavourite onFavourite;

    public RecitationCardAdapter(@NonNull String lang, @NonNull OnOpen onOpen) {
        super(DIFF);
        this.lang = lang;
        this.onOpen = onOpen;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        String id = getItem(position).id;
        return id != null ? id.hashCode() : position;
    }

    public void setOnFavourite(@Nullable OnFavourite l) { onFavourite = l; }

    /** The cart changed somewhere: refresh every card's chip/stepper. */
    public void cartChanged() {
        notifyItemRangeChanged(0, getItemCount(), CART);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recitation_card, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        h.bind(getItem(position));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(CART)) h.bindCart(getItem(position), true);
        else h.bind(getItem(position));
    }

    final class Holder extends RecyclerView.ViewHolder {
        final View card, stepper;
        final android.widget.ImageView heart;
        final TextView category, arabic, translit, name, meaning, add;
        final CountRollTextView count;

        Holder(@NonNull View v) {
            super(v);
            card = v.findViewById(R.id.card);
            category = v.findViewById(R.id.card_category);
            arabic = v.findViewById(R.id.card_arabic);
            translit = v.findViewById(R.id.card_transliteration);
            name = v.findViewById(R.id.card_name);
            meaning = v.findViewById(R.id.card_meaning);
            add = v.findViewById(R.id.card_add);
            stepper = v.findViewById(R.id.card_stepper);
            count = v.findViewById(R.id.card_count);
            heart = v.findViewById(R.id.card_heart);
        }

        void bind(@NonNull dto.RecitationDto r) {
            category.setTag(r.category);
            show(arabic, r.arabicName);
            show(translit, r.transliteration);
            name.setText(RecitationStore.title(r, lang));
            show(meaning, r.translation);
            add.setText("+" + Numbers.format(RecitationStore.step(r)));
            add.setContentDescription(add.getContext().getString(R.string.recite_add_cd,
                    RecitationStore.step(r), name.getText()));

            card.setOnClickListener(x -> onOpen.onOpen(r));
            add.setOnClickListener(x -> {
                Haptics.tick(x.getContext());
                Cart.get().add(r.id, r.arabicName, RecitationStore.title(r, lang), RecitationStore.step(r));
            });
            itemView.findViewById(R.id.card_plus).setOnClickListener(x -> {
                Haptics.tick(x.getContext());
                Cart.get().add(r.id, r.arabicName, RecitationStore.title(r, lang), RecitationStore.step(r));
            });
            itemView.findViewById(R.id.card_minus).setOnClickListener(x -> {
                Haptics.tick(x.getContext());
                Cart.get().subtract(r.id);
            });
            bindHeart(r);
            heart.setOnClickListener(x -> {
                Haptics.tick(x.getContext());
                boolean on = new in.mohammad.ramiz.islamic.kasrat_e_darrod.util.Favourites(x.getContext()).toggle(r.id);
                bindHeart(r);
                if (onFavourite != null) onFavourite.onFavourite(r, on);
            });
            bindCart(r, false);
        }

        /** Stitch: teal heart for a favourite, warm grey otherwise. */
        void bindHeart(@NonNull dto.RecitationDto r) {
            boolean fav = new in.mohammad.ramiz.islamic.kasrat_e_darrod.util.Favourites(heart.getContext()).has(r.id);
            heart.setImageTintList(android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(heart.getContext(),
                            fav ? R.color.amber : R.color.text_muted)));
            heart.setContentDescription(heart.getContext().getString(
                    fav ? R.string.recite_fav_remove : R.string.recite_fav_add));
        }

        void bindCart(@NonNull dto.RecitationDto r, boolean animate) {
            int c = Cart.get().countFor(r.id);
            boolean inCart = c > 0;
            add.setVisibility(inCart ? View.GONE : View.VISIBLE);
            stepper.setVisibility(inCart ? View.VISIBLE : View.GONE);
            card.setBackgroundResource(inCart ? R.drawable.bg_k_card_session : R.drawable.bg_k_card);
            // Eyebrow: "DAROOD" → "DAROOD · IN SESSION" while it's in the log.
            String cat = (String) category.getTag();
            if (TextUtils.isEmpty(cat)) {
                category.setVisibility(View.GONE);
            } else {
                category.setVisibility(View.VISIBLE);
                category.setText(inCart ? category.getContext().getString(R.string.recite_in_session, cat) : cat);
            }
            if (inCart) count.setCount(c, animate && count.hasCount());
            else count.clearCount();
        }

        private void show(TextView tv, String text) {
            tv.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
            tv.setText(text);
        }
    }

    private static final DiffUtil.ItemCallback<dto.RecitationDto> DIFF = new DiffUtil.ItemCallback<dto.RecitationDto>() {
        @Override
        public boolean areItemsTheSame(@NonNull dto.RecitationDto a, @NonNull dto.RecitationDto b) {
            return Objects.equals(a.id, b.id);
        }

        @Override
        public boolean areContentsTheSame(@NonNull dto.RecitationDto a, @NonNull dto.RecitationDto b) {
            return Objects.equals(a.arabicName, b.arabicName)
                    && Objects.equals(a.englishName, b.englishName)
                    && Objects.equals(a.urduName, b.urduName)
                    && Objects.equals(a.transliteration, b.transliteration)
                    && Objects.equals(a.translation, b.translation)
                    && Objects.equals(a.category, b.category)
                    && a.defaultIncrement == b.defaultIncrement;
        }
    };
}
