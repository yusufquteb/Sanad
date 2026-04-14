package com.missingpersons.app.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.google.firebase.database.*;
import com.missingpersons.app.R;
import java.util.*;

/**
 * AmberAlertManager — تنبيه عاجل للأطفال المفقودين
 *
 * عند موافقة الأدمن على بلاغ طفل (عمر ≤ 12):
 *   1. يُحفظ تنبيه في Firebase تحت "amber_alerts/{governorate}"
 *   2. كل مستخدم في نفس المحافظة يتلقى إشعاراً عاجلاً
 *   3. الإشعار يفتح CaseDetailActivity مباشرة
 */
public class AmberAlertManager {

    private static final String TAG           = "AmberAlertManager";
    private static final String CHANNEL_ID    = "amber_alerts";
    private static final int    CHILD_MAX_AGE = 12;

    // ─── إصدار تنبيه (يُستدعى من AdminActivity) ─────────────────

    public static void issueAlert(Context ctx, String reportId, String personName,
                                   String governorate, String imageUrl, int age) {
        if (age > CHILD_MAX_AGE || governorate == null || governorate.isEmpty()) return;

        long now = System.currentTimeMillis();
        String topicKey = "amber_" + governorate.replaceAll("\s+", "_");

        Map<String, Object> alert = new HashMap<>();
        alert.put("reportId",    reportId);
        alert.put("personName",  personName != null ? personName : "مجهول");
        alert.put("governorate", governorate);
        alert.put("imageUrl",    imageUrl != null ? imageUrl : "");
        alert.put("age",         age);
        alert.put("timestamp",   now);
        alert.put("active",      true);
        // [إصلاح] نحفظ type=amber_alert حتى يعرفه FCMService
        alert.put("type",        "amber_alert");

        // اشتراك الجهاز الحالي في topic المحافظة
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance()
                .subscribeToTopic(topicKey);
        } catch (Exception ignored) {}

        // حفظ التنبيه في Firebase — Cloud Function (مستقبلاً) ترسل FCM للـ topic
        FirebaseDatabase.getInstance()
            .getReference("amber_alerts")
            .child(governorate)
            .child(reportId + "_" + now)
            .setValue(alert)
            .addOnSuccessListener(v -> {
                Log.i(TAG, "✅ Amber Alert saved → " + governorate);
                // عرض إشعار محلي فوري للأدمن الذي أصدر التنبيه
                showAmberNotification(ctx, reportId, personName, governorate, age);
            })
            .addOnFailureListener(e ->
                Log.e(TAG, "issueAlert failed: " + e.getMessage()));
    }

    // ─── استماع للتنبيهات (يُستدعى من HomeActivity) ─────────────

    public static void listenForAlerts(Context ctx, String userGovernorate) {
        if (userGovernorate == null || userGovernorate.isEmpty()) return;
        createNotificationChannel(ctx);

        final long oneDayAgo = System.currentTimeMillis() - 86_400_000L;

        // إرسال FCM لكل مستخدمي المحافظة عبر topic
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance()
                .subscribeToTopic("amber_" + userGovernorate.replaceAll("\\s+", "_"));
        } catch (Exception ignored) {}

        FirebaseDatabase.getInstance()
            .getReference("amber_alerts")
            .child(userGovernorate)
            .orderByChild("timestamp")
            .startAt(oneDayAgo)
            .addChildEventListener(new ChildEventListener() {

                @Override
                public void onChildAdded(@NonNull DataSnapshot snap, String prev) {
                    Boolean active = snap.child("active").getValue(Boolean.class);
                    if (Boolean.FALSE.equals(active)) return;

                    Long ts = snap.child("timestamp").getValue(Long.class);
                    if (ts != null && ts < oneDayAgo) return;

                    String reportId   = snap.child("reportId").getValue(String.class);
                    String personName = snap.child("personName").getValue(String.class);
                    Long   ageLong    = snap.child("age").getValue(Long.class);
                    int    age        = ageLong != null ? ageLong.intValue() : 0;

                    showAmberNotification(ctx, reportId, personName, userGovernorate, age);
                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot s, String p) {}
                @Override
                public void onChildRemoved(@NonNull DataSnapshot s) {}
                @Override
                public void onChildMoved(@NonNull DataSnapshot s, String p) {}
                @Override
                public void onCancelled(@NonNull DatabaseError e) {
                    Log.e(TAG, "listenForAlerts error: " + e.getMessage());
                }
            });
    }

    // ─── عرض الإشعار ─────────────────────────────────────────────

    private static void showAmberNotification(Context ctx, String reportId,
                                               String personName, String governorate, int age) {
        Intent intent = new Intent(ctx,
            com.missingpersons.app.activities.CaseDetailActivity.class);
        intent.putExtra("reportId", reportId != null ? reportId : "");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int reqCode = reportId != null ? reportId.hashCode() : (int) System.currentTimeMillis();
        PendingIntent pi = PendingIntent.getActivity(ctx, reqCode,
            intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String name  = personName != null ? personName : "مجهول";
        String title = "🚨 تنبيه عاجل — طفل مفقود!";
        String body  = "الطفل " + name + " (عمر " + age + " سنة) مفقود في "
                     + governorate + " — ساعد في إيجاده!";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setColor(0xFFC62828)
            .setVibrate(new long[]{0, 500, 200, 500, 200, 500});

        try {
            NotificationManagerCompat.from(ctx)
                .notify(("amber_" + reportId).hashCode(), builder.build());
        } catch (SecurityException e) {
            Log.w(TAG, "No notification permission: " + e.getMessage());
        }
    }

    private static void createNotificationChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "تنبيهات الأطفال المفقودين",
                NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("تنبيهات عاجلة عند اختفاء أطفال في منطقتك");
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 500, 200, 500});
            ch.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────

    public static boolean isEligibleForAmberAlert(int age, String category) {
        return age <= CHILD_MAX_AGE
            || "مختطف".equalsIgnoreCase(category)
            || "طفل مجهول".equalsIgnoreCase(category);
    }

    public static void deactivateAlert(String governorate, String reportId) {
        if (governorate == null || reportId == null) return;
        // إرسال FCM لكل مستخدمي المحافظة عبر topic
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance()
                .subscribeToTopic("amber_" + governorate.replaceAll("\\s+", "_"));
        } catch (Exception ignored) {}

        FirebaseDatabase.getInstance()
            .getReference("amber_alerts").child(governorate)
            .orderByChild("reportId").equalTo(reportId)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snap) {
                    for (DataSnapshot c : snap.getChildren())
                        c.child("active").getRef().setValue(false);
                }
                @Override
                public void onCancelled(@NonNull DatabaseError e) {}
            });
    }
}
