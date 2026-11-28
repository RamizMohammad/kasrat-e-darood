package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;

/** Thin progress ring (Stitch home hero, tasbih): hairline track, deep-teal arc from 12 o'clock, round cap. */
public class KRingView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private float progress;

    public KRingView(Context context) { this(context, null); }

    public KRingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float dp = getResources().getDisplayMetrics().density;
        for (Paint p : new Paint[]{track, arc}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3 * dp);
        }
        arc.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(ContextCompat.getColor(context, R.color.hairline));
        arc.setColor(ContextCompat.getColor(context, R.color.amber));
    }

    public void setStrokeWidthDp(float w) {
        float px = w * getResources().getDisplayMetrics().density;
        track.setStrokeWidth(px);
        arc.setStrokeWidth(px);
        invalidate();
    }

    /** 0–1 (clamped). */
    public void setProgress(float f) {
        progress = Math.max(0f, Math.min(1f, f));
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas c) {
        float half = track.getStrokeWidth() / 2f;
        // r = 45 of 104 in Stitch: inset so the ring sits a little inside the box.
        float inset = Math.max(half, getWidth() * (52f - 45f) / 104f);
        box.set(inset, inset, getWidth() - inset, getHeight() - inset);
        c.drawOval(box, track);
        if (progress > 0f) c.drawArc(box, -90f, 360f * progress, false, arc);
    }
}
