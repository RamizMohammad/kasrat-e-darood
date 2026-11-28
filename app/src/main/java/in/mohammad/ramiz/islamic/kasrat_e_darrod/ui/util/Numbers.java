package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * Counts, ranks and codes always use Latin digits. Large numbers use Indian
 * grouping (1,00,000) for Hindi and for English in India, Western grouping
 * (100,000) otherwise.
 */
public final class Numbers {

    private Numbers() { }

    @NonNull
    public static String format(long n) {
        return format(n, Locale.getDefault());
    }

    @NonNull
    public static String format(long n, @NonNull Locale locale) {
        boolean indian = "hi".equals(locale.getLanguage()) || "IN".equals(locale.getCountry());
        boolean negative = n < 0;
        String digits = Long.toString(Math.abs(n));
        StringBuilder out = new StringBuilder();
        int len = digits.length();
        if (len <= 3) {
            out.append(digits);
        } else if (indian) {
            String head = digits.substring(0, len - 3);
            String tail = digits.substring(len - 3);
            StringBuilder h = new StringBuilder();
            int i = head.length();
            while (i > 2) {
                h.insert(0, "," + head.substring(i - 2, i));
                i -= 2;
            }
            h.insert(0, head.substring(0, i));
            out.append(h).append(',').append(tail);
        } else {
            int first = len % 3 == 0 ? 3 : len % 3;
            out.append(digits, 0, first);
            for (int i = first; i < len; i += 3) out.append(',').append(digits, i, i + 3);
        }
        return negative ? "-" + out : out.toString();
    }
}
