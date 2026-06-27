package in.mohammad.ramiz.islamic.kasrat_e_darrod.util;

import android.view.animation.AnimationUtils;

import androidx.recyclerview.widget.RecyclerView;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;

/** Small animation helpers for a livelier UX. */
public final class Anims {
    private Anims() {}

    /** Plays a staggered fall-down/fade-in over a RecyclerView's current items. */
    public static void fallDown(RecyclerView rv) {
        if (rv == null) return;
        rv.setLayoutAnimation(
                AnimationUtils.loadLayoutAnimation(rv.getContext(),
                        R.anim.layout_animation_fall_down));
        rv.scheduleLayoutAnimation();
    }
}
