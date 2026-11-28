package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.auth;

import android.app.Activity;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiErrors;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.nav.Nav;

/** Shared bits of the sign-in screens: store the session, show errors. */
final class AuthSession {

    private AuthSession() { }

    /** Save the tokens + profile, then continue (onboarding for new devices, else home). */
    static void complete(@NonNull Activity activity, @NonNull dto.LoginResponse body) {
        TokenStore store = new TokenStore(activity);
        dto.UserDto u = body.user;
        store.saveSession(body.accessToken, body.refreshToken,
                u != null ? u.id : null, u != null ? u.displayName : null, u != null ? u.email : null);
        store.setPhotoUrl(u != null ? u.photoUrl : null);
        store.setAvatar(u != null ? u.avatar : null);
        // Home's data starts loading while the next screen opens.
        in.mohammad.ramiz.islamic.kasrat_e_darrod.data.cache.Prefetch.warm(activity);
        Nav.next(activity);
        activity.finish();
    }

    /** Map a server failure to a friendly message in the user's language. */
    @StringRes
    static int messageFor(@NonNull ApiErrors.Failure f, @StringRes int fallback) {
        if (f.rateLimited()) return R.string.err_too_many;
        if (f.code != null) {
            switch (f.code) {
                case "invalid_credentials": return R.string.err_wrong_password;
                case "email_taken": return R.string.err_email_taken;
                case "email_unverified": return R.string.err_email_unverified;
                case "invalid_code": return R.string.err_code_expired;
                case "incorrect_code": return R.string.err_code_wrong;
                case "too_many_attempts": return R.string.err_code_too_many;
                default: break;
            }
        }
        if (f.status == 401) return R.string.err_wrong_password;
        if (f.status == 409) return R.string.err_email_taken;
        return fallback;
    }

    /** Show (or hide, with null) the brick error plate. */
    static void showError(@NonNull TextView plate, @Nullable CharSequence message) {
        if (message == null) {
            plate.setVisibility(android.view.View.GONE);
        } else {
            plate.setText(message);
            plate.setVisibility(android.view.View.VISIBLE);
            plate.announceForAccessibility(message);
        }
    }
}
