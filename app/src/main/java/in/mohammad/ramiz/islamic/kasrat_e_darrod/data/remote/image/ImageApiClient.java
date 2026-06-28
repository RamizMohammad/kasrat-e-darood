package in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.image;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** Retrofit factory + URL helpers for the external image service. */
public final class ImageApiClient {
    public static final String BASE_URL = "https://image.tools.mohammadramiz.in/";

    private static ImageApi service;

    private ImageApiClient() {}

    public static ImageApi get() {
        if (service == null) {
            OkHttpClient client = new OkHttpClient.Builder().build();
            service = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ImageApi.class);
        }
        return service;
    }

    /** Build a displayable URL for an uploaded image. variant = thumb|medium|large. */
    public static String imageUrl(String imageId, String variant) {
        return BASE_URL + "image?image_id=" + imageId + "&variant=" + variant;
    }
}
