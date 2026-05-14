package com.missingpersons.app.utils;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.missingpersons.app.MyApplication;
import com.missingpersons.app.R;
import com.missingpersons.app.activities.NewHomeActivity;

/**
 * NotificationHelper — إشعارات محلية مع دعم الصوت المخصص
 *
 * [إصلاح Phase-1]
 *   - دعم صوت الإشعار (ringtone) بصوت افتراضي أو مخصص من الإعدادات
 *   - getNotificationSound()  → يقرأ URI المحفوظ في SharedPreferences
 *   - saveNotificationSound() → يُستدعى من SettingsActivity عند الاختيار
 */
public class NotificationHelper {

    private static final String PREF_NOTIF    = "notif_prefs";
    private static final String KEY_SOUND_URI = "custom_sound_uri";
    private static int notifId = 1000;

    // ─────────────────────────────────────────────────────────────────
    //  Public API — show notifications
    // ─────────────────────────────────────────────────────────────────

    public static void showMatchNotification(Context context, String personName,
                                              int percent, String reportId) {
        String title = "🎉 تطابق محتمل!";
        String body  = "تم العثور على تطابق " + percent + "% لـ " + personName;
        Intent intent = new Intent(context, NewHomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("openReport", reportId);
        show(context, title, body, intent, MyApplication.CHANNEL_ID);
    }

    public static void showChatNotification(Context context, String senderName,
                                             String message) {
        String title = "💬 " + senderName;
        String body  = message.length() > 80 ? message.substring(0, 80) + "..." : message;
        Intent intent = new Intent(context, NewHomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        show(context, title, body, intent, MyApplication.CHANNEL_ID);
    }

    public static void showAdminNotification(Context context, String title, String body) {
        Intent intent = new Intent(context, NewHomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        show(context, title, body, intent, MyApplication.CHANNEL_ADMIN_ID);
    }

    public static void showGeneralNotification(Context context, String title, String body) {
        Intent intent = new Intent(context, NewHomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        show(context, title, body, intent, MyApplication.CHANNEL_ID);
    }

    // ─────────────────────────────────────────────────────────────────
    //  Sound management
    // ─────────────────────────────────────────────────────────────────

    /**
     * احفظ URI نغمة مخصصة (يُستدعى من SettingsActivity)
     * null = إعادة تعيين للافتراضي
     */
    public static void saveNotificationSound(Context ctx, Uri soundUri) {
        SharedPreferences.Editor ed =
            ctx.getSharedPreferences(PREF_NOTIF, Context.MODE_PRIVATE).edit();
        if (soundUri == null) {
            ed.remove(KEY_SOUND_URI);
        } else {
            ed.putString(KEY_SOUND_URI, soundUri.toString());
        }
        ed.apply();
    }

    /**
     * اجلب URI الصوت — المخصص أو الافتراضي للجهاز
     */
    public static Uri getNotificationSound(Context ctx) {
        SharedPreferences prefs =
            ctx.getSharedPreferences(PREF_NOTIF, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_SOUND_URI, null);
        if (saved != null) {
            return Uri.parse(saved);
        }
        // الافتراضي: نغمة رسائل الجهاز
        Uri defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if (defaultSound == null) {
            defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        }
        return defaultSound;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Internal builder
    // ─────────────────────────────────────────────────────────────────

    private static void show(Context context, String title, String body,
                              Intent intent, String channelId) {
        PendingIntent pending = PendingIntent.getActivity(context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Uri soundUri = getNotificationSound(context);

        NotificationCompat.Builder builder =
            new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setSound(soundUri);                 // ← صوت مخصص أو افتراضي

        // Android O+ — الصوت يُضبط على مستوى الـ Channel (في MyApplication)
        // لكن نضيف setSound هنا للتوافق مع الإصدارات الأقدم
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(soundUri);
        }

        NotificationManager mgr = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (mgr != null) {
            mgr.notify(notifId++, builder.build());
        }
    }
}
