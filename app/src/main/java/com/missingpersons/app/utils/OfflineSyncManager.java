package com.missingpersons.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.util.Log;
import com.google.firebase.database.FirebaseDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;

/**
 * OfflineSyncManager — مزامنة البلاغات المحفوظة محلياً
 *
 * ══════════════════════════════════════════════════════
 * إصلاح 3 أخطاء:
 *
 * 1. Bug: مسح كل البلاغات المحفوظة فقط لو synced == total
 *    → إذا فشل واحد، لا شيء يُمسح ولو نجح البقية
 *    Fix: مسح كل بلاغ بعد رفعه بنجاح فوراً (partial clear)
 *
 * 2. Bug: لا يتحقق من الشبكة أثناء الرفع
 *    → لو انقطع الإنترنت في المنتصف، الفشل يُبتلع بصمت
 *    Fix: فحص isOnline() قبل كل رفع + retry queue
 *
 * 3. Bug: لا يمنع التكرار
 *    → نفس البلاغ يُحفظ أكثر من مرة (مثلاً عند تعدد محاولات الرفع)
 *    Fix: كل بلاغ له localId فريد — يُتجاهل إذا وُجد مسبقاً
 * ══════════════════════════════════════════════════════
 */
public class OfflineSyncManager {

    private static final String TAG  = "OfflineSync";
    private static final String PREF = "offline_reports";
    private static final String KEY  = "pending_reports";

    private static volatile boolean isSyncing = false;

    // ════════════════════════════════════════════════════════════════════
    //  Network check
    // ════════════════════════════════════════════════════════════════════

    public static boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
            context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return nc != null && (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            || nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            || nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    // ════════════════════════════════════════════════════════════════════
    //  Save offline
    // ════════════════════════════════════════════════════════════════════

    /**
     * حفظ بلاغ محلياً عند انقطاع الإنترنت.
     *
     * Fix: كل بلاغ له localId فريد لمنع التكرار.
     */
    public static void saveOffline(Context context, HashMap<String, Object> report) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            String existing = prefs.getString(KEY, "[]");
            JSONArray arr = new JSONArray(existing);

            // ── توليد localId فريد ───────────────────────────────────
            String localId = "local_" + System.currentTimeMillis()
                + "_" + (int)(Math.random() * 9999);

            // ── تحقق من التكرار ──────────────────────────────────────
            for (int i = 0; i < arr.length(); i++) {
                JSONObject existing_obj = arr.getJSONObject(i);
                // إذا نفس الـ personName + timestamp → تكرار محتمل
                Object existTs   = existing_obj.opt("timestamp");
                Object newTs     = report.get("timestamp");
                Object existName = existing_obj.opt("personName");
                Object newName   = report.get("personName");

                if (existTs != null && existTs.equals(newTs)
                    && existName != null && existName.equals(newName)) {
                    Log.w(TAG, "Duplicate offline report detected — skipping");
                    return;
                }
            }

            // ── حفظ البلاغ ──────────────────────────────────────────
            JSONObject obj = new JSONObject();
            obj.put("_localId",      localId);
            obj.put("_savedAt",      System.currentTimeMillis());
            for (String key : report.keySet()) {
                Object val = report.get(key);
                if (val != null) obj.put(key, val);
            }
            arr.put(obj);

            prefs.edit().putString(KEY, arr.toString()).apply();
            Log.d(TAG, "Saved offline [" + localId + "]. Total pending: " + arr.length());

        } catch (Exception e) {
            Log.e(TAG, "saveOffline error: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Sync
    // ════════════════════════════════════════════════════════════════════

    public static int getPendingCount(Context context) {
        try {
            return new JSONArray(
                context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                       .getString(KEY, "[]")
            ).length();
        } catch (Exception e) { return 0; }
    }

    /**
     * رفع البلاغات المعلقة عند عودة الاتصال.
     *
     * الإصلاحات:
     *   - كل بلاغ يُمسح بعد نجاحه فوراً (partial clear)
     *   - فحص isOnline() قبل كل رفع
     *   - تجنب الـ race condition عبر isSyncing flag
     */
    public static void syncPendingReports(Context context, SyncCallback callback) {
        if (!isOnline(context)) {
            Log.d(TAG, "syncPendingReports: offline — skipping");
            if (callback != null) callback.onComplete(0, getPendingCount(context));
            return;
        }

        // منع تشغيل أكثر من sync واحد في نفس الوقت
        if (isSyncing) {
            Log.d(TAG, "syncPendingReports: already running — skipping");
            return;
        }
        isSyncing = true;

        SharedPreferences prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);

        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));

