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

    public static class RegisterRequest {
        public String email;
        public String password;
        @SerializedName("display_name") public String displayName;
        public String lang;
        public RegisterRequest(String email, String password, String displayName, String lang) {
            this.email = email;
            this.password = password;
            this.displayName = displayName;
            this.lang = lang;
        }
    }

    public static class EmailLoginRequest {
        public String email;
        public String password;
        public EmailLoginRequest(String email, String password) {
            this.email = email;
            this.password = password;
        }
    }

    public static class RefreshRequest {
        @SerializedName("refresh_token") public String refreshToken;
        public RefreshRequest(String refreshToken) { this.refreshToken = refreshToken; }
    }

    public static class GoogleLoginRequest {
        @SerializedName("id_token") public String idToken;
        public GoogleLoginRequest(String idToken) { this.idToken = idToken; }
    }

    public static class ForgotPasswordRequest {
        public String email;
        public ForgotPasswordRequest(String email) { this.email = email; }
    }

    public static class ResetPasswordRequest {
        public String email;
        public String code;
        @SerializedName("new_password") public String newPassword;
        public ResetPasswordRequest(String email, String code, String newPassword) {
            this.email = email;
            this.code = code;
            this.newPassword = newPassword;
        }
    }

    public static class OkResponse {
        public boolean ok;
    }

    public static class UserUpdateRequest {
        public String lang;
        @SerializedName("display_name") public String displayName;
        @SerializedName("photo_url") public String photoUrl;
        public UserUpdateRequest() {}
        public UserUpdateRequest(String lang) { this.lang = lang; }
        public static UserUpdateRequest photo(String url) {
            UserUpdateRequest r = new UserUpdateRequest();
            r.photoUrl = url;
            return r;
        }
    }

    public static class DeleteAccountConfirmRequest {
        public String code;
        public DeleteAccountConfirmRequest(String code) { this.code = code; }
    }

    /** Token-pair returned by /auth/refresh (rotation: both values are new). */
    public static class TokenPair {
        @SerializedName("access_token") public String accessToken;
        @SerializedName("refresh_token") public String refreshToken;
    }

    /** Server error envelope: { "error": { "code": "...", "message": "..." } }. */
    public static class ErrorEnvelope {
        public ErrorBody error;
        public static class ErrorBody {
            public String code;
            public String message;
        }
    }

    public static class UserDto {
        public String id;
        @SerializedName("display_name") public String displayName;
        public String email;
        @SerializedName("photo_url") public String photoUrl;
        public String role;
        public String lang;
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
        @SerializedName("urdu_name") public String urduName;
        public String transliteration;
        public String category;
        public String translation;
        public String reference;
        public String icon;
        @SerializedName("default_increment") public int defaultIncrement;
        @SerializedName("category_id") public String categoryId;
    }

    public static class CategoryDto {
        public String id;
        public String name;
        public String slug;
        public String color;
        public String icon;
    }

    /** Body for creating a recitation (super members / admins). */
    public static class RecitationCreateRequest {
        @SerializedName("arabic_name") public String arabicName;
        @SerializedName("english_name") public String englishName;
        @SerializedName("urdu_name") public String urduName;
        @SerializedName("category_id") public String categoryId;
        @SerializedName("default_increment") public int defaultIncrement = 1;
    }

    public static class BulkItem {
        @SerializedName("recitation_id") public String recitationId;
        public int count;
        @SerializedName("client_uuid") public String clientUuid;
        public BulkItem(String recitationId, int count, String clientUuid) {
            this.recitationId = recitationId;
            this.count = count;
            this.clientUuid = clientUuid;
        }
    }

    /** Bulk submission. group_id omitted => server uses the community group. */
    public static class BulkSubmitRequest {
        public List<BulkItem> items;
        public BulkSubmitRequest(List<BulkItem> items) { this.items = items; }
    }

    /** A community activity-feed entry (also used for the home activity list). */
    public static class ActivityDto {
        public String id;
        @SerializedName("actor_name") public String actorName;
        public String initial;
        public String type;
        public String text;
        public java.util.Map<String, Double> reactions;
        @SerializedName("created_at") public String createdAt;

        public int reaction(String key) {
            if (reactions == null) return 0;
            Double v = reactions.get(key);
            return v == null ? 0 : (int) Math.round(v);
        }
    }

    public static class GoalDto {
        public int target;
        public int progress;
        public int percent;
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

    /** One of the current user's own submissions (for the profile screen). */
    public static class MySubmissionDto {
        public String id;
        @SerializedName("recitation_name") public String recitationName;
        @SerializedName("urdu_name") public String urduName;
        public int count;
        @SerializedName("created_at") public String createdAt;
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
        @SerializedName("monthly_total") public int monthlyTotal;
        @SerializedName("lifetime_total") public int lifetimeTotal;
        @SerializedName("group_total") public int groupTotal;
        @SerializedName("remaining_days") public int remainingDays;
        public int streak;
        public GoalDto goal;
        @SerializedName("top_contributors") public List<ContributorDto> topContributors;
        @SerializedName("recent_activity") public List<ActivityDto> recentActivity;
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

    // --- Community statistics (Stats page) ---
    public static class RecitationStat {
        @SerializedName("recitation_id") public String recitationId;
        public String name;
        @SerializedName("urdu_name") public String urduName;
        public String category;
        public int count;
    }

    public static class CategoryStat {
        public String category;
        public int count;
    }

    public static class CommunityStats {
        @SerializedName("week_id") public String weekId;
        @SerializedName("week_number") public Integer weekNumber;
        public String label;
        public String status;
        public int total;
        @SerializedName("by_recitation") public List<RecitationStat> byRecitation;
        @SerializedName("by_category") public List<CategoryStat> byCategory;
        @SerializedName("can_manage") public boolean canManage;
    }

    public static class WeekSummary {
        @SerializedName("week_id") public String weekId;
        @SerializedName("week_number") public int weekNumber;
        public String label;
        public int total;
        @SerializedName("closed_at") public String closedAt;
    }
}
