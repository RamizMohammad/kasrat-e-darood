package in.mohammad.ramiz.islamic.kasrat_e_darrod.util;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;

/** Maps a recitation's server icon hint / name to a local drawable. */
public final class RecitationIcons {
    private RecitationIcons() {}

    public static int forRecitation(String iconHint, String englishName) {
        String s = ((iconHint == null ? "" : iconHint) + " "
                + (englishName == null ? "" : englishName)).toLowerCase();

        if (s.contains("kursi") || s.contains("shield") || s.contains("protect")) {
            return R.drawable.ic_shield;
        }
        if (s.contains("darood") || s.contains("salawat") || s.contains("heart")) {
            return R.drawable.ic_heart;
        }
        if (s.contains("astagh") || s.contains("forgive") || s.contains("leaf")
                || s.contains("dhikr") || s.contains("beads")) {
            return R.drawable.ic_leaf;
        }
        if (s.contains("subhan") || s.contains("glory") || s.contains("sparkle")
                || s.contains("tasbih")) {
            return R.drawable.ic_sparkle;
        }
        return R.drawable.ic_book_open;  // Quran / default
    }
}
