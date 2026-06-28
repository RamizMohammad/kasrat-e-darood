package in.mohammad.ramiz.islamic.kasrat_e_darrod.util;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formats an ISO-8601 timestamp into a short relative label like "2m ago". */
public final class RelativeTime {
    private RelativeTime() {}

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault());

    /** Formats an ISO-8601 timestamp into an absolute date like "12 Jan 2026". */
    public static String asDate(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        Instant then = parse(iso);
        if (then == null) return "";
        return DATE_FMT.withZone(ZoneId.systemDefault()).format(then);
    }

    public static String from(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        Instant then = parse(iso);
        if (then == null) return "";

        long seconds = Math.max(0, (System.currentTimeMillis() - then.toEpochMilli()) / 1000);
        if (seconds < 60) return "just now";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        if (days < 7) return days + "d ago";
        long weeks = days / 7;
        if (weeks < 5) return weeks + "w ago";
        long months = days / 30;
        if (months < 12) return months + "mo ago";
        return (days / 365) + "y ago";
    }

    private static Instant parse(String iso) {
        try {
            return OffsetDateTime.parse(iso, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
        } catch (Exception ignored) {
            // fall through
        }
        try {
            return Instant.parse(iso);  // handles trailing 'Z'
        } catch (Exception ignored) {
            return null;
        }
    }
}