            if (arr.length() == 0) {
                isSyncing = false;
                if (callback != null) callback.onComplete(0, 0);
                return;
            }

            Log.d(TAG, "Starting sync of " + arr.length() + " pending reports");
            syncNext(context, prefs, arr, 0, 0, callback);

        } catch (Exception e) {
            isSyncing = false;
            Log.e(TAG, "syncPendingReports error: " + e.getMessage());
        }
    }

    /**
     * رفع البلاغات واحداً تلو الآخر — بعد نجاح كل واحد يُمسح فوراً.
     */
    private static void syncNext(Context context, SharedPreferences prefs,
                                  JSONArray arr, int index, int synced,
                                  SyncCallback callback) {
        if (index >= arr.length()) {
            // انتهى الكل
            isSyncing = false;
            int remaining = getPendingCount(context);
            Log.d(TAG, "Sync done — synced=" + synced + " remaining=" + remaining);
            if (callback != null) callback.onComplete(synced, remaining);
            return;
        }

        // ── تحقق من الشبكة قبل كل رفع ───────────────────────────────
        if (!isOnline(context)) {
            isSyncing = false;
            int remaining = getPendingCount(context);
            Log.w(TAG, "Network lost during sync — stopping at index " + index);
            if (callback != null) callback.onComplete(synced, remaining);
            return;
        }

        try {
            JSONObject obj = arr.getJSONObject(index);
            String     localId = obj.optString("_localId", "unknown");

            // بناء report map (تجاهل الحقول الداخلية)
            HashMap<String, Object> report = new HashMap<>();
            for (int j = 0; j < obj.names().length(); j++) {
                String k = obj.names().getString(j);
                if (k.startsWith("_")) continue;   // تجاهل _localId و _savedAt
                report.put(k, obj.get(k));
            }

            String pushKey = FirebaseDatabase.getInstance()
                .getReference("reports").push().getKey();
            if (pushKey == null) {
                syncNext(context, prefs, arr, index + 1, synced, callback);
                return;
            }

            FirebaseDatabase.getInstance().getReference("reports")
                .child(pushKey).setValue(report)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Synced [" + localId + "] → " + pushKey);
                    // ── مسح هذا البلاغ فوراً بعد النجاح ────────────
                    removeSinglePending(context, prefs, localId);
                    syncNext(context, prefs, arr, index + 1, synced + 1, callback);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to sync [" + localId + "]: " + e.getMessage());
                    // تخطِّ ولا تمسح (يبقى للمحاولة القادمة)
                    syncNext(context, prefs, arr, index + 1, synced, callback);
                });

        } catch (Exception e) {
            Log.e(TAG, "Error reading pending report at index " + index + ": " + e.getMessage());
            syncNext(context, prefs, arr, index + 1, synced, callback);
        }
    }

    /**
     * مسح بلاغ واحد من القائمة المحلية بعد رفعه.
     */
    private static void removeSinglePending(Context context,
                                             SharedPreferences prefs,
                                             String localId) {
        try {
            JSONArray arr     = new JSONArray(prefs.getString(KEY, "[]"));
            JSONArray updated = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (!localId.equals(obj.optString("_localId"))) {
                    updated.put(obj);
                }
            }
            prefs.edit().putString(KEY, updated.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "removeSinglePending error: " + e.getMessage());
        }
    }

    /** مسح كل البلاغات المحلية (للاستخدام في logout) */
    public static void clearAll(Context context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
               .edit().putString(KEY, "[]").apply();
        Log.d(TAG, "clearAll: all pending reports removed");
    }

    // ════════════════════════════════════════════════════════════════════

    public interface SyncCallback {
        void onComplete(int syncedCount, int remainingCount);
    }
}
