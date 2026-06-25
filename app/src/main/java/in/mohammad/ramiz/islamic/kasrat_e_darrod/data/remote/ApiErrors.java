package in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote;

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
}
