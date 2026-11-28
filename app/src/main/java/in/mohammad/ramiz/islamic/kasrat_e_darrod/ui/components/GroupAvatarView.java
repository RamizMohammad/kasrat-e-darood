package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A circle's emblem, drawn the Stitch way (circles_teal / new_circle_teal): a soft disc with
 * a 1px hairline and a thin geometric line mark, plus an optional faint pattern.
 *
 *   mark     0 two interlocking circles · 1 eight-point star (two squares) ·
 *            2 crescent · 3 a single ring
 *   colour   0 teal-soft · 1 gold-soft · 2 warm ivory · 3 pure white
 *   pattern  0 none · 1 ring of dots · 2 diamond lattice · 3 inner ring
 *
 * The group stores a short code, e.g. {@code "grp1:s0.c1.p2"} (s = mark, c = colour,
 * p = pattern), which the server checks against ^grp1:[a-z][0-9]{1,2}(\.[a-z][0-9]{1,2}){0,11}$.
 * Older codes with bigger numbers wrap round. A group without one gets a stable look from its id.
 *
 * {@link #setBare} draws only the mark or pattern glyph (no disc): the picker's small options.
 */
public class GroupAvatarView extends View {

    public static final String PREFIX = "grp1:";
    public static final int MARKS = 4, PATTERNS = 4;
    /** Disc fills (teal-soft, gold-soft/50 on white, warm ivory, pure white) as resources,
     *  so the dark theme has its own: k_emblem_disc_0…3. Only the count is fixed here. */
    public static final int[] COLORS = new int[4];
    private static final int[] DISC_RES = {in.mohammad.ramiz.islamic.kasrat_e_darrod.R.color.k_emblem_disc_0, in.mohammad.ramiz.islamic.kasrat_e_darrod.R.color.k_emblem_disc_1,
            in.mohammad.ramiz.islamic.kasrat_e_darrod.R.color.k_emblem_disc_2, in.mohammad.ramiz.islamic.kasrat_e_darrod.R.color.k_emblem_disc_3};
    private static final int GOLD = 0xFFC9A24B, GOLD_40 = 0x66C9A24B;
    private final int TEAL, HAIRLINE;
    private final int[] discs = new int[4];

    /** The four disc colours for the current theme (light or dark). */
    @NonNull
    public static int[] colors(@NonNull Context ctx) {
        int[] out = new int[DISC_RES.length];
        for (int i = 0; i < out.length; i++) out[i] = androidx.core.content.ContextCompat.getColor(ctx, DISC_RES[i]);
        return out;
    }

    private static final Pattern PART = Pattern.compile("([a-z])([0-9]{1,2})");

    /** A parsed emblem. */
    public static final class Spec {
        public final int mark, color, pattern;

        public Spec(int mark, int color, int pattern) {
            this.mark = Math.floorMod(mark, MARKS);
            this.color = Math.floorMod(color, COLORS.length);
            this.pattern = Math.floorMod(pattern, PATTERNS);
        }

        @NonNull
        public String encode() {
            return String.format(Locale.ROOT, "%ss%d.c%d.p%d", PREFIX, mark, color, pattern);
        }

        @NonNull public Spec withMark(int m) { return new Spec(m, color, pattern); }
        @NonNull public Spec withColor(int c) { return new Spec(mark, c, pattern); }
        @NonNull public Spec withPattern(int p) { return new Spec(mark, color, p); }

        /** The stored code, or a stable default made from the group's id. */
        @NonNull
        public static Spec of(@Nullable String avatar, @Nullable String groupId) {
            if (avatar != null && avatar.startsWith(PREFIX)) {
                int m = 0, c = 0, p = 0;
                Matcher x = PART.matcher(avatar.substring(PREFIX.length()));
                while (x.find()) {
                    int v = Integer.parseInt(x.group(2));
                    switch (x.group(1)) {
                        case "s": m = v; break;
                        case "c": c = v; break;
                        case "p": p = v; break;
                        default: break;
                    }
                }
                return new Spec(m, c, p);
            }
            int h = groupId == null ? 0 : groupId.hashCode();
            return new Spec(h, h >>> 4, 0);
        }

        /** The global circle: interlocking circles on teal-soft. */
        @NonNull
        public static Spec global() { return new Spec(0, 0, 0); }
    }

    public enum Bare { NONE, MARK, PATTERN }

    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint faint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF box = new RectF();
    private Spec spec = new Spec(0, 0, 0);
    private Bare bare = Bare.NONE;
    private int bareColor;

    public GroupAvatarView(@NonNull Context context) { this(context, null); }

    public GroupAvatarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        TEAL = androidx.core.content.ContextCompat.getColor(context, in.mohammad.ramiz.islamic.kasrat_e_darrod.R.color.k_emblem_mark);
        HAIRLINE = androidx.core.content.ContextCompat.getColor(context, in.mohammad.ramiz.islamic.kasrat_e_darrod.R.color.hairline);
        bareColor = TEAL;
        System.arraycopy(colors(context), 0, discs, 0, discs.length);
        border.setStyle(Paint.Style.STROKE);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        faint.setStyle(Paint.Style.STROKE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        set(spec);
    }

    public void setGroup(@Nullable String avatar, @Nullable String groupId, boolean global) {
        set(global ? Spec.global() : Spec.of(avatar, groupId));
    }

    public void set(@NonNull Spec s) {
        spec = s;
        disc.setColor(discs[s.color]);
        invalidate();
    }

    /** Draw just the mark or the pattern, in one colour, with no disc (picker options). */
    public void setBare(@NonNull Bare what, int color) {
        bare = what;
        bareColor = color;
        invalidate();
    }

    @NonNull public Spec spec() { return spec; }

    @Override
    protected void onDraw(@NonNull Canvas c) {
        float w = getWidth(), h = getHeight(), r = Math.min(w, h) / 2f, cx = w / 2f, cy = h / 2f;
        float dp = getResources().getDisplayMetrics().density;
        boolean gold = spec.color == 1;

        if (bare == Bare.NONE) {
            c.drawCircle(cx, cy, r, disc);
            border.setStrokeWidth(dp);
            border.setColor(gold ? GOLD_40 : HAIRLINE);
            c.drawCircle(cx, cy, r - dp / 2f, border);
            drawPattern(c, cx, cy, r, (TEAL & 0x00FFFFFF) | 0x2E000000);
            drawMark(c, cx, cy, r * 0.58f, gold);
        } else if (bare == Bare.MARK) {
            drawMarkIn(c, cx, cy, r, bareColor, bareColor);
        } else {
            drawPatternGlyph(c, cx, cy, r, bareColor);
        }
    }

    // ── Marks (Stitch line drawings, ~1.25px at 48dp) ───────────────────────

    private void drawMark(@NonNull Canvas c, float cx, float cy, float s, boolean gold) {
        drawMarkIn(c, cx, cy, s, gold ? GOLD : TEAL, TEAL);
    }

    /** {@code s} = half the mark's box. Colour a = first shape, b = second. */
    private void drawMarkIn(@NonNull Canvas c, float cx, float cy, float s, int a, int b) {
        line.setStrokeWidth(Math.max(1f, s * 0.09f));
        switch (spec.mark) {
            case 1: {                                   // eight-point star: square + square at 45°
                line.setColor(a);
                square(c, cx, cy, s * 0.72f, 0f);
                line.setColor(b);
                square(c, cx, cy, s * 0.72f, 45f);
                break;
            }
            case 2: {                                   // crescent (was a six-point star — replaced)
                line.setColor(b);
                float big = s * 0.66f;
                android.graphics.Path outer = new android.graphics.Path();
                outer.addCircle(cx - s * 0.06f, cy, big, android.graphics.Path.Direction.CW);
                android.graphics.Path cut = new android.graphics.Path();
                cut.addCircle(cx + big * 0.42f, cy - big * 0.12f, big * 0.80f, android.graphics.Path.Direction.CW);
                outer.op(cut, android.graphics.Path.Op.DIFFERENCE);
                c.drawPath(outer, line);
                break;
            }
            case 3: {                                   // a single ring
                line.setColor(b);
                c.drawCircle(cx, cy, s * 0.62f, line);
                break;
            }
            default: {                                  // two interlocking circles
                line.setColor(b);
                float rr = s * 0.52f, off = s * 0.26f;
                c.drawCircle(cx - off, cy, rr, line);
                c.drawCircle(cx + off, cy, rr, line);
            }
        }
    }

    private void square(@NonNull Canvas c, float cx, float cy, float half, float deg) {
        c.save();
        c.rotate(deg, cx, cy);
        box.set(cx - half, cy - half, cx + half, cy + half);
        float rad = half * 0.14f;
        c.drawRoundRect(box, rad, rad, line);
        c.restore();
    }

    private void triangle(@NonNull Canvas c, float cx, float cy, float s, boolean up) {
        path.reset();
        float top = up ? cy - s : cy + s, base = up ? cy + s * 0.5f : cy - s * 0.5f;
        path.moveTo(cx, top);
        path.lineTo(cx + s * 0.87f, base);
        path.lineTo(cx - s * 0.87f, base);
        path.close();
        c.drawPath(path, line);
    }

    // ── Patterns (faint, behind the mark) ───────────────────────────────────

    private void drawPattern(@NonNull Canvas c, float cx, float cy, float r, int color) {
        faint.setColor(color);
        faint.setStrokeWidth(Math.max(1f, r * 0.03f));
        switch (spec.pattern) {
            case 1: {                                   // ring of dots
                Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
                dot.setColor(color);
                for (int i = 0; i < 16; i++) {
                    double ang = Math.PI * 2 * i / 16;
                    c.drawCircle(cx + (float) Math.cos(ang) * r * 0.82f, cy + (float) Math.sin(ang) * r * 0.82f,
                            r * 0.035f, dot);
                }
                break;
            }
            case 2: {                                   // diamond lattice
                c.save();
                c.clipPath(circle(cx, cy, r * 0.96f));
                float step = r * 0.42f;
                for (float k = -2 * r; k <= 2 * r; k += step) {
                    c.drawLine(cx + k - r, cy - r, cx + k + r, cy + r, faint);
                    c.drawLine(cx + k + r, cy - r, cx + k - r, cy + r, faint);
                }
                c.restore();
                break;
            }
            case 3: {                                   // inner ring
                c.drawCircle(cx, cy, r * 0.84f, faint);
                break;
            }
            default: break;
        }
    }

    /** Picker glyphs: none = a short dash, dot, diamond, ring (Stitch new_circle_teal). */
    private void drawPatternGlyph(@NonNull Canvas c, float cx, float cy, float r, int color) {
        line.setColor(color);
        line.setStrokeWidth(Math.max(1f, r * 0.12f));
        switch (spec.pattern) {
            case 1: {
                Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
                dot.setColor(color);
                c.drawCircle(cx, cy, r * 0.38f, dot);
                break;
            }
            case 2: square(c, cx, cy, r * 0.55f, 45f); break;
            case 3: c.drawCircle(cx, cy, r * 0.7f, line); break;
            default: c.drawLine(cx - r * 0.85f, cy, cx + r * 0.85f, cy, line);
        }
    }

    @NonNull
    private Path circle(float cx, float cy, float r) {
        Path p = new Path();
        p.addCircle(cx, cy, r, Path.Direction.CW);
        return p;
    }
}
