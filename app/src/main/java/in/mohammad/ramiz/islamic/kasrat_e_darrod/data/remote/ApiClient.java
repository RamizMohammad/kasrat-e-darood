package in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote;

import android.content.Context;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Singleton Retrofit factory for the Noor API.
 *
 * Point {@link #BASE_URL} at your backend:
 *   - Android emulator -> http://10.0.2.2:8000/
 *   - Physical device  -> http://<your-machine-ip>:8000/
 */
public final class ApiClient {
    // 10.0.2.2 is the host machine as seen from the Android emulator.
    public static final String BASE_URL = "http://10.0.2.2:8000/";

    private static ApiService service;

    private ApiClient() {}

    public static ApiService get(Context context) {
        if (service == null) {
            TokenStore tokenStore = new TokenStore(context);
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(new AuthInterceptor(tokenStore))
                    .addInterceptor(logging)
                    .build();

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            service = retrofit.create(ApiService.class);
        }
        return service;
    }
}
