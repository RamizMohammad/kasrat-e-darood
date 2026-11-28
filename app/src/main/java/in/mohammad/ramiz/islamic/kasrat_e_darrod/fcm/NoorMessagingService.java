package in.mohammad.ramiz.islamic.kasrat_e_darrod.fcm;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.notifications.NotificationsActivity;

/** Receives FCM pushes and shows them, even when the app is in the background. */
public class NoorMessagingService extends FirebaseMessagingService {

    public static final String CHANNEL_ID = "noor_default";
    public static final String COMMUNITY_TOPIC = "community";
    private static int counter = 1000;

    /** Creates the notification channel (idempotent). Call once at app start. */
    public static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    ctx.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(ctx.getString(R.string.notif_channel_desc));
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    /** Subscribe this install to the community broadcast topic. */
    public static void subscribeToCommunity() {
        FirebaseMessaging.getInstance().subscribeToTopic(COMMUNITY_TOPIC);
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        // Topic-based delivery needs no per-device registration; just ensure
        // this install stays subscribed to the community topic.
        subscribeToCommunity();
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);

        String title = getString(R.string.app_name);
        String body = "";
        if (message.getNotification() != null) {
            if (message.getNotification().getTitle() != null) {
                title = message.getNotification().getTitle();
            }
            if (message.getNotification().getBody() != null) {
                body = message.getNotification().getBody();
            }
        } else if (!message.getData().isEmpty()) {
            title = message.getData().containsKey("title")
                    ? message.getData().get("title") : title;
            body = message.getData().containsKey("body")
                    ? message.getData().get("body") : body;
        }
        show(title, body);
    }

    private void show(String title, String body) {
        ensureChannel(this);

        Intent open = new Intent(this, NotificationsActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, flags);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        try {
            NotificationManagerCompat.from(this).notify(counter++, b.build());
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS not granted — nothing to show.
        }
    }
}
