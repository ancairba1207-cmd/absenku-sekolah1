package com.absenku.sekolah;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String CHANNEL_ID = "absenku_notifications";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        String title = remoteMessage.getNotification() != null
                ? remoteMessage.getNotification().getTitle()
                : remoteMessage.getData().get("title");

        String body = remoteMessage.getNotification() != null
                ? remoteMessage.getNotification().getBody()
                : remoteMessage.getData().get("body");

        if (title == null || title.isEmpty()) {
            title = "ABSENKU SEKOLAH";
        }

        if (body == null || body.isEmpty()) {
            body = "Ada pemberitahuan baru.";
        }

        showNotification(title, body);
    }

    @Override
    public void onNewToken(String token) {
        getSharedPreferences("absenku_fcm", MODE_PRIVATE)
                .edit()
                .putString("token", token)
                .apply();
        android.util.Log.d("ABSENKU_FCM", "FCM_TOKEN=" + token);
    }

    private void showNotification(String title, String body) {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        createNotificationChannel(manager);

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                                ? PendingIntent.FLAG_IMMUTABLE
                                : 0)
        );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void createNotificationChannel(NotificationManager manager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Uri soundUri = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Notifikasi ABSENKU",
                    NotificationManager.IMPORTANCE_HIGH
            );

            channel.setDescription("Notifikasi absensi, nilai dan obrolan");
            channel.setSound(soundUri, audioAttributes);
            channel.enableVibration(true);

            manager.createNotificationChannel(channel);
        }
    }
}
