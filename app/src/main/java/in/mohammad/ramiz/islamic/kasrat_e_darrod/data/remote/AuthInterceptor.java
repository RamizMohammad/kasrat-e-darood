package in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote;

import androidx.annotation.NonNull;

import java.io.IOException;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/** Attaches the app JWT bearer token to every outgoing request. */
public class AuthInterceptor implements Interceptor {
    private final TokenStore tokenStore;

    public AuthInterceptor(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        String token = tokenStore.accessToken();
        if (token == null) {
            return chain.proceed(original);
        }
        Request authed = original.newBuilder()
                .header("Authorization", "Bearer " + token)
                .build();
        return chain.proceed(authed);
    }
}
