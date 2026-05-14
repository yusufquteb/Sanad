package com.missingpersons.app.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
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
import com.missingpersons.app.utils.NotificationHelper;
import com.missingpersons.app.widget.MissingPersonsWidget;
import java.util.Map;

/**
 * FCMService — خدمة استقبال إشعارات Firebase  [Phase 2 Fix]
 *
 * الإصلاحات:
 *  1. يعمل في الخلفية حتى لو التطبيق مغلق
 *     (FirebaseMessagingService يُشغَّل تلقائياً من النظام)
 *  2. يستخدم data messages (ليس notification-only) حتى يصل
 *     onMessageReceived حتى لو التطبيق في الخلفية
 *  3. يستخدم صوت الإشعار المخصص من NotificationHelper
 *  4. يحدّث الـ Widget بعد استقبال أي إشعار تطابق
 *  5. يدعم إشعارات التطابق المتقاطع (cross-match) الجديدة
 */
public class FCMService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";
    private static int notifIdCounter = 2000;

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "onMessageReceived: from=" + remoteMessage.getFrom());

        Map<String, String> data = remoteMessage.getData();
        String type     = data.getOrDefault("type",     "general");
        String title    = data.getOrDefault("title",    "سند | إشعار جديد");
        String body     = data.getOrDefault("body",     "");
        String reportId = data.getOrDefault("reportId", null);
        String chatId   = data.getOrDefault("chatId",   null);
        String foundId  = data.getOrDefault("foundId",  null);

        // fallback لـ notification payload (لو الـ server يُرسل notification+data معاً)
        if (body.isEmpty() && remoteMessage.getNotification() != null) {
            RemoteMessage.Notification n = remoteMessage.getNotification();
            if (n.getTitle() != null) title = n.getTitle();
            if (n.getBody()  != null) body  = n.getBody();
        }

        // إشعارات التطابق المتقاطع — أضف معلومات الطرف الآخر
        if (type.equals("missing_matched_found") || type.equals("found_matched_missing")) {
            String otherName    = data.getOrDefault("otherName",     "");
            String otherReportId = data.getOrDefault("otherReportId", "");
            if (!otherName.isEmpty())
                body = body + "\n📋 رقم البلاغ: " + otherReportId;
        }

        // بناء الـ deep link intent
        Intent intent = buildDeepLinkIntent(type, reportId, chatId, foundId);
        String channel = chooseChannel(type);

        // عرض الإشعار بالصوت المخصص
        showNotificationWithSound(title, body, intent, channel);

        // تحديث الـ Widget إذا كان الإشعار يتعلق ببلاغ جديد أو تطابق
        if (type.contains("match") || type.equals("report_approved")) {
            try {
                MissingPersonsWidget.requestUpdate(getApplicationContext());
            } catch (Exception e) {
                Log.w(TAG, "Widget update after FCM failed (non-fatal): " + e.getMessage());
            }
        }
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.d(TAG, "FCM token refreshed");
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            FirebaseDatabase.getInstance()
                .getReference("users").child(uid).child("fcmToken")
                .setValue(token)
                .addOnSuccessListener(v -> Log.d(TAG, "FCM token saved"))
                .addOnFailureListener(e -> Log.w(TAG, "FCM token save failed: " + e.getMessage()));
        }
    }

    // ────────────────────────────────────────────────────────────────

    private Intent buildDeepLinkIntent(String type, String reportId,
                                        String chatId, String foundId) {
        Intent intent;
        switch (type) {
            case "report_approved":
            case "found_matches_report":
            case "report_matches_found":
            case "missing_matched_found":
            case "found_matched_missing":
            case "match_confirmed":
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
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
            | Intent.FLAG_ACTIVITY_SINGLE_TOP
            | Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private String chooseChannel(String type) {
        if (type.contains("chat") || type.contains("message"))
            return "chat_messages";
        if (type.contains("amber"))
            return "amber_alerts";
        if (type.contains("admin"))
            return MyApplication.CHANNEL_ADMIN_ID;
        return MyApplication.CHANNEL_ID;
    }

    /**
     * [Phase 2] عرض إشعار بالصوت المخصص من NotificationHelper
     * يعمل على كل الإصدارات من API 21+
     */
    private void showNotificationWithSound(String title, String body,
                                            Intent intent, String channelId) {
        int notifId = notifIdCounter++;
        PendingIntent pending = PendingIntent.getActivity(
            this, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // جلب صوت الإشعار المخصص (أو الافتراضي)
        Uri soundUri = NotificationHelper.getNotificationSound(this);

        NotificationCompat.Builder builder =
            new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setDefaults(NotificationCompat.DEFAULT_VIBRATE);

        // الصوت على الإصدارات قبل Android O
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(soundUri);
        }

        NotificationManager mgr = (NotificationManager)
            getSystemService(Context.NOTIFICATION_SERVICE);
        if (mgr != null) mgr.notify(notifId, builder.build());
    }
}
