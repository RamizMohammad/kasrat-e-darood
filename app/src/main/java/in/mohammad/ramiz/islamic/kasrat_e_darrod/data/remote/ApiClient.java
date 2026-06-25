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
 * The app talks to the live production backend over HTTPS. For local backend
 * development, point {@link #BASE_URL} at http://10.0.2.2:8000/ (emulator) and
 * temporarily re-enable cleartext traffic in AndroidManifest.xml.
 */
public final class ApiClient {
    /** Live production API. */
    public static final String BASE_URL = "https://kasrat.darood.mohammadramiz.in/";

    private static ApiService service;

    private ApiClient() {}

    public static ApiService get(Context context) {
        if (service == null) {
            TokenStore tokenStore = new TokenStore(context);
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(new AuthInterceptor(tokenStore))
                    .authenticator(new TokenAuthenticator(context, BASE_URL))
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
