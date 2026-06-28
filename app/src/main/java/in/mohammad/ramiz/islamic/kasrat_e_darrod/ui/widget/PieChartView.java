package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Lightweight donut/pie chart drawn with Canvas — no external dependency. */
public class PieChartView extends View {

    public static class Slice {
        public final String label;
        public final float value;
        public final int color;
        public Slice(String label, float value, int color) {
            this.label = label; this.value = value; this.color = color;
        }
    }

    private final List<Slice> slices = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float total = 0f;
    private Integer holeColorOverride = null;

    /** Override the donut-hole colour so it blends with a custom background. */
    public void setHoleColor(int color) {
        this.holeColorOverride = color;
        invalidate();
    }

    public PieChartView(Context c) { super(c); init(); }
    public PieChartView(Context c, @Nullable AttributeSet a) { super(c, a); init(); }

    private void init() {
        paint.setStyle(Paint.Style.FILL);
    }

    public void setData(List<Slice> data) {
        slices.clear();
        total = 0f;
        if (data != null) {
            slices.addAll(data);
            for (Slice s : data) total += s.value;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        int size = Math.min(w, h);
        float pad = size * 0.06f;
        float cx = w / 2f, cy = h / 2f;
        rect.set(cx - size / 2f + pad, cy - size / 2f + pad,
                cx + size / 2f - pad, cy + size / 2f - pad);

        if (total <= 0f) {
            paint.setColor(0xFFE3E3DE);
            canvas.drawArc(rect, 0, 360, true, paint);
        } else {
            float start = -90f;
            for (Slice s : slices) {
                float sweep = 360f * (s.value / total);
                paint.setColor(s.color);
                canvas.drawArc(rect, start, sweep, true, paint);
                start += sweep;
            }
        }

        // Donut hole.
        paint.setColor(0x00000000);
        float holeR = (rect.width() / 2f) * 0.55f;
        Paint hole = new Paint(Paint.ANTI_ALIAS_FLAG);
        hole.setColor(resolveSurface());
        canvas.drawCircle(cx, cy, holeR, hole);
    }

    private int resolveSurface() {
        if (holeColorOverride != null) return holeColorOverride;
        // Use the themed surface color so the hole blends in light/dark mode.
        android.util.TypedValue tv = new android.util.TypedValue();
        if (getContext().getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorSurface, tv, true)) {
            return tv.data;
        }
        return 0xFFFAFAF5;
    }
}
