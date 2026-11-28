package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Stitch circle goal bar (circle_home_board_teal): a 6dp hairline track, a deep-teal fill
 * and three 1dp white ticks at 25 / 50 / 75%. Mirrors in right-to-left languages.
 */
public class KTickBarView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint();
    private final RectF r = new RectF();
    private final float dp;
    private float fraction;

    public KTickBarView(@NonNull Context c) { this(c, null); }

    public KTickBarView(@NonNull Context c, @Nullable AttributeSet a) {
        super(c, a);
        dp = getResources().getDisplayMetrics().density;
        track.setColor(androidx.core.content.ContextCompat.getColor(c, in.mohammad.ramiz.islamic.kasrat_e_darrod.R.color.hairline));
        fill.setColor(androidx.core.content.ContextCompat.getColor(c, in.mohammad.ramiz.islamic.kasrat_e_darrod.R.color.amber));
        tick.setColor(androidx.core.content.ContextCompat.getColor(c, in.mohammad.ramiz.islamic.kasrat_e_darrod.R.color.k_tick));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setFraction(float f) {
        fraction = Math.max(0f, Math.min(1f, f));
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), w), Math.round(6 * dp));
    }

    @Override
    protected void onDraw(@NonNull Canvas c) {
        float w = getWidth(), h = getHeight(), rad = h / 2f;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        r.set(0, 0, w, h);
        c.drawRoundRect(r, rad, rad, track);
        if (fraction > 0f) {
            float fw = Math.max(h, w * fraction);
            if (rtl) r.set(w - fw, 0, w, h); else r.set(0, 0, fw, h);
            c.drawRoundRect(r, rad, rad, fill);
        }
        for (float q : new float[]{0.25f, 0.5f, 0.75f}) {
            float x = w * q;
            c.drawRect(x, 0, x + Math.max(1f, dp), h, tick);
        }
    }
}
