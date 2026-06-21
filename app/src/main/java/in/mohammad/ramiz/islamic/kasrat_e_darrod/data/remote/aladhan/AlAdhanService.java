package in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.aladhan;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/** Retrofit interface for the AlAdhan prayer-times API. */
public interface AlAdhanService {

    /**
     * Prayer times + Hijri/Gregorian date for a gregorian {@code date} (DD-MM-YYYY).
     *
     * @param method calculation method (1 = Karachi, 2 = ISNA, 3 = MWL, 4 = Makkah ...)
     * @param school 0 = Shafi, 1 = Hanafi
     */
    @GET("v1/timings/{date}")
    Call<AlAdhanDto.Response> timings(
            @Path("date") String date,
            @Query("latitude") double latitude,
            @Query("longitude") double longitude,
            @Query("method") int method,
            @Query("school") int school);
}
