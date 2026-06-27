package in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
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

    @GET("api/v1/recitations")
    Call<List<dto.RecitationDto>> recitations(@Query("group_id") String groupId,
                                              @Query("q") String query);

    @POST("api/v1/submissions")
    Call<dto.SubmissionResult> submit(@Body dto.SubmissionRequest body);

    @POST("api/v1/submissions/bulk")
    Call<dto.Totals> submitBulk(@Body dto.BulkSubmitRequest body);

    @GET("api/v1/submissions/me")
    Call<List<dto.MySubmissionDto>> mySubmissions();

    @GET("api/v1/dashboard")
    Call<dto.DashboardResponse> dashboard(@Query("group_id") String groupId);

    @GET("api/v1/feed")
    Call<List<dto.ActivityDto>> feed(@Query("group_id") String groupId);

    @GET("api/v1/leaderboards")
    Call<dto.LeaderboardResponse> leaderboard(@Query("group_id") String groupId,
                                             @Query("scope") String scope);
}
