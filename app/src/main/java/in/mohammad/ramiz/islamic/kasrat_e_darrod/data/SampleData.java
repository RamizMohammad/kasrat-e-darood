package in.mohammad.ramiz.islamic.kasrat_e_darrod.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.model.ActivityItem;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.model.FeedPost;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.model.LeaderboardEntry;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.model.Recitation;

/**
 * Seed content used to render the screens before the backend is connected.
 * Mirrors the data the Noor API will return so swapping in Retrofit is a
 * drop-in replacement (see {@code data.remote.ApiService}).
 */
public final class SampleData {
    private SampleData() {}

    public static List<Recitation> recitations() {
        return new ArrayList<>(Arrays.asList(
            new Recitation("1", "يس", "Surah Yaseen", "Heart of Quran", "Chapter 36", R.drawable.ic_book_open),
            new Recitation("2", "آية الكرسي", "Ayatul Kursi", "Protective Verse", "Verse 255", R.drawable.ic_shield),
            new Recitation("3", "درود إبراهيم", "Darood Ibrahim", "Salutations", "Daily Prayer", R.drawable.ic_heart),
            new Recitation("4", "أستغفر الله", "Astaghfirullah", "Forgiveness", "Repentance", R.drawable.ic_leaf),
            new Recitation("5", "سبحان الله", "SubhanAllah", "Glory be to Allah", "Praise", R.drawable.ic_sparkle),
            new Recitation("6", "الفاتحة", "Al-Fatihah", "The Opening", "Chapter 1", R.drawable.ic_book_open)
        ));
    }

    public static List<ActivityItem> activity() {
        return new ArrayList<>(Arrays.asList(
            new ActivityItem("A", "Amina K. completed Surah Ar-Rahman", "Earned 'Mindful Reciter' badge", "2m ago", false),
            new ActivityItem("I", "Dr. Ibrahim shared a reflection on Surah Al-Mulk", "\"The creation is a sign of His infinite...\"", "45m ago", true),
            new ActivityItem("Y", "Yusuf M. started a new group goal", "Weekly Khatm challenge", "2h ago", false)
        ));
    }

    public static List<LeaderboardEntry> leaderboard() {
        return new ArrayList<>(Arrays.asList(
            new LeaderboardEntry(4, "Fatima R.", "7 days streak", 315, false),
            new LeaderboardEntry(5, "Zaid H.", "3 days streak", 280, false),
            new LeaderboardEntry(6, "Layla B.", "12 days streak", 242, true)
        ));
    }

    public static List<FeedPost> feed() {
        return new ArrayList<>(Arrays.asList(
            new FeedPost("F", "Fatima", "2h ago", "Completed Surah Yaseen x3", 12, 8),
            new FeedPost("A", "Ahmed", "4h ago", "Completed Darood x100", 24, 7),
            new FeedPost("Z", "Zainab", "1d ago", "Shared a reflection on Surah Ar-Rahman", 15, 9),
            new FeedPost("M", "Maryam K.", "1d ago", "Completed 500 SubhanAllah", 42, 6)
        ));
    }
}
