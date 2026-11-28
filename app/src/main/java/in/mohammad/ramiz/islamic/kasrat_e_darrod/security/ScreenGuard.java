package in.mohammad.ramiz.islamic.kasrat_e_darrod.security;

import android.app.Activity;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;

/**
 * For screens that show passwords, one-time codes or account deletion:
 *  - no screenshots, screen recording or preview in Recents (FLAG_SECURE);
 *  - taps are ignored while another app draws over the window
 *    (tapjacking: a fake overlay tricking someone into confirming).
 */
public final class ScreenGuard {

    private ScreenGuard() { }

    /** Call in onCreate, before or after setContentView. */
    public static void protect(@NonNull Activity a) {
        a.getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        View decor = a.getWindow().getDecorView();
        decor.setFilterTouchesWhenObscured(true);
        if (android.os.Build.VERSION.SDK_INT >= 31) a.getWindow().setHideOverlayWindows(true);
    }
}
