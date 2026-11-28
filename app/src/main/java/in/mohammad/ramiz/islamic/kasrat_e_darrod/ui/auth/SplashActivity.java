package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.auth;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.BuildConfig;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.cache.Prefetch;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.security.Integrity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.base.Router;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.KasratDialog;

/**
 * Launcher. The Android 12+ SplashScreen API draws the crescent animation
 * (Theme.Kasrat.Starting); this activity checks the device, warms the cache
 * (so Home opens with data) and decides where to go next.
 */
@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private static final String PREFS = "kasrat_security";
    private static final String KEY_ROOT_WARNED = "root_warned_version";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        Integrity.Status status = Integrity.check(this);
        if (status == Integrity.Status.TAMPERED) {
            // A modified copy or a hooked process: never keep a session here.
            new TokenStore(this).clear();
            KasratDialog.with(this).icon(R.drawable.ic_ph_lock)
                    .title(R.string.security_tampered_title).body(R.string.security_tampered_body)
                    .cancelable(false)
                    .primary(R.string.security_close, d -> {
                        d.dismiss();
                        finishAndRemoveTask();
                    })
                    .show();
            return;
        }

        // Start the first requests now, in parallel with the splash and routing.
        Prefetch.warm(this);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (status == Integrity.Status.ROOTED
                && prefs.getInt(KEY_ROOT_WARNED, 0) != BuildConfig.VERSION_CODE) {
            // Once per app version: tell them, then carry on.
            KasratDialog.with(this).icon(R.drawable.ic_ph_lock)
                    .title(R.string.security_rooted_title).body(R.string.security_rooted_body)
                    .cancelable(false)
                    .primary(R.string.security_rooted_continue, d -> {
                        d.dismiss();
                        prefs.edit().putInt(KEY_ROOT_WARNED, BuildConfig.VERSION_CODE).apply();
                        go();
                    })
                    .show();
            return;
        }
        go();
    }

    private void go() {
        startActivity(Router.start(this));
        finish();
    }
}
