package com.missingpersons.app.utils;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.*;
import com.google.firebase.database.*;
import java.util.*;

/**
 * GeofenceHelper — إشعارات عند دخول منطقة فيها بلاغ مفقود
 *
 * يعمل بالخلفية ويتحقق كل 5 دقائق
 * النطاق: 2 كم حول كل بلاغ
 */
public class GeofenceHelper {

    private static final String TAG = "GeofenceHelper";
    private static final float ALERT_RADIUS_KM = 2.0f;
    private static final long CHECK_INTERVAL_MS = 5 * 60 * 1000; // 5 دقائق
    private static final String PREF_NAME = "geofence_alerts";

    private static FusedLocationProviderClient locationClient;
    private static LocationCallback locationCallback;
    private static boolean isRunning = false;

    public interface NearbyAlertCallback {
        void onNearbyReport(String reportId, String personName,
                           double lat, double lng, float distanceKm);
    }

    /**
     * بدء المراقبة
     */
    public static void startMonitoring(Context context, NearbyAlertCallback callback) {
        if (isRunning) return;
        if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        locationClient = LocationServices.getFusedLocationProviderClient(context);

        LocationRequest req = new LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY, CHECK_INTERVAL_MS)
            .setMinUpdateIntervalMillis(CHECK_INTERVAL_MS / 2)
            .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location loc = result.getLastLocation();
                if (loc != null) {
                    checkNearbyReports(context, loc.getLatitude(),
                        loc.getLongitude(), callback);
                }
            }
        };

        locationClient.requestLocationUpdates(req, locationCallback,
            Looper.getMainLooper());
        isRunning = true;
        Log.d(TAG, "Geofence monitoring started");
    }

    /**
     * إيقاف المراقبة
     */
    public static void stopMonitoring() {
        if (locationClient != null && locationCallback != null) {
            locationClient.removeLocationUpdates(locationCallback);
        }
        isRunning = false;
    }

    private static void checkNearbyReports(Context context, double userLat,
                                            double userLng,
                                            NearbyAlertCallback callback) {
        FirebaseDatabase.getInstance().getReference("reports")
            .orderByChild("status").equalTo("approved")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    SharedPreferences prefs = context.getSharedPreferences(
                        PREF_NAME, Context.MODE_PRIVATE);

                    for (DataSnapshot child : snapshot.getChildren()) {
                        Object latObj = child.child("latitude").getValue();
                        Object lngObj = child.child("longitude").getValue();
                        if (latObj == null || lngObj == null) continue;

                        double lat, lng;
                        try {
                            lat = latObj instanceof Double ? (Double) latObj
                                : Double.parseDouble(latObj.toString());
                            lng = lngObj instanceof Double ? (Double) lngObj
                                : Double.parseDouble(lngObj.toString());
                        } catch (Exception e) { continue; }

                        if (lat == 0 && lng == 0) continue;

                        float dist = distanceKm(userLat, userLng, lat, lng);
                        if (dist <= ALERT_RADIUS_KM) {
                            String rid = child.getKey();
                            // لا ترسل نفس الإشعار مرتين في 24 ساعة
                            long lastAlert = prefs.getLong("alert_" + rid, 0);
                            if (System.currentTimeMillis() - lastAlert < 24 * 60 * 60 * 1000)
                                continue;

                            String name = child.child("personName")
                                .getValue(String.class);
                            if (name == null) name = "مجهول";

                            prefs.edit()
                                .putLong("alert_" + rid, System.currentTimeMillis())
                                .apply();

                            if (callback != null) {
                                callback.onNearbyReport(rid, name, lat, lng, dist);
                            }

                            // إشعار محلي
                            NotificationHelper.showMatchNotification(context,
                                name, 0,
                                "🚨 قريب منك: " + name + " — " +
                                String.format(Locale.US, "%.1f كم", dist));
                        }
                    }
                }

                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
    }

    private static float distanceKm(double lat1, double lon1,
                                      double lat2, double lon2) {
        double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (float)(R * c);
    }
}
