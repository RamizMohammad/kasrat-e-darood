package in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;

import retrofit2.Response;

/** Turns a failed Retrofit response into a human-readable message. */
public final class ApiErrors {
    private static final Gson GSON = new Gson();

    private ApiErrors() {}

    /** Extract the server's error message, falling back to a status-based default. */
    public static String messageFrom(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                String raw = response.errorBody().string();
                dto.ErrorEnvelope env = GSON.fromJson(raw, dto.ErrorEnvelope.class);
                if (env != null && env.error != null && env.error.message != null
                        && !env.error.message.isEmpty()) {
                    return env.error.message;
                }
            }
        } catch (Exception ignored) {
            // fall through to generic message
        }
        switch (response.code()) {
            case 401: return "Incorrect email or password.";
            case 409: return "An account with this email already exists.";
            case 422: return "Please check the details you entered.";
            default:  return "Something went wrong (" + response.code() + "). Please try again.";
        }
    }

    /** A failed response, decoded once: HTTP status + the server's error code and message. */
    public static final class Failure {
        public final int status;
        @Nullable public final String code;
        @Nullable public final String message;

        Failure(int status, @Nullable String code, @Nullable String message) {
            this.status = status;
            this.code = code;
            this.message = message;
        }

        /** True for HTTP 429 (rate limited). */
        public boolean rateLimited() { return status == 429; }
    }

    /**
     * Decode a failed response's error envelope. Reads the error body, so call
     * it at most once per response (and not together with {@link #messageFrom}).
     */
    @NonNull
    public static Failure of(@NonNull Response<?> response) {
        String code = null, message = null;
        try {
            if (response.errorBody() != null) {
                dto.ErrorEnvelope env = GSON.fromJson(response.errorBody().string(),
                        dto.ErrorEnvelope.class);
                if (env != null && env.error != null) {
                    code = env.error.code;
                    message = env.error.message;
                }
            }
        } catch (Exception ignored) {
            // not JSON: fall back to the status code
        }
        return new Failure(response.code(), code, message);
    }
}
