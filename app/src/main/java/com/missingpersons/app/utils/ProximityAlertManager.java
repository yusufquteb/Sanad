package com.missingpersons.app.utils;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.database.*;
import com.missingpersons.app.models.ReportModel;
import java.util.ArrayList;
import java.util.List;

/**
 * ProximityAlertManager — تنبيه القرب من المفقودين
 *
 * ══════════════════════════════════════════════════════
 * إصلاح استنزاف البطارية:
 *
 * المشكلة قبل الإصلاح:
 *   - checkProximity() تُنشئ Firebase listener جديد مع كل تحديث موقع
 *   - المستخدم يتحرك → onLocationResult يُطلَق كل 3 ثوانٍ
 *   - كل استدعاء يفتح اتصال Firebase جديد → 20 طلب/دقيقة
 *   - لا يوجد cancelAll() → الـ listeners تتراكم حتى بعد onPause
 *
 * الإصلاحات:
 *   1. Throttle: فحص واحد كل 60 ثانية على الأقل
 *   2. تخزين نتيجة الفحص الأخير (cache بسيط)
 *   3. cancelAll() لإيقاف كل الـ listeners — استدعِها من onPause()
 *   4. تتبع الـ listener النشط الوحيد وإلغاؤه قبل فتح جديد
 * ══════════════════════════════════════════════════════
 */
public class ProximityAlertManager {

    private static final String TAG              = "ProximityAlert";
    private static final double ALERT_RADIUS_KM  = 2.0;
    private static final long   MIN_CHECK_INTERVAL_MS = 60_000L; // دقيقة واحدة بين كل فحص

    // ── تتبع الـ listener النشط ──────────────────────────────────────
    private static DatabaseReference activeRef;
    private static ValueEventListener activeListener;

    // ── Throttle ──────────────────────────────────────────────────────
    private static long lastCheckTime = 0L;

    public interface ProximityCallback {
        void onNearbyCase(ReportModel report, double distanceKm);
    }

    // ════════════════════════════════════════════════════════════════════
    //  Public API
    // ════════════════════════════════════════════════════════════════════

    /**
     * فحص البلاغات القريبة من موقع المستخدم.
     *
     * مُقيَّد: لا يُنفَّذ أكثر من مرة كل 60 ثانية.
     * آمن للاستدعاء من onLocationResult مباشرةً.
     *
     * استدعِ cancelAll() من onPause() لوقف الفحوصات.
     */
    public static void checkProximity(Context context, double userLat, double userLng,
                                       ProximityCallback callback) {
        long now = System.currentTimeMillis();

        // ── Throttle: تجاهل الاستدعاء لو أقل من 60 ثانية ────────────
        if (now - lastCheckTime < MIN_CHECK_INTERVAL_MS) {
            Log.d(TAG, "checkProximity throttled — last check was "
                + ((now - lastCheckTime) / 1000) + "s ago");
            return;
        }
        lastCheckTime = now;

        // ── إلغاء أي listener سابق قبل فتح جديد ─────────────────────
        cancelActiveListener();

        DatabaseReference ref = FirebaseDatabase.getInstance()
            .getReference("reports");

        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // أزل نفسك من الـ tracking فور التنفيذ
                clearTracking();

                for (DataSnapshot child : snapshot.getChildren()) {
                    ReportModel report = child.getValue(ReportModel.class);
                    if (report == null) continue;

                    double repLat = report.getLatitude();
                    double repLng = report.getLongitude();
                    if (repLat == 0 && repLng == 0) continue;

                    double distance = haversineKm(userLat, userLng, repLat, repLng);
                    if (distance <= ALERT_RADIUS_KM) {
                        report.setReportId(child.getKey());
                        callback.onNearbyCase(report, distance);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                clearTracking();
                Log.e(TAG, "checkProximity cancelled: " + error.getMessage());
            }
        };

        // تتبع الـ listener النشط
        activeRef      = ref;
        activeListener = listener;
        ref.orderByChild("status").equalTo("approved")
           .addListenerForSingleValueEvent(listener);
    }

    /**
     * إيقاف كل الفحوصات الجارية.
     *
     * استدعِها دائماً من:
     *   @Override protected void onPause() {
     *       super.onPause();
     *       ProximityAlertManager.cancelAll();
     *   }
     */
    public static void cancelAll() {
        cancelActiveListener();
        // إعادة ضبط الـ throttle — لضمان فحص جديد عند العودة لـ onResume
        lastCheckTime = 0L;
        Log.d(TAG, "cancelAll() called — listeners stopped");
    }

    /**
     * إعادة تعيين الـ throttle يدوياً (مثلاً عند تغيير الفلتر).
     */
    public static void resetThrottle() {
        lastCheckTime = 0L;
    }

    // ════════════════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════════════════

    private static void cancelActiveListener() {
        if (activeRef != null && activeListener != null) {
            try {
                activeRef.removeEventListener(activeListener);
            } catch (Exception ignored) {}
        }
        clearTracking();
    }

    private static void clearTracking() {
        activeRef      = null;
        activeListener = null;
    }

    /** حساب المسافة بين نقطتين بالكيلومترات (Haversine Formula) */
    public static double haversineKm(double lat1, double lng1,
                                      double lat2, double lng2) {
        final double R    = 6371.0;
        double       dLat = Math.toRadians(lat2 - lat1);
        double       dLng = Math.toRadians(lng2 - lng1);
        double       a    = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                          + Math.cos(Math.toRadians(lat1))
                          * Math.cos(Math.toRadians(lat2))
                          * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /** تنسيق المسافة للعرض */
    public static String formatDistance(double km) {
        if (km < 1.0) return String.format("%.0f متر", km * 1000);
        return String.format("%.1f كم", km);
    }
}
