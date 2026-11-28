package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util;

import androidx.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Server timestamps ("2026-11-27T10:15:00.123456+00:00", or without a zone = UTC) → millis. */
public final class IsoTime {

    private static final String[] PATTERNS = {"yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSSSS", "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss"};

    private IsoTime() { }

    /** -1 when it can't be read. */
    public static long parse(@Nullable String iso) {
        if (iso == null) return -1;
        for (String p : PATTERNS) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(p, Locale.US);
                f.setLenient(false);
                if (!p.endsWith("XXX")) f.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date d = f.parse(iso);
                if (d != null) return d.getTime();
            } catch (ParseException ignored) {
                // next shape
            }
        }
        return -1;
    }
}
