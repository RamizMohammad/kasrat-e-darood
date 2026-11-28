package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;

/**
 * Stitch me_teal "Last 12 weeks": 12 columns (a week each, this week last) × 7 rows (Monday
 * on top) of rounded squares filling the width (19 of every 24 units, rx 4). Four tints:
 * ivory with a hairline (nothing), teal-soft, deep teal 55%, deep teal — relative to your
 * best day in those 12 weeks. Days after today are left blank. Mirrors in RTL.
 */
public class KHeatmapView extends View {

    public static final int WEEKS = 12;
    private static final int[] LEVEL_RES = {in.mohammad.ramiz.islamic.kasrat_e_darrod.R.color.k_heat_0, in.mohammad.ramiz.islamic.kasrat_e_darrod.R.color.k_heat_1,
            in.mohammad.ramiz.islamic.kasrat_e_darrod.R.color.k_heat_2, in.mohammad.ramiz.islamic.kasrat_e_darrod.R.color.k_heat_3};
    private final int[] LEVELS = new int[4];

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Map<LocalDate, Long> totals = new HashMap<>();
    private LocalDate today = LocalDate.now();
    private long max;

    public KHeatmapView(@NonNull Context c) { this(c, null); }

    public KHeatmapView(@NonNull Context c, @Nullable AttributeSet a) {
        super(c, a);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(getResources().getDisplayMetrics().density);
        edge.setColor(androidx.core.content.ContextCompat.getColor(c, in.mohammad.ramiz.islamic.kasrat_e_darrod.R.color.hairline));
        for (int i = 0; i < 4; i++) LEVELS[i] = androidx.core.content.ContextCompat.getColor(c, LEVEL_RES[i]);
    }

    public void setDays(@Nullable List<dto.DayTotal> days) {
        totals.clear();
        today = LocalDate.now();
        LocalDate start = firstDay();
        max = 0;
        if (days != null) {
            for (dto.DayTotal d : days) {
                try {
                    LocalDate day = LocalDate.parse(d.date);
                    if (day.isBefore(start) || day.isAfter(today)) continue;
                    totals.put(day, d.total);
                    max = Math.max(max, d.total);
                } catch (DateTimeParseException | NullPointerException ignored) { }
            }
        }
        int active = 0;
        for (long t : totals.values()) if (t > 0) active++;
        setContentDescription(getResources().getQuantityString(
                in.mohammad.ramiz.islamic.kasrat_e_darrod.R.plurals.me_k_active_days, active, active));
        invalidate();
    }

    @NonNull
    private LocalDate firstDay() {
        return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(WEEKS - 1);
    }

    private int level(long t) {
        if (t <= 0 || max <= 0) return 0;
        double f = t / (double) max;
        return f <= 1 / 3d ? 1 : f <= 2 / 3d ? 2 : 3;
    }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        float pitch = width / (WEEKS - 5f / 24f);          // 12 cells + 11 gaps, gap = 5/19 cell
        setMeasuredDimension(width, Math.round(pitch * 7 - pitch * 5f / 24f));
    }

    @Override
    protected void onDraw(@NonNull Canvas c) {
        float w = getWidth();
        float pitch = w / (WEEKS - 5f / 24f);
        float cell = pitch * 19f / 24f, rad = pitch * 4f / 24f, half = edge.getStrokeWidth() / 2f;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        LocalDate start = firstDay();
        for (int col = 0; col < WEEKS; col++) {
            for (int row = 0; row < 7; row++) {
                LocalDate day = start.plusDays(col * 7L + row);
                if (day.isAfter(today)) continue;
                float x = (rtl ? WEEKS - 1 - col : col) * pitch, y = row * pitch;
                Long t = totals.get(day);
                int lv = level(t == null ? 0 : t);
                fill.setColor(LEVELS[lv]);
                r.set(x, y, x + cell, y + cell);
                c.drawRoundRect(r, rad, rad, fill);
                if (lv == 0) {
                    r.inset(half, half);
                    c.drawRoundRect(r, rad, rad, edge);
                }
            }
        }
    }
}
