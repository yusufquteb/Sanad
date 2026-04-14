package com.missingpersons.app.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.*;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.*;
import com.missingpersons.app.utils.FaceEmbeddingManager;
import com.missingpersons.app.utils.NotificationHelper;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * BackgroundMatchWorker — مطابقة خلفية يومية
 *
 * يشغّله WorkManager مرة يومياً لمقارنة
 * البلاغات الجديدة (آخر 24 ساعة) مع كل found_persons.
 *
 * [إصلاح خطأ-09 بيانات وهمية] تمت مزامنة THRESHOLD مع
 * FaceEmbeddingManager.MATCH_THRESHOLD (0.70f) بدلاً من
 * قيمة مختلفة مبرمجة بشكل ثابت (0.65f كان hardcoded).
 *
 * ═══════════════════════════════════════
 * جدولة التشغيل من MyApplication.onCreate():
 *   BackgroundMatchWorker.scheduleDailyMatch(this);
 * ═══════════════════════════════════════
 */
public class BackgroundMatchWorker extends Worker {

    private static final String TAG     = "BackgroundMatchWorker";
    private static final long   TIMEOUT = 30L; // ثواني

    /**
     * [إصلاح بيانات وهمية] كان THRESHOLD = 0.65f hardcoded — مختلف عن
     * FaceEmbeddingManager.MATCH_THRESHOLD (0.70f) مما يسبب عدم اتساق.
     * الآن يُقرأ من FaceEmbeddingManager مباشرةً لضمان الاتساق.
     */
    private static float getThreshold() {
        return FaceEmbeddingManager.MATCH_THRESHOLD;
    }

    public BackgroundMatchWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "🔄 بدء المطابقة الخلفية اليومية — threshold=" + getThreshold());
        try {
            List<Map<String, String>> newReports   = fetchNewReports();
            List<Map<String, String>> foundPersons = fetchFoundPersons();

            if (newReports.isEmpty()) {
                Log.i(TAG, "لا توجد بلاغات جديدة للمقارنة.");
                return Result.success();
            }

            float threshold = getThreshold();
            int matchCount = 0;

            for (Map<String, String> report : newReports) {
                String reportEmb = report.get("faceEmbedding");
                if (reportEmb == null || reportEmb.isEmpty()) continue;
                float[] reportVec = FaceEmbeddingManager.stringToEmbedding(reportEmb);
                if (reportVec == null) continue;

                for (Map<String, String> found : foundPersons) {
                    String foundEmb = found.get("faceEmbedding");
                    if (foundEmb == null || foundEmb.isEmpty()) continue;
                    float[] foundVec = FaceEmbeddingManager.stringToEmbedding(foundEmb);
                    if (foundVec == null) continue;

                    float sim = FaceEmbeddingManager.cosineSimilarity(reportVec, foundVec);
                    if (sim >= threshold) {
                        int    percent    = (int)(sim * 100);
                        String reportId   = report.get("id");
                        String personName = report.get("personName");
                        if (personName == null) personName = "مفقود";

                        Log.d(TAG, "✅ تطابق: " + reportId + " = " + percent + "%");

                        // إشعار محلي
                        NotificationHelper.showMatchNotification(
                            getApplicationContext(),
                            personName,
                            percent,
                            reportId != null ? reportId : "");

                        // حفظ سجل التطابق في Firebase
                        saveMatchRecord(reportId, found.get("id"), sim,
                            report.get("reporterId"), found.get("reporterId"));

                        matchCount++;
                    }
                }
            }

            Log.i(TAG, "✅ انتهت المطابقة — " + matchCount + " تطابق/ات");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "doWork() error: " + e.getMessage());
            return Result.retry();
        }
    }

    // ─── Firebase Fetching ────────────────────────────────────────

    private List<Map<String, String>> fetchNewReports() throws InterruptedException {
        List<Map<String, String>> list = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        long since = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24);

        FirebaseDatabase.getInstance().getReference("reports")
            .orderByChild("timestamp")
            .startAt(since)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    for (DataSnapshot c : snap.getChildren()) {
                        String status = c.child("status").getValue(String.class);
                        if (!"approved".equals(status)) continue;
                        Map<String, String> m = new HashMap<>();
                        m.put("id",            c.getKey());
                        m.put("faceEmbedding", safeStr(c, "faceEmbedding"));
                        m.put("personName",    safeStr(c, "personName"));
                        m.put("reporterId",    safeStr(c, "reporterId"));
                        list.add(m);
                    }
                    latch.countDown();
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {
                    latch.countDown();
                }
            });

        latch.await(TIMEOUT, TimeUnit.SECONDS);
        return list;
    }

    private List<Map<String, String>> fetchFoundPersons() throws InterruptedException {
        List<Map<String, String>> list = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        FirebaseDatabase.getInstance().getReference("found_persons")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    for (DataSnapshot c : snap.getChildren()) {
                        Map<String, String> m = new HashMap<>();
                        m.put("id",            c.getKey());
                        m.put("faceEmbedding", safeStr(c, "faceEmbedding"));
                        m.put("reporterId",    safeStr(c, "reporterId"));
                        list.add(m);
                    }
                    latch.countDown();
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {
                    latch.countDown();
                }
            });

        latch.await(TIMEOUT, TimeUnit.SECONDS);
        return list;
    }

    private void saveMatchRecord(String reportId, String foundId, float sim,
                                  String reporterUid, String founderUid) {
        if (reportId == null || foundId == null) return;
        Map<String, Object> data = new HashMap<>();
        data.put("reportId",    reportId);
        data.put("foundId",     foundId);
        data.put("similarity",  sim);
        data.put("timestamp",   System.currentTimeMillis());
        data.put("reporterUid", reporterUid != null ? reporterUid : "");
        data.put("founderUid",  founderUid  != null ? founderUid  : "");
        data.put("source",      "background_worker");
        FirebaseDatabase.getInstance().getReference("matches")
            .child(reportId + "_" + foundId)
            .setValue(data);
    }

    private String safeStr(DataSnapshot snap, String key) {
        String v = snap.child(key).getValue(String.class);
        return v != null ? v : "";
    }

    // ─── Static Scheduling Helpers ────────────────────────────────

    public static void runOnce(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BackgroundMatchWorker.class)
            .setConstraints(new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build())
            .build();
        WorkManager.getInstance(context).enqueue(request);
    }

    public static void scheduleDailyMatch(Context context) {
        PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(
            BackgroundMatchWorker.class, 24, TimeUnit.HOURS)
            .setConstraints(new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build())
            .setInitialDelay(2, TimeUnit.HOURS)
            .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "daily_face_match",
            ExistingPeriodicWorkPolicy.KEEP,
            work);

        Log.i(TAG, "✅ Daily match job scheduled — threshold=" + getThreshold());
    }
}
