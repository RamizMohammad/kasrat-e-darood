package in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.aladhan;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** Singleton Retrofit client for the public AlAdhan API. */
public final class AlAdhanClient {
    public static final String BASE_URL = "https://api.aladhan.com/";

    /** Default calculation method (1 = University of Islamic Sciences, Karachi). */
    public static final int DEFAULT_METHOD = 1;
    /** 0 = Shafi (standard), 1 = Hanafi. */
    public static final int DEFAULT_SCHOOL = 1;

    private static AlAdhanService service;

    private AlAdhanClient() {}

    public static AlAdhanService get() {
        if (service == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            service = retrofit.create(AlAdhanService.class);
        }
        return service;
    }
}
