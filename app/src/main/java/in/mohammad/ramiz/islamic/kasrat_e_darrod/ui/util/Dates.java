package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util;

import android.icu.text.DateFormat;
import android.icu.util.Calendar;
import android.icu.util.ULocale;

import androidx.annotation.NonNull;

import java.util.Date;
import java.util.Locale;

/** Hijri (Umm al-Qura) and Gregorian dates in the app's language, worked out on the phone. */
public final class Dates {

    private Dates() { }

    @NonNull
    public static String hijri(@NonNull Date date) {
        ULocale ul = ULocale.forLocale(Locale.getDefault()).setKeywordValue("calendar", "islamic-umalqura");
        Calendar cal = Calendar.getInstance(ul);
        cal.setTime(date);
        return DateFormat.getInstanceForSkeleton(cal, "dMMMMy", ul).format(date);
    }

    @NonNull
    public static String gregorian(@NonNull Date date) {
        ULocale ul = ULocale.forLocale(Locale.getDefault()).setKeywordValue("calendar", "gregorian");
        return DateFormat.getInstanceForSkeleton("EEEEdMMMM", ul).format(date);
    }

    /** Home's date line part: "Friday, 27 Nov" (short month, Stitch home). */
    @NonNull
    public static String gregorianShort(@NonNull Date date) {
        ULocale ul = ULocale.forLocale(Locale.getDefault()).setKeywordValue("calendar", "gregorian");
        return DateFormat.getInstanceForSkeleton("EEEEdMMM", ul).format(date);
    }

    /** The Hijri month number (1–12) for today, e.g. 9 = Ramadan. */
    public static int hijriMonth(@NonNull Date date) {
        ULocale ul = ULocale.forLocale(Locale.US).setKeywordValue("calendar", "islamic-umalqura");
        Calendar cal = Calendar.getInstance(ul);
        cal.setTime(date);
        return cal.get(Calendar.MONTH) + 1;
    }

    public static int hijriDay(@NonNull Date date) {
        ULocale ul = ULocale.forLocale(Locale.US).setKeywordValue("calendar", "islamic-umalqura");
        Calendar cal = Calendar.getInstance(ul);
        cal.setTime(date);
        return cal.get(Calendar.DAY_OF_MONTH);
    }
}
