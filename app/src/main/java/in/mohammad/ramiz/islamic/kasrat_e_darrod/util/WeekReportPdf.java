package in.mohammad.ramiz.islamic.kasrat_e_darrod.util;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.io.OutputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;

/**
 * Renders a single archived week's community report to a PDF on the device and
 * saves it into the public Downloads collection. Returns the resulting content
 * {@link Uri} (openable with ACTION_VIEW).
 */
public final class WeekReportPdf {

    // A4 at 72dpi (points).
    private static final int PAGE_W = 595;
    private static final int PAGE_H = 842;
    private static final int MARGIN = 48;

    private static final int EMERALD = Color.parseColor("#1B5E4B");
    private static final int EMERALD_LIGHT = Color.parseColor("#D8EFE6");
    private static final int GOLD = Color.parseColor("#B8860B");
    private static final int INK = Color.parseColor("#1A1A1A");
    private static final int MUTED = Color.parseColor("#6B7280");
    private static final int TRACK = Color.parseColor("#ECECEC");

    private WeekReportPdf() {}

    public static Uri generate(Context ctx, String weekTitle, String periodLabel,
                               int total, List<dto.RecitationStat> rows) throws Exception {
        PdfDocument doc = new PdfDocument();

        final NumberFormat nf = NumberFormat.getIntegerInstance();
        int max = 1;
        if (rows != null) for (dto.RecitationStat r : rows) max = Math.max(max, r.count);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint bold = new Paint(Paint.ANTI_ALIAS_FLAG);
        bold.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        int rowsPerPage = 20;
        int totalRows = rows == null ? 0 : rows.size();
        int pages = Math.max(1, (int) Math.ceil(totalRows / (double) rowsPerPage));

        int index = 0;
        for (int pageNo = 1; pageNo <= pages; pageNo++) {
            PdfDocument.PageInfo info =
                    new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create();
            PdfDocument.Page page = doc.startPage(info);
            Canvas c = page.getCanvas();

            float y;
            if (pageNo == 1) {
                // Header band
                p.setColor(EMERALD);
                c.drawRect(0, 0, PAGE_W, 120, p);

                bold.setColor(Color.WHITE);
                bold.setTextSize(22);
                c.drawText("Kasrat-e-Darood", MARGIN, 52, bold);

                p.setColor(EMERALD_LIGHT);
                p.setTextSize(12);
                c.drawText("Community Recitation Report", MARGIN, 74, p);

                p.setColor(Color.WHITE);
                p.setTextSize(11);
                String generated = "Generated " + new SimpleDateFormat(
                        "d MMM yyyy", Locale.getDefault()).format(new Date());
                c.drawText(generated, MARGIN, 96, p);

                // Week title
                bold.setColor(INK);
                bold.setTextSize(20);
                c.drawText(weekTitle != null ? weekTitle : "Week", MARGIN, 168, bold);
                if (periodLabel != null && !periodLabel.isEmpty()) {
                    p.setColor(MUTED);
                    p.setTextSize(12);
                    c.drawText(periodLabel, MARGIN, 188, p);
                }

                // Total card
                RectF card = new RectF(MARGIN, 210, PAGE_W - MARGIN, 300);
                p.setColor(EMERALD_LIGHT);
                c.drawRoundRect(card, 16, 16, p);
                p.setColor(EMERALD);
                p.setTextSize(12);
                c.drawText("TOTAL SUBMISSIONS", MARGIN + 24, 244, p);
                bold.setColor(EMERALD);
                bold.setTextSize(40);
                c.drawText(nf.format(total), MARGIN + 24, 288, bold);

                // Breakdown heading
                bold.setColor(INK);
                bold.setTextSize(14);
                c.drawText("By Recitation", MARGIN, 340, bold);
                y = 364;
            } else {
                bold.setColor(INK);
                bold.setTextSize(14);
                c.drawText("By Recitation (cont.)", MARGIN, 64, bold);
                y = 88;
            }

            int onThisPage = 0;
            while (index < totalRows && onThisPage < rowsPerPage) {
                dto.RecitationStat r = rows.get(index);
                String name = r.name != null ? r.name : "Recitation";

                p.setColor(INK);
                p.setTextSize(12);
                c.drawText(name, MARGIN, y, p);

                bold.setColor(EMERALD);
                bold.setTextSize(12);
                String count = nf.format(r.count);
                float cw = bold.measureText(count);
                c.drawText(count, PAGE_W - MARGIN - cw, y, bold);

                // Filling bar
                float barTop = y + 6;
                float barH = 7;
                RectF track = new RectF(MARGIN, barTop, PAGE_W - MARGIN, barTop + barH);
                p.setColor(TRACK);
                c.drawRoundRect(track, 4, 4, p);
                float frac = Math.max(0.02f, (float) r.count / (float) max);
                RectF fill = new RectF(MARGIN, barTop,
                        MARGIN + (PAGE_W - 2 * MARGIN) * frac, barTop + barH);
                p.setColor(EMERALD);
                c.drawRoundRect(fill, 4, 4, p);

                y += 34;
                index++;
                onThisPage++;
            }

            if (totalRows == 0 && pageNo == 1) {
                p.setColor(MUTED);
                p.setTextSize(12);
                c.drawText("No submissions were recorded for this week.", MARGIN, y, p);
            }

            // Footer
            p.setColor(MUTED);
            p.setTextSize(9);
            c.drawText("Kasrat-e-Darood  ·  Page " + pageNo + " of " + pages,
                    MARGIN, PAGE_H - 28, p);

            doc.finishPage(page);
        }

        Uri uri = save(ctx, doc, fileName(weekTitle));
        doc.close();
        return uri;
    }

    private static String fileName(String weekTitle) {
        String base = weekTitle == null ? "Week" : weekTitle.replaceAll("[^A-Za-z0-9]+", "_");
        return "KasratReport_" + base + ".pdf";
    }

    private static Uri save(Context ctx, PdfDocument doc, String name) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, name);
        values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Downloads.IS_PENDING, 1);
        }

        Uri item = ctx.getContentResolver()
                .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (item == null) throw new IllegalStateException("Couldn't create file in Downloads");

        try (OutputStream os = ctx.getContentResolver().openOutputStream(item)) {
            if (os == null) throw new IllegalStateException("Couldn't open output stream");
            doc.writeTo(os);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear();
            values.put(MediaStore.Downloads.IS_PENDING, 0);
            ctx.getContentResolver().update(item, values, null, null);
        }
        return item;
    }
}
