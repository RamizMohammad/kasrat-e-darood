package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;

/**
 * Stitch tasbih ring (264dp box): a soft glow, a dotted outer ring (r 124, 2px, dashes
 * 1.5 / 10.1), a 3px track (r 112) and the lap's progress from 12 o'clock, plus one 8dp
 * gold dot per finished lap above the ring. Day = deep teal on hairline; night = gold
 * on night-hairline (tasbih_counter_night_teal).
 */
public class TasbihRingView extends View {

    private final float dp;
    private final Paint dotted = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lap = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private float shown;            // 0–1, animated
    private int laps;
    private boolean night;
    @Nullable private ValueAnimator anim;

    public TasbihRingView(Context context) { this(context, null); }

    public TasbihRingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        dotted.setStyle(Paint.Style.STROKE);
        track.setStyle(Paint.Style.STROKE);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        setNight(false);
    }

    public void setNight(boolean on) {
        night = on;
        Context c = getContext();
        dotted.setColor(on ? 0x2EFAF7F2
                : (ContextCompat.getColor(c, R.color.text_primary) & 0x00FFFFFF) | 0x29000000);   // text/16 (dark theme too)           // ivory/18 · ink/16
        track.setColor(ContextCompat.getColor(c, on ? R.color.k_night_hairline : R.color.k_hairline));
        arc.setColor(ContextCompat.getColor(c, on ? R.color.k_gold : R.color.k_deep_teal));
        lap.setColor(ContextCompat.getColor(c, R.color.k_gold));
        glow.setShader(null);
        invalidate();
    }

    /** Progress through the current lap (0–1) and how many laps are done. */
    public void setProgress(float f, int lapsDone, boolean animate) {
        laps = lapsDone;
        float to = Math.max(0f, Math.min(1f, f));
        if (anim != null) anim.cancel();
        if (!animate || !isLaidOut()) {
            shown = to;
            invalidate();
            return;
        }
        anim = ValueAnimator.ofFloat(shown, to);
        anim.setDuration(120);
        anim.setInterpolator(AnimationUtils.loadInterpolator(getContext(), R.interpolator.k_surface_enter));
        anim.addUpdateListener(a -> {
            shown = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        glow.setShader(null);
    }

    @Override
    protected void onDraw(@NonNull Canvas c) {
        float size = Math.min(getWidth(), getHeight());
        float s = size / 264f;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;

        // Glow: radial teal → gold → transparent (night: gold → teal), inset 16 of 264.
        if (glow.getShader() == null) {
            int[] colors = night
                    ? new int[]{0x47C9A24B, 0x1F0F5E55, 0x00000000}
                    : new int[]{0x380F5E55, 0x24C9A24B, 0x00000000};
            float[] stops = night ? new float[]{0f, 0.55f, 0.78f} : new float[]{0f, 0.5f, 0.75f};
            glow.setShader(new RadialGradient(cx, cy, (132 - 16) * s, colors, stops, Shader.TileMode.CLAMP));
        }
        glow.setAlpha(night ? 102 : 140);
        c.drawCircle(cx, cy, (132 - 16) * s, glow);

        dotted.setStrokeWidth(2 * s);
        dotted.setPathEffect(new DashPathEffect(new float[]{1.5f * s, 10.1f * s}, 0));
        c.drawCircle(cx, cy, 124 * s, dotted);

        track.setStrokeWidth(3 * s);
        arc.setStrokeWidth(3 * s);
        float r = 112 * s;
        box.set(cx - r, cy - r, cx + r, cy + r);
        c.drawOval(box, track);
        if (shown > 0f) c.drawArc(box, -90f, 360f * shown, false, arc);

        // Finished laps: 8dp gold dots, 8dp apart, centred just above the ring.
        int n = Math.min(laps, 9);
        if (n > 0) {
            float d = 8 * dp, gap = 8 * dp;
            float total = n * d + (n - 1) * gap;
            float x = cx - total / 2f + d / 2f;
            float y = cy - 132 * s - 3 * dp + d / 2f;
            for (int i = 0; i < n; i++) {
                c.drawCircle(x, Math.max(d / 2f, y), d / 2f, lap);
                x += d + gap;
            }
        }
    }
}
