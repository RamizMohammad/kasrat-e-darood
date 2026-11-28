package in.mohammad.ramiz.islamic.kasrat_e_darrod;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.fcm.NoorMessagingService;

/** Applies persisted UI settings and sets up push notifications at process start. */
public class NoorApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Slide / fade transitions between every screen (ui/util/ScreenMotion).
        in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.ScreenMotion.install(this);
        // Settings → Theme: Light (default), Dark or System. The per-app locale is restored by AppCompat.
        in.mohammad.ramiz.islamic.kasrat_e_darrod.util.AppPrefs.applyTheme(
                new in.mohammad.ramiz.islamic.kasrat_e_darrod.util.AppPrefs(this).theme());

        // Push notifications: create the channel and subscribe to the broadcast topic.
        NoorMessagingService.ensureChannel(this);
        NoorMessagingService.subscribeToCommunity();
        // Restore the recitation cart saved before the app was last closed.
        in.mohammad.ramiz.islamic.kasrat_e_darrod.data.Cart.get().attach(this);
    }
}
