package in.mohammad.ramiz.islamic.kasrat_e_darrod;

import android.app.Application;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.fcm.NoorMessagingService;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.AppPrefs;

/** Applies persisted UI settings and sets up push notifications at process start. */
public class NoorApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Per-app locale is restored automatically by AppCompat; night mode is not.
        AppPrefs.applyDarkMode(new AppPrefs(this).darkMode());

        // Push notifications: create the channel and subscribe to the broadcast topic.
        NoorMessagingService.ensureChannel(this);
        NoorMessagingService.subscribeToCommunity();
    }
}
