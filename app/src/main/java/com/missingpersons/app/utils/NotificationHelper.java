package com.missingpersons.app.utils;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.missingpersons.app.MyApplication;
import com.missingpersons.app.R;
import com.missingpersons.app.activities.NewHomeActivity;

/**
 * NotificationHelper — عرض إشعارات محلية
 */
public class NotificationHelper {

    private static int notifId = 1000;

    /**
     * إشعار تطابق وجه
     */
    public static void showMatchNotification(Context context, String personName,
                                              int percent, String reportId) {
        String title = "🎉 تطابق محتمل!";
        String body = "تم العثور على تطابق " + percent + "% لـ " + personName;

        Intent intent = new Intent(context, NewHomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("openReport", reportId);

        show(context, title, body, intent, MyApplication.CHANNEL_ID);
    }

    /**
     * إشعار رسالة شات
     */
    public static void showChatNotification(Context context, String senderName,
                                             String message) {
        String title = "💬 " + senderName;
        String body = message.length() > 80 ? message.substring(0, 80) + "..." : message;

        Intent intent = new Intent(context, NewHomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        show(context, title, body, intent, MyApplication.CHANNEL_ID);
    }

    /**
     * إشعار إداري (بلاغ جديد / سوء استخدام)
     */
    public static void showAdminNotification(Context context, String title, String body) {
        Intent intent = new Intent(context, NewHomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        show(context, title, body, intent, MyApplication.CHANNEL_ADMIN_ID);
    }

    private static void show(Context context, String title, String body,
                              Intent intent, String channelId) {
        PendingIntent pending = PendingIntent.getActivity(context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending);

        NotificationManager mgr = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (mgr != null) {
            mgr.notify(notifId++, builder.build());
        }
    }
}
