package in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/** Retrofit interface mapping 1:1 to the Noor REST API contract. */
public interface ApiService {

    @POST("api/v1/auth/register")
    Call<dto.LoginResponse> register(@Body dto.RegisterRequest body);

    @POST("api/v1/auth/login")
    Call<dto.LoginResponse> login(@Body dto.EmailLoginRequest body);

    @POST("api/v1/auth/firebase")
    Call<dto.LoginResponse> loginWithFirebase(@Body dto.FirebaseLoginRequest body);

    @POST("api/v1/auth/refresh")
    Call<dto.TokenPair> refresh(@Body dto.RefreshRequest body);

    /** Revokes this refresh token on the server (sign-out). 204, no body. */
    @POST("api/v1/auth/logout")
    Call<Void> logout(@Body dto.RefreshRequest body);

    @POST("api/v1/auth/google")
    Call<dto.LoginResponse> loginWithGoogle(@Body dto.GoogleLoginRequest body);

    @POST("api/v1/auth/forgot-password")
    Call<dto.OkResponse> forgotPassword(@Body dto.ForgotPasswordRequest body);

    @POST("api/v1/auth/reset-password")
    Call<dto.OkResponse> resetPassword(@Body dto.ResetPasswordRequest body);

    @GET("api/v1/auth/me")
    Call<dto.UserDto> me();

    @PATCH("api/v1/users/me")
    Call<dto.UserDto> updateMe(@Body dto.UserUpdateRequest body);

    @POST("api/v1/auth/delete-account/request")
    Call<dto.OkResponse> requestAccountDeletion();

    @POST("api/v1/auth/delete-account/confirm")
    Call<dto.OkResponse> confirmAccountDeletion(@Body dto.DeleteAccountConfirmRequest body);

    /** The library. Pass the last ETag: 304 (no body) means your copy is current. */
    @GET("api/v1/recitations")
    Call<List<dto.RecitationDto>> recitations(@Query("group_id") String groupId,
                                              @Query("q") String query,
                                              @retrofit2.http.Header("If-None-Match") String etag);

    @GET("api/v1/categories")
    Call<List<dto.CategoryDto>> categories();

    @POST("api/v1/recitations")
    Call<dto.RecitationDto> createRecitation(@Body dto.RecitationCreateRequest body);

    @POST("api/v1/submissions")
    Call<dto.SubmissionResult> submit(@Body dto.SubmissionRequest body);

    @POST("api/v1/submissions/bulk")
    Call<dto.Totals> submitBulk(@Body dto.BulkSubmitRequest body);

    @GET("api/v1/submissions/me")
    Call<List<dto.MySubmissionDto>> mySubmissions();

    /** Your most recent logs, up to {@code limit} (server max 100). */
    @GET("api/v1/submissions/me")
    Call<List<dto.MySubmissionDto>> recentSubmissions(@Query("limit") int limit);

    /** One page, newest first. Pass the previous page's X-Next-Cursor header as {@code before}. */
    @GET("api/v1/submissions/me")
    Call<List<dto.MySubmissionDto>> submissionsPage(@Query("limit") int limit,
                                                    @Query("before") String before);

    /** Undo a log within 5 minutes; returns your totals after it. */
    @DELETE("api/v1/submissions/{id}")
    Call<dto.Totals> undoSubmission(@Path("id") String submissionId);

    /** Everything the Me tab shows: heat map, per-recitation totals, streaks. */
    @GET("api/v1/statistics/me")
    Call<dto.MyStats> myStats();

    /** Partial settings change: {"preferences": {only the fields that changed}}. */
    @PATCH("api/v1/users/me")
    Call<dto.UserDto> updatePreferences(@Body com.google.gson.JsonObject body);

    @GET("api/v1/dashboard")
    Call<dto.DashboardResponse> dashboard(@Query("group_id") String groupId);

    @GET("api/v1/leaderboards")
    Call<dto.LeaderboardResponse> leaderboard(@Query("group_id") String groupId,
                                             @Query("scope") String scope);

    @GET("api/v1/statistics/community")
    Call<dto.CommunityStats> communityStats(@Query("week_id") String weekId);

    @GET("api/v1/statistics/weeks")
    Call<List<dto.WeekSummary>> weekHistory();

    @GET("api/v1/notifications")
    Call<List<dto.ActivityDto>> notifications(@Query("category") String category);

    /** Close the week now. With {@code weekId}, a second press does nothing. */
    @POST("api/v1/statistics/close-week")
    Call<dto.OkResponse> closeWeek(@Query("week_id") String weekId);

