package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.text.Layout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Motion;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Numbers;

/**
 * A count in JetBrains Mono whose changed digits roll: the old digit slides out
 * and fades while the new one slides in (220 ms). Up when the number grows,
 * down when it shrinks. Digits that didn't change stay still.
 *
 * Needs a monospaced font (every glyph the same width), which the count text
 * appearances use. With "Remove animations" on, the number just changes.
 */
public class CountRollTextView extends AppCompatTextView {

    private static final long ROLL_MS = 220;

    private long value;
    private boolean hasValue;
    private String from = "", to = "";
    private float progress = 1f;
    private boolean up = true;
    @Nullable private ValueAnimator roll;

    public CountRollTextView(@NonNull Context context) {
        super(context);
        init();
    }

    public CountRollTextView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CountRollTextView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setTextDirection(View.TEXT_DIRECTION_LTR);   // digits read left to right in every locale
        setMaxLines(1);
    }

    public long getCount() { return value; }

    public boolean hasCount() { return hasValue; }

    /** Shows {@code n}; rolls from the current number when {@code animate} and one is showing. */
    public void setCount(long n, boolean animate) {
        String next = Numbers.format(n);
        boolean canRoll = animate && hasValue && Motion.enabled() && isLaidOut() && n != value;
        if (roll != null) roll.cancel();
        if (!canRoll) {
            value = n;
            hasValue = true;
            progress = 1f;
            setText(next);
            return;
        }
        from = getText().toString();
        to = next;
        up = n > value;
        value = n;
        setText(next);
        roll = ValueAnimator.ofFloat(0f, 1f);
        roll.setDuration(ROLL_MS);
        roll.setInterpolator(new FastOutSlowInInterpolator());
        roll.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        progress = 0f;
        roll.start();
    }

    /** A placeholder ("—") until the first number arrives. */
    public void clearCount() {
        if (roll != null) roll.cancel();
        hasValue = false;
        progress = 1f;
        setText("—");
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        Layout layout = getLayout();
        if (progress >= 1f || layout == null || layout.getLineCount() != 1) {
            super.onDraw(canvas);
            return;
        }
        TextPaint p = getPaint();
        p.setColor(getCurrentTextColor());
        int baseAlpha = p.getAlpha();
        float baseline = getTotalPaddingTop() + layout.getLineBaseline(0);
        float right = getTotalPaddingLeft() + layout.getLineRight(0);
        float cell = p.measureText("0");
        float shift = p.getFontSpacing() * 0.85f * (up ? 1f : -1f);

        canvas.save();
        canvas.clipRect(getPaddingLeft(), getPaddingTop(),
                getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
        int n = Math.max(from.length(), to.length());
        for (int i = 0; i < n; i++) {
            char a = i < from.length() ? from.charAt(from.length() - 1 - i) : 0;
            char b = i < to.length() ? to.charAt(to.length() - 1 - i) : 0;
            float x = right - (i + 1) * cell;
            if (a == b) {
                draw(canvas, p, b, x, baseline, baseAlpha);
            } else {
                draw(canvas, p, a, x, baseline - shift * progress, Math.round(baseAlpha * (1f - progress)));
                draw(canvas, p, b, x, baseline + shift * (1f - progress), Math.round(baseAlpha * progress));
            }
        }
        canvas.restore();
        p.setAlpha(baseAlpha);
    }

    private static void draw(Canvas c, TextPaint p, char ch, float x, float y, int alpha) {
        if (ch == 0 || alpha <= 0) return;
        p.setAlpha(alpha);
        c.drawText(String.valueOf(ch), x, y, p);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (roll != null) roll.cancel();
        progress = 1f;
        super.onDetachedFromWindow();
    }
}
