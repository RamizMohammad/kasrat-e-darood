package in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import androidx.annotation.NonNull;

import java.util.concurrent.TimeUnit;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.BuildConfig;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.cache.ApiCache;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;
import okhttp3.CertificatePinner;
import okhttp3.ConnectionPool;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Singleton Retrofit factory for the Kasrat API.
 *
 * The server address comes from secrets.properties (API_BASE_URL) via
 * BuildConfig. Release builds pin the API's TLS certificate chain
 * (API_CERT_PINS, default: Let's Encrypt roots), so a proxy or a rogue CA
 * can't read or change the traffic even on a phone that trusts one.
 */
public final class ApiClient {
    /** The Kasrat API, from API_BASE_URL in secrets.properties (always ends in "/"). */
    public static final String BASE_URL = BuildConfig.API_BASE_URL;

    private static volatile ApiService service;
    private static volatile OkHttpClient http;

    private ApiClient() {}

    public static ApiService get(Context context) {
        ApiService s = service;
        if (s != null) return s;
        synchronized (ApiClient.class) {
            if (service == null) {
                Context app = context.getApplicationContext();
                service = new Retrofit.Builder()
                        .baseUrl(BASE_URL)
                        .client(http(app))
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                        .create(ApiService.class);
            }
            return service;
        }
    }

    /** The shared client (also used by TokenAuthenticator's refresh call, minus auth). */
    @NonNull
    static OkHttpClient http(@NonNull Context app) {
        OkHttpClient c = http;
        if (c != null) return c;
        synchronized (ApiClient.class) {
            if (http == null) {
                boolean debuggable = (app.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
                // Full bodies (passwords, OTPs, tokens) are logged only in debuggable
                // builds. Release builds log nothing.
                HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
                logging.setLevel(debuggable ? HttpLoggingInterceptor.Level.BODY : HttpLoggingInterceptor.Level.NONE);
                logging.redactHeader("Authorization");

                OkHttpClient.Builder b = base(app);
                if (debuggable) b.eventListenerFactory(NetTiming.FACTORY);   // logcat tag KasratNet
                http = b
                        .addInterceptor(new AuthInterceptor(new TokenStore(app)))
                        .addInterceptor(ApiClient::markWrites)
                        .authenticator(new TokenAuthenticator(app, BASE_URL))
                        .addInterceptor(logging)
                        .build();
            }
            return http;
        }
    }

    /** Timeouts, connection reuse and certificate pinning, without auth. */
    @NonNull
    static OkHttpClient.Builder base(@NonNull Context app) {
        OkHttpClient.Builder b = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                // Mobile networks (NAT) silently drop idle connections after ~30-60 s;
                // reusing such a dead one stalls a request for seconds. So: ping the
                // HTTP/2 connection every 20 s while it's pooled (keeps the NAT entry
                // alive and spots a dead one at once) and let idle ones go after 60 s,
                // before nginx's own keep-alive (75 s) closes them.
                .pingInterval(20, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(5, 60, TimeUnit.SECONDS));
        CertificatePinner pinner = pinner(app);
        if (pinner != null) b.certificatePinner(pinner);
        return b;
    }

    /**
     * Pins for the API host. Off in debuggable builds (so a debugging proxy
     * works), when API_CERT_PINS = "off", and for local addresses.
     */
    private static CertificatePinner pinner(@NonNull Context app) {
        boolean debuggable = (app.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        String pins = BuildConfig.API_CERT_PINS == null ? "" : BuildConfig.API_CERT_PINS.trim();
        HttpUrl url = HttpUrl.parse(BASE_URL);
        if (debuggable || pins.isEmpty() || "off".equalsIgnoreCase(pins) || url == null
                || !url.isHttps() || isLocal(url.host())) {
            return null;
        }
        CertificatePinner.Builder p = new CertificatePinner.Builder();
        for (String pin : pins.split(",")) {
            String v = pin.trim();
            if (!v.isEmpty()) p.add(url.host(), v.startsWith("sha256/") ? v : "sha256/" + v);
        }
        return p.build();
    }

    private static boolean isLocal(@NonNull String host) {
        return host.equals("localhost") || host.equals("10.0.2.2") || host.startsWith("192.168.")
                || host.startsWith("10.") || host.endsWith(".local") || host.equals("127.0.0.1");
    }

    /** A successful write makes every cached copy stale (screens then refresh). */
    @NonNull
    private static Response markWrites(@NonNull Interceptor.Chain chain) throws java.io.IOException {
        Response r = chain.proceed(chain.request());
        if (r.isSuccessful() && !"GET".equals(chain.request().method())) ApiCache.markWrite();
        return r;
    }
}
