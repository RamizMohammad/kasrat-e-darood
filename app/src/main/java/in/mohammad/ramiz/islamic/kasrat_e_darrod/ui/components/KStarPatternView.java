package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Stitch's faint Islamic 8-point star pattern (weekly report, share story): a 64-unit tile with
 * a four-point star and a square turned 45°, deep ink at 4% — set the alpha on the view, the
 * strokes are drawn opaque. One unit = 1dp.
 */
public class KStarPatternView extends View {

    private final Paint star = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint square = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path starPath = new Path();
    private final float dp;

    public KStarPatternView(@NonNull Context c) { this(c, null); }

    public KStarPatternView(@NonNull Context c, @Nullable AttributeSet a) {
        super(c, a);
        dp = getResources().getDisplayMetrics().density;
        star.setStyle(Paint.Style.STROKE);
        star.setStrokeWidth(dp);
        star.setColor(androidx.core.content.ContextCompat.getColor(c, in.mohammad.ramiz.islamic.kasrat_e_darrod.R.color.text_primary));
        square.set(star);
        square.setStrokeWidth(0.75f * dp);
        float[][] pts = {{32, 4}, {39.5f, 24.5f}, {60, 32}, {39.5f, 39.5f}, {32, 60}, {24.5f, 39.5f}, {4, 32}, {24.5f, 24.5f}};
        starPath.moveTo(pts[0][0] * dp, pts[0][1] * dp);
        for (int i = 1; i < pts.length; i++) starPath.lineTo(pts[i][0] * dp, pts[i][1] * dp);
        starPath.close();
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(@NonNull Canvas c) {
        float tile = 64 * dp;
        for (float y = 0; y < getHeight(); y += tile) {
            for (float x = 0; x < getWidth(); x += tile) {
                c.save();
                c.translate(x, y);
                c.drawPath(starPath, star);
                c.rotate(45, 32 * dp, 32 * dp);
                c.drawRect(14 * dp, 14 * dp, 50 * dp, 50 * dp, square);
                c.restore();
            }
        }
    }
}
