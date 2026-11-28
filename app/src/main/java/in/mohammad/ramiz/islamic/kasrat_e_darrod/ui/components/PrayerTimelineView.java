package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;

/**
 * Stitch home_teal "Prayer timeline": five 16dp nodes on a hairline, the part already
 * prayed drawn in deep-ink/30. Past nodes are deep ink, the next prayer is deep teal
 * with a 4dp teal-soft ring and its time + countdown underneath, later ones are white
 * with a hairline. Labels are label-sm (Jakarta 12sp / 500).
 */
public class PrayerTimelineView extends View {

    private final float dp;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int ink, inkFaint, teal, tealSoft, white, hairline, grey;

    @NonNull private String[] names = new String[0];
    @NonNull private String[] times = new String[0];
    private int next = -1;
    private boolean allPast;
    @Nullable private String left;

    public PrayerTimelineView(Context context) { this(context, null); }

    public PrayerTimelineView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        ink = ContextCompat.getColor(context, R.color.text_primary);
        inkFaint = (ink & 0x00FFFFFF) | 0x4D000000;                    // deep-ink/30
        teal = ContextCompat.getColor(context, R.color.amber);
        tealSoft = ContextCompat.getColor(context, R.color.secondary_container);
        white = ContextCompat.getColor(context, R.color.surface_lowest);
        hairline = ContextCompat.getColor(context, R.color.hairline);
        grey = ContextCompat.getColor(context, R.color.text_muted);
        line.setStrokeWidth(dp);
        stroke.setStyle(Paint.Style.STROKE);
        Typeface jakarta = ResourcesCompat.getFont(context, R.font.jakarta);
        text.setTypeface(Typeface.create(jakarta, 500, false));
        text.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 12, getResources().getDisplayMetrics()));
        text.setTextAlign(Paint.Align.CENTER);
        text.setFontFeatureSettings("tnum");
    }

    /**
     * @param nextIndex the next prayer (0–4)
     * @param tomorrow  after Isha: every node is past and Fajr (tomorrow) is next
     */
    public void setPrayers(@NonNull String[] prayerNames, @NonNull String[] prayerTimes,
                           int nextIndex, boolean tomorrow, @Nullable String leftText) {
        names = prayerNames;
        times = prayerTimes;
        next = nextIndex;
        allPast = tomorrow;
        left = leftText;
        StringBuilder cd = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            if (cd.length() > 0) cd.append(", ");
            cd.append(names[i]).append(' ').append(i < times.length ? times[i] : "");
        }
        if (next >= 0 && left != null) cd.append(". ").append(names[next]).append(' ').append(left);
        setContentDescription(cd);
        requestLayout();
        invalidate();
    }

    private float lineH() { return 16 * dp; }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        // 16 node + 6 gap + label (16) + 4 gap + second line (16)
        int h = (int) (16 * dp + 6 * dp + lineH() + 4 * dp + lineH()) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), resolveSize(h, heightSpec));
    }

    private float centerX(int i) {
        int n = Math.max(1, names.length);
        float l = getPaddingLeft() + 16 * dp, r = getWidth() - getPaddingRight() - 16 * dp;
        return n == 1 ? (l + r) / 2f : l + (r - l) * i / (n - 1f);
    }

    @Override
    protected void onDraw(@NonNull Canvas c) {
        int n = names.length;
        if (n == 0) return;
        float cy = getPaddingTop() + 8 * dp;

        // Hairline, then the prayed part in deep-ink/30.
        line.setColor(hairline);
        c.drawLine(centerX(0), cy, centerX(n - 1), cy, line);
        float doneTo = allPast ? centerX(n - 1) : next > 0 ? centerX(next) : -1;
        if (doneTo > 0) {
            line.setColor(inkFaint);
            c.drawLine(centerX(0), cy, doneTo, cy, line);
        }

        Paint.FontMetrics fm = text.getFontMetrics();
        float baseline1 = cy + 8 * dp + 6 * dp + (lineH() - fm.ascent - fm.descent) / 2f;
        for (int i = 0; i < n; i++) {
            float cx = centerX(i);
            boolean isNext = i == next;
            boolean past = allPast || i < next;
            if (isNext) {
                fill.setColor(tealSoft);
                c.drawCircle(cx, cy, 12 * dp, fill);                     // ring-4 teal-soft
                fill.setColor(white);
                c.drawCircle(cx, cy, 8 * dp, fill);                      // border-2 white
                fill.setColor(teal);
                c.drawCircle(cx, cy, 6 * dp, fill);
            } else if (past) {
                fill.setColor(white);
                c.drawCircle(cx, cy, 8 * dp, fill);
                fill.setColor(ink);
                c.drawCircle(cx, cy, 6 * dp, fill);
            } else {
                fill.setColor(white);
                c.drawCircle(cx, cy, 8 * dp, fill);
                stroke.setStrokeWidth(dp);
                stroke.setColor(hairline);
                c.drawCircle(cx, cy, 7.5f * dp, stroke);
            }

            text.setColor(isNext ? teal : grey);
            String label = isNext && i < times.length ? names[i] + " " + times[i] : names[i];
            c.drawText(label, clampX(cx, label), isNext ? baseline1 - 2 * dp : baseline1, text);
            if (isNext && left != null) {
                c.drawText(left, clampX(cx, left), baseline1 - 2 * dp + 4 * dp + lineH(), text);
            }
        }
    }

    /** Keeps edge labels ("Fajr 05:12") inside the card. */
    private float clampX(float cx, @NonNull String s) {
        float half = text.measureText(s) / 2f;
        float min = getPaddingLeft() + half, max = getWidth() - getPaddingRight() - half;
        return Math.max(min, Math.min(max, cx));
    }
}
