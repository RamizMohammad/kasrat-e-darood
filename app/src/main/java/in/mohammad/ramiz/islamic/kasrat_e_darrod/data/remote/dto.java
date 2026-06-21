package in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/** Data-transfer objects matching the Noor REST API contract (docs/API_CONTRACT.md). */
public final class dto {
    private dto() {}

    public static class FirebaseLoginRequest {
        @SerializedName("id_token") public String idToken;
        public FirebaseLoginRequest(String idToken) { this.idToken = idToken; }
    }

    public static class UserDto {
        public String id;
        @SerializedName("display_name") public String displayName;
        public String email;
        @SerializedName("photo_url") public String photoUrl;
        public String role;
        @SerializedName("lifetime_total") public int lifetimeTotal;
        @SerializedName("streak_days") public int streakDays;
    }

    public static class LoginResponse {
        @SerializedName("access_token") public String accessToken;
        @SerializedName("refresh_token") public String refreshToken;
        public UserDto user;
    }

    public static class RecitationDto {
        public String id;
        @SerializedName("arabic_name") public String arabicName;
        @SerializedName("english_name") public String englishName;
        public String translation;
        @SerializedName("category_id") public String categoryId;
    }

    public static class SubmissionRequest {
        @SerializedName("group_id") public String groupId;
        @SerializedName("recitation_id") public String recitationId;
        public int count;
        @SerializedName("client_uuid") public String clientUuid;
    }

    public static class Totals {
        @SerializedName("user_week") public int userWeek;
        @SerializedName("user_lifetime") public int userLifetime;
        @SerializedName("group_week") public int groupWeek;
        public int today;
    }

    public static class SubmissionResult {
        public Totals totals;
    }

    public static class ContributorDto {
        public UserDto user;
        public int total;
        public int rank;
    }

    public static class DashboardResponse {
        @SerializedName("user_total") public int userTotal;
        @SerializedName("today_total") public int todayTotal;
        @SerializedName("weekly_total") public int weeklyTotal;
        @SerializedName("lifetime_total") public int lifetimeTotal;
        @SerializedName("group_total") public int groupTotal;
        @SerializedName("remaining_days") public int remainingDays;
        public int streak;
        @SerializedName("top_contributors") public List<ContributorDto> topContributors;
    }

    public static class LeaderboardEntryDto {
        public UserDto user;
        public int total;
        public int rank;
        public int streak;
    }

    public static class LeaderboardResponse {
        public String scope;
        public List<LeaderboardEntryDto> entries;
    }
}
