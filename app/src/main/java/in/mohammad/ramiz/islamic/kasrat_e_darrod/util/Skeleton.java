package in.mohammad.ramiz.islamic.kasrat_e_darrod.util;

import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;

/** Lightweight shimmer for skeleton placeholders: a looping alpha pulse. */
public final class Skeleton {
    private Skeleton() {}

    /** Starts a soft pulsing animation on the given skeleton container. */
    public static void shimmer(View view) {
        if (view == null) return;
        AlphaAnimation anim = new AlphaAnimation(1f, 0.35f);
        anim.setDuration(750);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        view.startAnimation(anim);
    }

    /** Stops the shimmer and hides the skeleton, revealing its content sibling. */
    public static void reveal(View skeleton, View content) {
        if (skeleton != null) {
            skeleton.clearAnimation();
            skeleton.setVisibility(View.GONE);
        }
        if (content != null) {
            content.setVisibility(View.VISIBLE);
        }
    }
}
