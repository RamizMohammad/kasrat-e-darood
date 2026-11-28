package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Motion;

/** "Logged" confirmation: a sage circle whose tick draws itself in 300 ms. */
public class TickView extends View {

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path full = new Path();
    private final Path part = new Path();
    private final PathMeasure measure = new PathMeasure();
    private float progress = 1f;
    @Nullable private ValueAnimator anim;

    public TickView(@NonNull Context context) {
        this(context, null);
    }

    public TickView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;
        ring.setColor(ContextCompat.getColor(context, R.color.sage_tint));
        tick.setStyle(Paint.Style.STROKE);
        tick.setStrokeWidth(4 * d);
        tick.setStrokeCap(Paint.Cap.ROUND);
        tick.setStrokeJoin(Paint.Join.ROUND);
        tick.setColor(ContextCompat.getColor(context, R.color.sage));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void play() {
        if (anim != null) anim.cancel();
        if (!Motion.enabled()) {
            progress = 1f;
            invalidate();
            return;
        }
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(300);
        anim.setInterpolator(new FastOutSlowInInterpolator());
        anim.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        progress = 0f;
        anim.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        full.reset();
        full.moveTo(w * 0.30f, h * 0.52f);
        full.lineTo(w * 0.44f, h * 0.66f);
        full.lineTo(w * 0.71f, h * 0.38f);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float r = Math.min(getWidth(), getHeight()) / 2f;
        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, r, ring);
        part.reset();
        measure.setPath(full, false);
        float len = 0f;
        do { len += measure.getLength(); } while (measure.nextContour());
        measure.setPath(full, false);
        float want = len * progress;
        // The tick is two strokes: draw them in order up to "want".
        do {
            float seg = measure.getLength();
            measure.getSegment(0, Math.min(seg, want), part, true);
            want -= seg;
        } while (want > 0 && measure.nextContour());
        canvas.drawPath(part, tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }
}
