package in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

/** Retrofit interface mapping 1:1 to the Noor REST API contract. */
public interface ApiService {

    @POST("api/v1/auth/firebase")
    Call<dto.LoginResponse> loginWithFirebase(@Body dto.FirebaseLoginRequest body);

    @GET("api/v1/auth/me")
    Call<dto.UserDto> me();

    @GET("api/v1/recitations")
    Call<List<dto.RecitationDto>> recitations(@Query("group_id") String groupId,
                                              @Query("q") String query);

    @POST("api/v1/submissions")
    Call<dto.SubmissionResult> submit(@Body dto.SubmissionRequest body);

    @GET("api/v1/dashboard")
    Call<dto.DashboardResponse> dashboard(@Query("group_id") String groupId);

    @GET("api/v1/leaderboards")
    Call<dto.LeaderboardResponse> leaderboard(@Query("group_id") String groupId,
                                             @Query("scope") String scope);
}
