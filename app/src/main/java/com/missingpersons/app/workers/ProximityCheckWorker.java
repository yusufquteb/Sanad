package com.missingpersons.app.workers;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.work.*;
import com.google.android.gms.location.*;
import com.google.android.gms.tasks.Tasks;
import com.missingpersons.app.utils.NotificationHelper;
import com.missingpersons.app.utils.ProximityAlertManager;
import com.missingpersons.app.models.ReportModel;
import java.util.concurrent.TimeUnit;

/**
 * ProximityCheckWorker — فحص دوري للبلاغات القريبة في الخلفية
 *
 * [إصلاح ProximityAlertManager]
 * كان ProximityAlertManager يعمل فقط داخل MapActivity (foreground).
 * هذا الـ Worker يُشغَّل كل 30 دقيقة بواسطة WorkManager حتى حين
 * يكون التطبيق في الخلفية، ويُرسل إشعاراً محلياً عند الاقتراب.
 *
 * الجدولة: MyApplication.onCreate() → scheduleProximityCheck(context)
 */
public class ProximityCheckWorker extends Worker {

    private static final String TAG              = "ProximityCheckWorker";
    private static final String WORK_NAME        = "proximity_check";
    private static final long   INTERVAL_MINUTES = 30L;

    public ProximityCheckWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();

        // تحقق من إذن الموقع
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission not granted — skipping proximity check");
            return Result.success();
        }

        try {
            // اجلب آخر موقع معروف بشكل synchronous
            FusedLocationProviderClient locationClient =
                LocationServices.getFusedLocationProviderClient(ctx);

            android.location.Location location =
                Tasks.await(locationClient.getLastLocation(), 10, TimeUnit.SECONDS);

            if (location == null) {
                Log.d(TAG, "No last known location — skipping");
                return Result.success();
            }

            double userLat = location.getLatitude();
            double userLng = location.getLongitude();

            Log.d(TAG, "Running proximity check at " + userLat + ", " + userLng);

            // استخدم ProximityAlertManager للفحص (synchronous لا يحتاج callback thread)
            ProximityAlertManager.checkProximity(ctx, userLat, userLng,
                (report, distanceKm) -> {
                    String name = report.getPersonName();
                    if (name == null) name = "مفقود";
                    String distStr = ProximityAlertManager.formatDistance(distanceKm);

                    Log.d(TAG, "Nearby case found: " + name + " @ " + distStr);

                    // إرسال إشعار محلي
                    NotificationHelper.showMatchNotification(
                        ctx, name, 0,
                        "🚨 قريب منك: " + name + " — " + distStr);
                });

            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "ProximityCheckWorker error: " + e.getMessage());
            return Result.retry();
        }
    }

    // ─── جدولة دورية ─────────────────────────────────────────────────

    /**
     * يُجدول فحصاً دورياً كل 30 دقيقة (إذا كان الجهاز متصلاً بالشبكة).
     * يُستدعى من MyApplication.onCreate()
     */
    public static void scheduleProximityCheck(Context context) {
        PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(
            ProximityCheckWorker.class, INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build())
            .setInitialDelay(5, TimeUnit.MINUTES)
            .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            work);

        Log.i(TAG, "✅ ProximityCheckWorker scheduled every " + INTERVAL_MINUTES + " min");
    }

    public static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
        Log.i(TAG, "ProximityCheckWorker cancelled");
    }
}
