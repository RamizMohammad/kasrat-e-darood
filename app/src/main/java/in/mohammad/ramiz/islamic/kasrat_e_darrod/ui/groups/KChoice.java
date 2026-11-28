package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.groups;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;

/** Fills an item_k_choice radio card (Stitch new_circle_teal privacy). */
final class KChoice {

    private KChoice() { }

    static void bind(@NonNull View card, @DrawableRes int icon, @StringRes int title, @StringRes int sub, boolean on) {
        ((TextView) card.findViewById(R.id.choice_title)).setText(title);
        ((TextView) card.findViewById(R.id.choice_sub)).setText(sub);
        ImageView ic = card.findViewById(R.id.choice_icon);
        ic.setImageResource(icon);
        // Chosen: deep-teal hairline, teal-soft disc, teal icon, check. Other: ivory disc, grey icon.
        card.setBackgroundResource(on ? R.drawable.bg_k_card_selected : R.drawable.bg_k_card);
        card.findViewById(R.id.choice_icon_disc).setBackgroundResource(on ? R.drawable.bg_k_emblem_soft : R.drawable.bg_k_avatar_disc);
        ic.setImageTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(card.getContext(), on ? R.color.amber : R.color.text_muted)));
        card.findViewById(R.id.choice_check).setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        card.setSelected(on);
        card.setContentDescription(card.getContext().getString(title) + ". " + card.getContext().getString(sub));
    }
}
