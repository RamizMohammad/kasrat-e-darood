package in.mohammad.ramiz.islamic.kasrat_e_darrod.util;

import android.widget.ImageView;

import com.bumptech.glide.Glide;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;

/** Loads the signed-in user's circular profile photo, or the default avatar. */
public final class Avatars {
    private Avatars() {}

    public static void load(ImageView view) {
        if (view == null) return;
        loadUrl(view, new TokenStore(view.getContext()).photoUrl());
    }

    /** Loads any user's circular profile photo by URL (or the default avatar). */
    public static void loadUrl(ImageView view, String url) {
        if (view == null) return;
        view.setBackground(null);
        view.setImageTintList(null);
        Glide.with(view.getContext())
                .load(url == null || url.isEmpty() ? null : url)
                .circleCrop()
                .placeholder(R.drawable.default_profile)
                .fallback(R.drawable.default_profile)
                .error(R.drawable.default_profile)
                .into(view);
    }
}
