package in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.image;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;

/** Client for the external image service (image.tools.mohammadramiz.in). */
public interface ImageApi {

    class UploadResponse {
        public boolean ok;
        public String image_id;
        public String meta_id;
    }

    @Multipart
    @POST("upload")
    Call<UploadResponse> upload(@Query("user_id") String userId,
                               @Part MultipartBody.Part file);
}