    // --- P4 · Ranks ---

    @GET("api/v1/leaderboards/global")
    Call<dto.BoardResponse> globalBoard(@Query("scope") String scope,
                                        @Query("around_me") Boolean aroundMe,
                                        @Query("limit") Integer limit);

    /** Newest first; the next page's cursor is in the X-Next-Cursor header. */
    @GET("api/v1/weeks/hall-of-fame")
    Call<List<dto.HallOfFameWeek>> hallOfFame(@Query("cursor") String cursor, @Query("limit") Integer limit);

    @GET("api/v1/weeks/current")
    Call<dto.WeekInfo> currentWeek();

    // --- P5 · Groups ---

    @GET("api/v1/groups/mine")
    Call<List<dto.MyGroup>> myGroups();

    /** Body: name, description, avatar, privacy, join_rule, weekly_goal, total_only. */
    @POST("api/v1/groups")
    Call<dto.GroupDetail> createGroup(@Body com.google.gson.JsonObject body);

    @GET("api/v1/groups/{id}")
    Call<dto.GroupDetail> group(@Path("id") String id);

    /** Partial: only the fields sent change; "weekly_goal": null clears the goal. */
    @PATCH("api/v1/groups/{id}")
    Call<dto.GroupDetail> updateGroup(@Path("id") String id, @Body com.google.gson.JsonObject body);

    /** Owner only; body {"confirm_name": "<the group's name>"}. */
    @retrofit2.http.HTTP(method = "DELETE", path = "api/v1/groups/{id}", hasBody = true)
    Call<dto.OkResponse> deleteGroup(@Path("id") String id, @Body com.google.gson.JsonObject body);

    @GET("api/v1/groups/{id}/board")
    Call<dto.BoardResponse> groupBoard(@Path("id") String id, @Query("scope") String scope,
                                       @Query("around_me") Boolean aroundMe, @Query("limit") Integer limit);

    @POST("api/v1/groups/{id}/join")
    Call<dto.JoinResult> joinPublicGroup(@Path("id") String id);

    /**
     * Phase 6 (needs the backend): preview a circle from its 6-character invite code —
     * GroupDetail with the preview fields (top, viewer_status, full). 404 = no such code.
     */
    @GET("api/v1/groups/invite/{code}")
    Call<dto.GroupDetail> groupByCode(@Path("code") String code);

    /** Join with an invite code (API_CONTRACT: POST /groups/join { invite_code }). */
    @POST("api/v1/groups/join")
    Call<dto.JoinResult> joinByCode(@Body com.google.gson.JsonObject body);

    @POST("api/v1/groups/{id}/leave")
    Call<dto.OkResponse> leaveGroup(@Path("id") String id);

    @GET("api/v1/groups/{id}/activity")
    Call<List<dto.GroupActivity>> groupActivity(@Path("id") String id, @Query("cursor") String cursor,
                                                @Query("limit") Integer limit);

    @GET("api/v1/leaderboards/groups")
    Call<dto.GroupRanks> groupRanks(@Query("metric") String metric);

    // --- Phase 6b · Circle settings (API_CONTRACT: Members, invite-code, announcements) ---

    /** Everyone in the circle (and pending requests, status "pending"), owner first. */
    @GET("api/v1/groups/{id}/members")
    Call<List<dto.Member>> groupMembers(@Path("id") String id);

    /** Body {"role": "admin" | "moderator" | "member"}. */
    @POST("api/v1/groups/{id}/members/{uid}/role")
    Call<dto.Member> setMemberRole(@Path("id") String id, @Path("uid") String uid,
                                   @Body com.google.gson.JsonObject body);

    /** Remove from the circle (they can't rejoin with the code). */
    @POST("api/v1/groups/{id}/members/{uid}/ban")
    Call<dto.Member> banMember(@Path("id") String id, @Path("uid") String uid);

    /** Let a pending request in. */
    @POST("api/v1/groups/{id}/members/{uid}/approve")
    Call<dto.Member> approveMember(@Path("id") String id, @Path("uid") String uid);

    /** New invite code; the old one stops working. Returns {"invite_code": "…"}. */
    @POST("api/v1/groups/{id}/invite-code")
    Call<com.google.gson.JsonObject> regenerateInviteCode(@Path("id") String id);

    /** Pin a note for every member. Body {"text": "…"}; an empty text clears it. */
    @POST("api/v1/groups/{id}/announcements")
    Call<dto.Announcement> postAnnouncement(@Path("id") String id, @Body com.google.gson.JsonObject body);
}
