package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Motion;

/**
 * Tasbih ring (Stitch): a honey arc on a sand track, with quarter pips and a
 * saffron knob at its tip, around a white dial. It fills a little with every tap
 * toward a target (33, 100, 1,000 or custom), starting at the top. A completed
 * round shows as a full teal ring for a moment, then the next round starts from
 * empty. It fills clockwise, or counter-clockwise in right-to-left languages.
 *
 * The count itself is drawn by a separate CountRollTextView laid over the centre.
 */
public class CounterRingView extends View {

    private static final long STEP_MS = 90;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pip = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dial = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dialRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final int honey, done;
    private final float dp;

    private int target = 33;
    private int count;
    private float shown;            // 0..1 of the current round, animated
    @Nullable private ValueAnimator anim;

    public CounterRingView(@NonNull Context context) {
        this(context, null);
    }

    public CounterRingView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        float stroke = dp * 12;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(stroke);
        track.setColor(ContextCompat.getColor(context, R.color.surface_container_high));
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(stroke);
        arc.setStrokeCap(Paint.Cap.ROUND);
        honey = ContextCompat.getColor(context, R.color.tertiary_fixed_dim);
        done = ContextCompat.getColor(context, R.color.secondary);
        dot.setColor(ContextCompat.getColor(context, R.color.amber));
        knobEdge.setColor(ContextCompat.getColor(context, R.color.surface_lowest));
        pip.setColor(ContextCompat.getColor(context, R.color.surface_lowest));
        dial.setColor(ContextCompat.getColor(context, R.color.surface_lowest));
        dial.setShadowLayer(dp * 16, 0, dp * 6, ContextCompat.getColor(context, R.color.shadow_warm));
        dialRing.setStyle(Paint.Style.STROKE);
        dialRing.setStrokeWidth(dp);
        dialRing.setColor(ContextCompat.getColor(context, R.color.surface_container));
        setLayerType(LAYER_TYPE_SOFTWARE, null);      // for the dial's soft shadow
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setTarget(int target) {
        this.target = Math.max(1, target);
        jumpTo(fraction(count));
    }

    public int getTarget() { return target; }

    /** True when {@code n} is a whole number of rounds (and not zero). */
    public boolean isAtTarget(int n) { return n > 0 && n % target == 0; }

    public void setCount(int n, boolean animate) {
        count = Math.max(0, n);
        float to = fraction(count);
        if (!animate || !Motion.enabled() || !isLaidOut()) {
            jumpTo(to);
            return;
        }
        if (anim != null) anim.cancel();
        float startAt = to < shown ? 0f : shown;   // a new round starts from empty
        anim = ValueAnimator.ofFloat(startAt, to);
        anim.setDuration(STEP_MS);
        anim.setInterpolator(new FastOutSlowInInterpolator());
        anim.addUpdateListener(a -> {
            shown = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    private float fraction(int n) {
        if (n <= 0) return 0f;
        int inRound = n % target;
        return inRound == 0 ? 1f : inRound / (float) target;
    }

    private void jumpTo(float f) {
        if (anim != null) anim.cancel();
        shown = f;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float half = track.getStrokeWidth() / 2f;
        float size = Math.min(getWidth() - getPaddingLeft() - getPaddingRight(),
                getHeight() - getPaddingTop() - getPaddingBottom());
        float left = getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight() - size) / 2f;
        float top = getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom() - size) / 2f;
        box.set(left + half, top + half, left + size - half, top + size - half);
        float cx = box.centerX(), cy = box.centerY(), r = box.width() / 2f;

        // White dial: 184 / 256 of the ring, with a hairline inner ring.
        float dialR = size * (184f / 256f) / 2f;
        canvas.drawCircle(cx, cy, dialR, dial);
        canvas.drawCircle(cx, cy, dialR - dp * 14, dialRing);

        canvas.drawOval(box, track);
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float frac = Math.min(1f, Math.max(0f, shown));
        if (frac > 0f) {
            arc.setColor(frac >= 1f ? done : honey);
            float sweep = 360f * frac * (rtl ? -1f : 1f);
            canvas.drawArc(box, -90f, sweep, false, arc);
        }
        // Quarter pips on the track.
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(-90 + 90 * i);
            canvas.drawCircle(cx + (float) (r * Math.cos(a)), cy + (float) (r * Math.sin(a)), dp * 3.5f, pip);
        }
        // Knob at the tip of the arc.
        if (frac > 0f && frac < 1f) {
            double a = Math.toRadians(-90 + 360f * frac * (rtl ? -1f : 1f));
            float kx = cx + (float) (r * Math.cos(a)), ky = cy + (float) (r * Math.sin(a));
            canvas.drawCircle(kx, ky, dp * 10, knobEdge);
            canvas.drawCircle(kx, ky, dp * 7.5f, dot);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }
}
