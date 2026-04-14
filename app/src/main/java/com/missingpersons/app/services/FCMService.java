package com.missingpersons.app.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.missingpersons.app.MyApplication;
import com.missingpersons.app.R;
import com.missingpersons.app.activities.NewHomeActivity;
import com.missingpersons.app.activities.ChatActivity;
import com.missingpersons.app.activities.CaseDetailActivity;
import java.util.Map;

public class FCMService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Map<String, String> data = remoteMessage.getData();
        String type    = data.getOrDefault("type",  "general");
        String title   = data.getOrDefault("title", "تطبيق المفقودين");
        String body    = data.getOrDefault("body",  "");
        String reportId = data.getOrDefault("reportId", null);
        String chatId  = data.getOrDefault("chatId", null);

        // fallback لـ notification payload
        if (body.isEmpty() && remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle() != null
                ? remoteMessage.getNotification().getTitle() : title;
            body  = remoteMessage.getNotification().getBody() != null
                ? remoteMessage.getNotification().getBody() : "";
        }

        // اختر الـ deep link حسب النوع
        Intent intent = buildDeepLinkIntent(type, reportId, chatId);
        String channel = chooseChannel(type);

        showNotification(title, body, intent, channel, type);
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            FirebaseDatabase.getInstance()
                .getReference("users").child(uid).child("fcmToken")
                .setValue(token);
        }
    }

    // ════════════════════════════════════════════════════════════════════

    /** يبني Intent بالـ deep link الصح حسب نوع الإشعار */
    private Intent buildDeepLinkIntent(String type, String reportId, String chatId) {
        Intent intent;
        switch (type) {
            case "report_approved":
            case "found_matches_report":
            case "report_matches_found":
            case "sighting_match":
                if (reportId != null) {
                    intent = new Intent(this, CaseDetailActivity.class);
                    intent.putExtra("reportId", reportId);
                } else {
                    intent = new Intent(this, NewHomeActivity.class);
                }
                break;
            case "chat_message":
            case "admin_message":
                intent = new Intent(this, ChatActivity.class);
                if (chatId != null) intent.putExtra("chatIdOverride", chatId);
                break;
            case "amber_alert":
                if (reportId != null) {
                    intent = new Intent(this, CaseDetailActivity.class);
                    intent.putExtra("reportId", reportId);
                } else {
                    intent = new Intent(this, NewHomeActivity.class);
                }
                break;
            case "broadcast":
            case "admin_face_match":
            default:
                intent = new Intent(this, NewHomeActivity.class);
                break;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    /** يختار قناة الإشعار حسب النوع */
    private String chooseChannel(String type) {
        switch (type) {
            case "amber_alert":
                return "amber_alerts";
            case "admin_face_match":
            case "found_matches_report":
            case "report_matches_found":
                return MyApplication.CHANNEL_ADMIN_ID;
            default:
                return MyApplication.CHANNEL_ID;
        }
    }

    private void showNotification(String title, String body,
                                   Intent intent, String channel, String type) {
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            (int) System.currentTimeMillis(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        int priority = isHighPriority(type)
            ? NotificationCompat.PRIORITY_MAX
            : NotificationCompat.PRIORITY_HIGH;

        NotificationCompat.Builder builder =
            new NotificationCompat.Builder(this, channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(priority)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0, 400, 200, 400});

        NotificationManager manager =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null)
            manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private boolean isHighPriority(String type) {
        return "found_matches_report".equals(type)
            || "report_matches_found".equals(type)
            || "admin_face_match".equals(type)
            || "amber_alert".equals(type);
    }
}
