package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.base;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.MainActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.auth.LoginActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.entry.LanguageActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.entry.OnboardingActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.AppPrefs;

/**
 * Decides where the app opens:
 * first run → Language; signed out → Login; signed in but onboarding not
 * finished → Onboarding; otherwise → Home. A v2 user who is already signed
 * in skips Language and Onboarding (they chose a language in v2 and know the app).
 */
public final class Router {

    private Router() { }

    @NonNull
    public static Intent start(@NonNull Context ctx) {
        AppPrefs prefs = new AppPrefs(ctx);
        boolean signedIn = new TokenStore(ctx).isLoggedIn();
        if (signedIn && !prefs.languageChosen()) {
            // Upgrading from v2: keep their language, don't re-onboard.
            prefs.markLanguageChosen();
            prefs.markOnboardingDone();
        }
        Intent next;
        if (!prefs.languageChosen()) {
            next = new Intent(ctx, LanguageActivity.class);
        } else if (!signedIn) {
            next = new Intent(ctx, LoginActivity.class);
        } else if (!prefs.onboardingDone()) {
            next = new Intent(ctx, OnboardingActivity.class);
        } else {
            next = new Intent(ctx, MainActivity.class);
        }
        return next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }

    /** After a successful sign-in or registration. */
    @NonNull
    public static Intent afterSignIn(@NonNull Context ctx) {
        return start(ctx);
    }
}
