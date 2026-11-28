package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;

/**
 * Stitch weekly report "Daily rhythm": seven 10dp rounded bars in a 128dp area (heights as a
 * share of the best day, which is gold with its value above it, e.g. "1.8k"), a day initial
 * under each. Days with nothing get a 6dp hairline stub. Mirrors in RTL.
 */
public class KDayBarsView extends View {

    private final int TEAL, GOLD, HAIRLINE, GREY, INK;

    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float dp;
    private long[] values = new long[7];
    private String[] initials = {"", "", "", "", "", "", ""};
    @Nullable private String bestLabel;

    public KDayBarsView(@NonNull Context c) { this(c, null); }

    public KDayBarsView(@NonNull Context c, @Nullable AttributeSet a) {
        super(c, a);
        TEAL = androidx.core.content.ContextCompat.getColor(c, R.color.amber);
        GOLD = androidx.core.content.ContextCompat.getColor(c, R.color.k_milestone);
        HAIRLINE = androidx.core.content.ContextCompat.getColor(c, R.color.hairline);
        GREY = androidx.core.content.ContextCompat.getColor(c, R.color.text_muted);
        INK = androidx.core.content.ContextCompat.getColor(c, R.color.text_primary);
        dp = getResources().getDisplayMetrics().density;
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(12 * getResources().getDisplayMetrics().scaledDensity);
        try {
            Typeface t = ResourcesCompat.getFont(c, R.font.jakarta);
            if (t != null) label.setTypeface(t);
        } catch (RuntimeException ignored) { }
    }

    /** @param bestLabel the best day's value as shown above its bar ("1.8k"). */
    public void setData(@NonNull long[] values, @NonNull String[] initials, @Nullable String bestLabel) {
        this.values = values;
        this.initials = initials;
        this.bestLabel = bestLabel;
        invalidate();
    }

    private int best() {
        int b = -1;
        for (int i = 0; i < values.length; i++) if (values[i] > 0 && (b < 0 || values[i] > values[b])) b = i;
        return b;
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), Math.round(128 * dp + 16 * dp + 8 * dp));
    }

    @Override
    protected void onDraw(@NonNull Canvas c) {
        int n = values.length;
        if (n == 0) return;
        float colW = getWidth() / (float) n;
        float labelH = 16 * dp, gap = 8 * dp, top = 16 * dp;     // pt-4
        float area = getHeight() - labelH - gap - top;
        float baseline = top + area;
        long max = 1;
        for (long v : values) max = Math.max(max, v);
        int best = best();
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        Paint.FontMetrics fm = label.getFontMetrics();
        for (int i = 0; i < n; i++) {
            float cx = ((rtl ? n - 1 - i : i) + 0.5f) * colW;
            boolean isBest = i == best;
            // The tallest bar reaches 84% so the best day's value fits above it (Stitch).
            float frac = values[i] <= 0 ? 0 : (float) values[i] / max * 0.84f;
            float h = values[i] <= 0 ? 6 * dp : Math.max(10 * dp, area * frac);
            bar.setColor(values[i] <= 0 ? HAIRLINE : isBest ? GOLD : TEAL);
            r.set(cx - 5 * dp, baseline - h, cx + 5 * dp, baseline);
            c.drawRoundRect(r, 5 * dp, 5 * dp, bar);
            if (isBest && bestLabel != null) {
                label.setColor(INK);
                c.drawText(bestLabel, cx, baseline - h - 6 * dp - fm.descent, label);
            }
            label.setColor(isBest ? INK : GREY);
            c.drawText(initials[i], cx, baseline + gap - fm.ascent, label);
        }
    }
}
