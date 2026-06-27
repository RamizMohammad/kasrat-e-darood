package in.mohammad.ramiz.islamic.kasrat_e_darrod.util;

import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;

/** Loads the app's branded loading GIF (res/raw/star_loader.gif) into a view. */
public final class GifLoader {
    private GifLoader() {}

    /** Start the loading GIF in the given ImageView and make it visible. */
    public static void show(ImageView view) {
        if (view == null) return;
        view.setVisibility(View.VISIBLE);
        Glide.with(view.getContext()).asGif().load(R.raw.star_loader).into(view);
    }

    /** Hide the loading view and stop rendering the GIF. */
    public static void hide(ImageView view) {
        if (view == null) return;
        Glide.with(view.getContext()).clear(view);
        view.setVisibility(View.GONE);
    }
}
