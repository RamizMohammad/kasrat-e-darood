package in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote;

import android.content.Context;

import androidx.annotation.Nullable;

import java.io.IOException;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;
import okhttp3.Authenticator;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Refreshes an expired access token on a 401 and retries the request.
 *
 * The backend rotates refresh tokens, so concurrent 401s must not each call
 * /auth/refresh (only the first would succeed). A single lock serializes
 * refreshes; later threads detect that the stored token already changed and
 * simply retry with the new one instead of refreshing again.
 */
public class TokenAuthenticator implements Authenticator {

    private static final Object LOCK = new Object();

    private final TokenStore store;
    private final String baseUrl;
    private volatile ApiService refreshApi;

    public TokenAuthenticator(Context context, String baseUrl) {
        this.store = new TokenStore(context.getApplicationContext());
        this.baseUrl = baseUrl;
    }

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, Response response) {
        // Give up if we've already retried this request a couple of times.
        if (priorResponseCount(response) >= 2) return null;

        String requestToken = bearerOf(response.request().header("Authorization"));

        synchronized (LOCK) {
            String current = store.accessToken();
            if (current == null) return null;

            // Another thread already refreshed — retry with the fresh token.
            if (requestToken != null && !requestToken.equals(current)) {
                return retryWith(response.request(), current);
            }

            String refresh = store.refreshToken();
            if (refresh == null) {
                store.clear();
                return null;
            }

            try {
                retrofit2.Response<dto.TokenPair> r =
                        refreshApi().refresh(new dto.RefreshRequest(refresh)).execute();
                if (r.isSuccessful() && r.body() != null && r.body().accessToken != null) {
                    store.save(r.body().accessToken, r.body().refreshToken);
                    return retryWith(response.request(), r.body().accessToken);
                }
            } catch (IOException ignored) {
                // network error — fall through and give up for now
                return null;
            }

            // Refresh token rejected: the session is over.
            store.clear();
            return null;
        }
    }

    private Request retryWith(Request original, String accessToken) {
        return original.newBuilder()
                .header("Authorization", "Bearer " + accessToken)
                .build();
    }

    /** A bare client with no auth interceptor/authenticator (avoids recursion). */
    private ApiService refreshApi() {
        if (refreshApi == null) {
            OkHttpClient bare = new OkHttpClient.Builder().build();
            refreshApi = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(bare)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ApiService.class);
        }
        return refreshApi;
    }

    private static int priorResponseCount(Response response) {
        int count = 1;
        Response prior = response.priorResponse();
        while (prior != null) {
            count++;
            prior = prior.priorResponse();
        }
        return count;
    }

    @Nullable
    private static String bearerOf(@Nullable String header) {
        if (header == null) return null;
        return header.regionMatches(true, 0, "Bearer ", 0, 7)
                ? header.substring(7).trim() : header.trim();
    }
}
