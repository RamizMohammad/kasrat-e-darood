package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.MainActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.auth.LoginActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.auth.SplashActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.entry.LanguageActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.entry.OnboardingActivity;

/**
 * Screen-to-screen motion for every activity, set up once in NoorApp:
 *
 *  - Inside a flow (Home → circle → settings…): the new screen slides in
 *    from the end edge over the old one, which drifts back a quarter and dims;
 *    Back reverses it. Mirrored automatically in Urdu (anim-ldrtl/).
 *  - Between flows (sign-in → Home, sign-out → sign-in, onboarding): a quick
 *    fade-through, since nothing "goes back" there.
 *
 * Android 12–13 read the theme's windowAnimationStyle; Android 14+ needs
 * overrideActivityTransition per activity (also used by predictive Back).
 * The system "Remove animations" setting turns all of it off.
 */
public final class ScreenMotion implements Application.ActivityLifecycleCallbacks {

    private ScreenMotion() { }

    public static void install(@NonNull Application app) {
        app.registerActivityLifecycleCallbacks(new ScreenMotion());
    }

    /** Entry points of a flow: arriving at them fades instead of sliding. */
    private static boolean isFlowRoot(@NonNull Activity a) {
        return a instanceof MainActivity || a instanceof LoginActivity
                || a instanceof OnboardingActivity || a instanceof LanguageActivity;
    }

    // Double-tap guard: the same screen with the same extras, opened twice
    // within this window (two quick taps on a card), keeps only the first.
    private static final long DOUBLE_OPEN_MS = 700;
    @Nullable private String lastOpen;
    private long lastOpenAt;

    @NonNull
    private static String signature(@NonNull Activity a) {
        android.content.Intent i = a.getIntent();
        Bundle x = i == null ? null : i.getExtras();
        StringBuilder s = new StringBuilder(a.getClass().getName());
        if (x != null) {
            java.util.TreeSet<String> keys = new java.util.TreeSet<>(x.keySet());
            for (String k : keys) s.append('|').append(k).append('=').append(x.get(k));
        }
        return s.toString();
    }

    @Override
    public void onActivityCreated(@NonNull Activity a, @Nullable Bundle saved) {
        if (saved == null && !(a instanceof SplashActivity)) {
            String sig = signature(a);
            long now = android.os.SystemClock.elapsedRealtime();
            if (sig.equals(lastOpen) && now - lastOpenAt < DOUBLE_OPEN_MS) {
                a.finish();                                   // the duplicate from a double tap
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    a.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0);
                } else {
                    //noinspection deprecation
                    a.overridePendingTransition(0, 0);
                }
                return;
            }
            lastOpen = sig;
            lastOpenAt = now;
        }
        if (a instanceof SplashActivity) return;           // the SplashScreen API animates its own exit
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return;   // theme handles 12–13
        if (isFlowRoot(a)) {
            a.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN,
                    R.anim.screen_fade_in, R.anim.screen_fade_out);
        } else {
            a.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN,
                    R.anim.screen_open_enter, R.anim.screen_open_exit);
        }
        a.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.screen_close_enter, R.anim.screen_close_exit);
    }

    @Override public void onActivityStarted(@NonNull Activity a) { }
    @Override public void onActivityResumed(@NonNull Activity a) { }
    @Override public void onActivityPaused(@NonNull Activity a) { }
    @Override public void onActivityStopped(@NonNull Activity a) { }
    @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle out) { }
    @Override public void onActivityDestroyed(@NonNull Activity a) { }
}
