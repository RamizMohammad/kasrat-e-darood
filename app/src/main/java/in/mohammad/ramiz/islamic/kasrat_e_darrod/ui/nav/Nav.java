package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.nav;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.auth.ForgotPasswordActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.auth.LoginActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.auth.RegisterActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.base.Router;

/** Every in-app jump goes through here, so screens never hard-code each other. */
public final class Nav {

    private Nav() { }

    public static void login(@NonNull Context ctx) {
        ctx.startActivity(new Intent(ctx, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    public static void register(@NonNull Context ctx, @Nullable String email) {
        ctx.startActivity(new Intent(ctx, RegisterActivity.class)
                .putExtra(RegisterActivity.EXTRA_EMAIL, email));
    }

    public static void forgotPassword(@NonNull Context ctx, @Nullable String email) {
        ctx.startActivity(new Intent(ctx, ForgotPasswordActivity.class)
                .putExtra(ForgotPasswordActivity.EXTRA_EMAIL, email));
    }

    /** Continue to wherever the app should be now (onboarding or home). */
    public static void next(@NonNull Context ctx) {
        ctx.startActivity(Router.start(ctx));
    }
}
