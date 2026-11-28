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
        /** Illustrated avatar spec ("av1:…") from the avatar studio. */
        public String avatar;
        public UserUpdateRequest() {}
        public UserUpdateRequest(String lang) { this.lang = lang; }
        public static UserUpdateRequest photo(String url) {
            UserUpdateRequest r = new UserUpdateRequest();
            r.photoUrl = url;
            return r;
        }
        public static UserUpdateRequest avatar(String spec) {
            UserUpdateRequest r = new UserUpdateRequest();
            r.avatar = spec;
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
        /** Illustrated avatar spec ("av1:…"), or null if the user hasn't made one. */
        public String avatar;
        public String role;
        public String lang;
        @SerializedName("lifetime_total") public int lifetimeTotal;
        @SerializedName("streak_days") public int streakDays;
        @SerializedName("best_streak") public int bestStreak;
        /** When the account was made (Me: "Member since Mar 2025"); may be absent. */
        @SerializedName("created_at") public String createdAt;
        /** Only on your own profile (GET/PATCH /users/me). */
        public Preferences preferences;
    }

    /** Your settings as the server stores them (B1). Every field has a default. */
    public static class Preferences {
        @SerializedName("hide_on_boards") public boolean hideOnBoards;
        @SerializedName("weekly_goal") public int weeklyGoal = 7;
        @SerializedName("prayer_method") public int prayerMethod = 1;
        @SerializedName("prayer_school") public int prayerSchool = 1;
        public Reminders reminders = new Reminders();
        public Notify notify = new Notify();
    }

    public static class Reminders {
        public boolean enabled;
        public String time = "21:00";
        public List<Integer> days;
    }

    public static class Notify {
        public boolean activity = true;
        public boolean groups = true;
        public boolean system = true;
        public boolean streak = true;
    }

    /** GET /statistics/me — everything the Me tab shows. */
    public static class MyStats {
        public String timezone;
        @SerializedName("from_date") public String fromDate;   // yyyy-MM-dd, local
        @SerializedName("to_date") public String toDate;
        public long today;
        public long week;
        public long month;
        public long lifetime;
        @SerializedName("current_streak") public int currentStreak;
        @SerializedName("best_streak") public int bestStreak;
        @SerializedName("active_days") public int activeDays;
        /** Sparse: only days with logs, oldest first. */
        public List<DayTotal> days;
        @SerializedName("by_recitation") public List<RecitationStat> byRecitation;
    }

    public static class DayTotal {
        public String date;     // yyyy-MM-dd
        public long total;
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
        public String transliteration;
        public String translation;
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
        /** Your streak after this change (days in a row with a log). */
        public int streak;
    }

    /** One of the current user's own submissions (for the profile screen). */
    public static class MySubmissionDto {
        public String id;
        @SerializedName("recitation_id") public String recitationId;
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
        /** v3: one entry per group you're in (not the global circle), biggest first. */
        @SerializedName("groups_this_week") public List<GroupThisWeek> groupsThisWeek;
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
        /** Your own row, even when you're outside the returned entries. */
        public LeaderboardEntryDto me;
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

    // --- P4 · Ranks (B2: /leaderboards/global, /weeks/...) ---

    /** Someone on a board. Hidden people have no id, the name "A member" and no picture. */
    public static class BoardUser {
        public String id;
        @SerializedName("display_name") public String displayName;
        public String avatar;
        @SerializedName("photo_url") public String photoUrl;
        public boolean hidden;
    }

    public static class BoardEntry {
        /** Null on a total-only group board. Ties share a rank (1, 1, 3). */
        public Integer rank;
        public long total;
        public BoardUser user;
        @SerializedName("is_me") public boolean isMe;
        /** Last week's rank, for the change arrow (week scope only). */
        @SerializedName("previous_rank") public Integer previousRank;
    }

    public static class WeekInfo {
        public String id;
        @SerializedName("week_number") public int weekNumber;
        @SerializedName("starts_at") public String startsAt;
        @SerializedName("closes_at") public String closesAt;
    }

    public static class BoardResponse {
        public String scope;
        public WeekInfo week;
        public long total;
        public List<BoardEntry> entries;
        /** Your own row, even when you're outside {@link #entries}. */
        public BoardEntry me;
        @SerializedName("total_only") public boolean totalOnly;
        public Long goal;
        @SerializedName("member_count") public Integer memberCount;
    }

    public static class HallOfFameWeek {
        @SerializedName("week_id") public String weekId;
        @SerializedName("week_number") public int weekNumber;
        public String label;
        @SerializedName("starts_at") public String startsAt;
        @SerializedName("closed_at") public String closedAt;
        public long total;
        public List<BoardEntry> top;
    }

    // --- P5 · Groups (B3) ---

    /** A group as a card. MyGroup / GroupDetail / GroupPreview add to it. */
    public static class GroupCard {
        public String id;
        public String name;
        public String description;
        public String avatar;
        public String privacy;                    // public | private
        @SerializedName("join_rule") public String joinRule;   // code | approval
        @SerializedName("member_count") public int memberCount;
        @SerializedName("member_limit") public int memberLimit;
        @SerializedName("weekly_goal") public Long weeklyGoal;
        @SerializedName("total_only") public boolean totalOnly;
        @SerializedName("is_global") public boolean isGlobal;
        @SerializedName("created_at") public String createdAt;
        @SerializedName("week_total") public long weekTotal;

        public boolean isPublic() { return "public".equals(privacy); }
    }

    public static class MyGroup extends GroupCard {
        public String role;                       // owner | admin | moderator | member
        @SerializedName("my_week_total") public long myWeekTotal;
        @SerializedName("joined_at") public String joinedAt;
    }

    public static class Announcement {
        public String id;
        public String text;
        public MiniUser author;
        @SerializedName("created_at") public String createdAt;
    }

    public static class MiniUser {
        public String id;
        @SerializedName("display_name") public String displayName;
        public String avatar;
        @SerializedName("photo_url") public String photoUrl;
    }

    public static class LastWeek {
        @SerializedName("week_number") public int weekNumber;
        public long total;
        public List<BoardEntry> top;
        @SerializedName("goal_reached") public boolean goalReached;
    }

    /**
     * GET /groups/{id}: a member gets everything (role != null, invite code…);
     * an outsider gets a preview of a public group (viewer_status, top 3).
     */
    public static class GroupDetail extends MyGroup {
        @SerializedName("owner_id") public String ownerId;
        @SerializedName("invite_code") public String inviteCode;
        @SerializedName("invite_link") public String inviteLink;
        public WeekInfo week;
        @SerializedName("goal_reached") public boolean goalReached;
        @SerializedName("pending_requests") public Integer pendingRequests;
        public Announcement announcement;
        @SerializedName("last_week") public LastWeek lastWeek;
        // Preview-only fields
        public boolean full;
        public List<BoardEntry> top;
        @SerializedName("viewer_status") public String viewerStatus;   // none | member | requested | banned

        public boolean isMember() { return role != null; }
        public boolean canManage() { return "owner".equals(role) || "admin".equals(role); }
        public boolean isOwner() { return "owner".equals(role); }
    }

    public static class GroupThisWeek {
        public String id;
        public String name;
        public String avatar;
        @SerializedName("week_total") public long weekTotal;
        @SerializedName("weekly_goal") public Long weeklyGoal;
        @SerializedName("member_count") public int memberCount;
        public String role;
    }

    public static class JoinResult {
        public String status;                     // joined | requested
        public GroupCard group;
    }

    public static class GroupRankEntry {
        public int rank;
        public GroupCard group;
        public long total;
        public double average;
        @SerializedName("is_mine") public boolean isMine;
    }

    public static class GroupRanks {
        public String metric;
        public WeekInfo week;
        public List<GroupRankEntry> entries;
        public List<GroupRankEntry> mine;
    }

    public static class GroupActivity {
        public String id;
        public String type;   // group.join | group.goal_reached | week.winners | group.announcement
        public String text;
        public MiniUser actor;
        public com.google.gson.JsonObject payload;
        @SerializedName("created_at") public String createdAt;
    }

    /** A circle membership (GET /groups/{id}/members). */
    public static class Member {
        public MiniUser user;
        public String role;                       // owner | admin | moderator | member
        public String status;                     // active | pending
        @SerializedName("joined_at") public String joinedAt;
    }
}
