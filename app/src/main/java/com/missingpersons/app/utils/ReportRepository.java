package com.missingpersons.app.utils;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import com.google.firebase.database.*;
import com.missingpersons.app.models.AppDatabase;
import com.missingpersons.app.models.ReportDao;
import com.missingpersons.app.models.ReportEntity;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ReportRepository — Room as Single Source of Truth
 *
 * ═══════════════════════════════════════════════════════════
 * المعمارية:
 *   1. Room هو المصدر الوحيد للحقيقة (SSOT)
 *   2. LiveData من Room → UI يتحدث تلقائياً
 *   3. Firebase sync في الخلفية → يكتب في Room فقط
 *   4. Cursor-based pagination: كل صفحة = PAGE_SIZE بلاغ
 *      cursor = timestamp أقدم عنصر في الصفحة السابقة
 *
 * الاستخدام:
 *   repo.getFilteredReports(type, gov, q, status)  // LiveData للعرض
 *   repo.syncInitial(filters, onDone)              // أول تحميل
 *   repo.loadMore(filters, cursor, onDone)         // تحميل المزيد
 * ═══════════════════════════════════════════════════════════
 */
public class ReportRepository {

    private static final String TAG       = "ReportRepository";
    public  static final int    PAGE_SIZE = 30;

    // العقد في Firebase التي تحمل البلاغات
    private static final String[] NODES = {"reports", "found_persons", "sightings"};

    private final ReportDao dao;
    private final Context   ctx;
    private final Executor  executor = Executors.newSingleThreadExecutor();

    public ReportRepository(Context context) {
        this.ctx = context.getApplicationContext();
        this.dao = AppDatabase.getInstance(ctx).reportDao();
    }

    // ════════════════════════════════════════════════════════
    //  Read — LiveData (Room SSOT)
    // ════════════════════════════════════════════════════════

    public LiveData<List<ReportEntity>> getApprovedReports() {
        return dao.getApprovedReports();
    }

    public LiveData<List<ReportEntity>> getFilteredReports(
            String type, String gov, String q, String status) {
        return dao.getFilteredReports(
                type   != null ? type   : "all",
                gov    != null ? gov    : "all",
                q      != null ? q      : "",
                status != null ? status : "all");
    }

    public LiveData<List<ReportEntity>> search(String query) {
        return dao.search(query != null ? query : "");
    }

    // ════════════════════════════════════════════════════════
    //  Sync — Initial (first load, fetches newest PAGE_SIZE)
    // ════════════════════════════════════════════════════════

    /**
     * يجلب أحدث PAGE_SIZE بلاغ من كل عقدة Firebase ويخزنها في Room.
     * النتيجة: LiveData يتحدث تلقائياً.
     * @param onDone يُستدعى في Main thread عند الانتهاء
     */
    public void syncInitial(String type, String gov, String status, Runnable onDone) {
        AtomicInteger pending = new AtomicInteger(NODES.length);
        Set<String> activeIds = Collections.synchronizedSet(new HashSet<>());

        for (String node : NODES) {
            String fbType = nodeFbType(node, type);
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference(node);
            Query query = (fbType != null)
                ? ref.orderByChild("reportType").equalTo(fbType)
                : ref.orderByChild("approved").equalTo(true);

            query.limitToLast(PAGE_SIZE)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snap) {
                        List<ReportEntity> batch = new ArrayList<>();
                        for (DataSnapshot c : snap.getChildren()) {
                            Boolean ok = c.child("approved").getValue(Boolean.class);
                            if (!Boolean.TRUE.equals(ok)) continue;
                            if (c.getKey() != null) activeIds.add(c.getKey());
                            ReportEntity e = fromSnapshot(c, node);
                            if (e != null) batch.add(e);
                        }
                        executor.execute(() -> {
                            if (!batch.isEmpty()) dao.insertAll(batch);
                            finishNode(pending, activeIds, onDone);
                        });
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError e) {
                        Log.w(TAG, "syncInitial cancelled on " + node + ": " + e.getMessage());
                        finishNode(pending, activeIds, onDone);
                    }
                });
        }
    }

    // ════════════════════════════════════════════════════════
    //  Pagination — Cursor-based (Firebase endAt cursor)
    // ════════════════════════════════════════════════════════

    /**
     * يجلب صفحة أقدم من cursor (timestamp) لكل عقدة Firebase.
     * البيانات تُكتب في Room → LiveData يتحدث تلقائياً.
     * @param cursor  timestamp أقدم عنصر في الصفحة الحالية (0 = ابدأ من الأحدث)
     * @param onDone  يُستدعى في Main thread: true = توجد صفحة قادمة، false = وصلنا للنهاية
     */
    public void loadMore(String type, String gov, String status,
                         long cursor, OnLoadMoreCallback onDone) {
        AtomicInteger pending   = new AtomicInteger(NODES.length);
        AtomicInteger totalNew  = new AtomicInteger(0);

        for (String node : NODES) {
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference(node);
            Query q = ref.orderByChild("timestamp");
            if (cursor > 0) q = q.endAt(cursor - 1);
            q = q.limitToLast(PAGE_SIZE);

            q.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snap) {
                    List<ReportEntity> batch = new ArrayList<>();
                    for (DataSnapshot c : snap.getChildren()) {
                        Boolean ok = c.child("approved").getValue(Boolean.class);
                        if (!Boolean.TRUE.equals(ok)) continue;
                        ReportEntity e = fromSnapshot(c, node);
                        if (e != null) batch.add(e);
                    }
                    executor.execute(() -> {
                        if (!batch.isEmpty()) {
                            dao.insertAll(batch);
                            totalNew.addAndGet(batch.size());
                        }
                        if (pending.decrementAndGet() == 0) {
                            boolean hasMore = totalNew.get() >= PAGE_SIZE;
                            android.os.Handler main = new android.os.Handler(
                                    android.os.Looper.getMainLooper());
                            main.post(() -> { if (onDone != null) onDone.onDone(hasMore); });
                        }
                    });
                }
                @Override
                public void onCancelled(@NonNull DatabaseError e) {
                    Log.w(TAG, "loadMore cancelled: " + e.getMessage());
                    if (pending.decrementAndGet() == 0) {
                        android.os.Handler main = new android.os.Handler(
                                android.os.Looper.getMainLooper());
                        main.post(() -> { if (onDone != null) onDone.onDone(false); });
                    }
                }
            });
        }
    }

    // ════════════════════════════════════════════════════════
    //  Write — Offline First
    // ════════════════════════════════════════════════════════

    public void saveReportOfflineFirst(ReportEntity entity, OnSaveCallback callback) {
        entity.synced      = false;
        entity.lastUpdated = System.currentTimeMillis();
        executor.execute(() -> {
            dao.insertOrUpdate(entity);
            Log.i(TAG, "Saved locally: " + entity.reportId);
            uploadToFirebase(entity, callback);
        });
    }

    private void uploadToFirebase(ReportEntity entity, OnSaveCallback callback) {
        Map<String, Object> data = entityToMap(entity);
        FirebaseDatabase.getInstance()
            .getReference("reports")
            .child(entity.reportId)
            .setValue(data)
            .addOnSuccessListener(v -> {
                executor.execute(() -> dao.markSynced(entity.reportId));
                if (callback != null) callback.onSuccess(entity.reportId);
            })
            .addOnFailureListener(e -> {
                Log.w(TAG, "Firebase upload failed (saved locally): " + e.getMessage());
                if (callback != null) callback.onSavedLocally(entity.reportId);
            });
    }

    // ════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════

    private void finishNode(AtomicInteger pending, Set<String> activeIds, Runnable onDone) {
        if (pending.decrementAndGet() == 0) {
            // حذف المحلي الذي اختفى من Firebase
            if (!activeIds.isEmpty()) {
                dao.deleteNotInList(new ArrayList<>(activeIds));
            }
            if (onDone != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(onDone);
            }
        }
    }

    /** إذا كان الفلتر محدداً لعقدة بعينها → استخدم الـ equalTo مختلف */
    private String nodeFbType(String node, String type) {
        if ("all".equals(type) || type == null) return null;
        // found_persons لا تحمل reportType في Firebase، نستخدم approved
        if ("found_persons".equals(node) || "sightings".equals(node)) return null;
        return type; // reports node supports reportType field
    }

    public ReportEntity fromSnapshot(DataSnapshot c, String node) {
        try {
            ReportEntity e = new ReportEntity();
            e.reportId      = c.getKey() != null ? c.getKey() : "";
            e.personName    = safeStr(c, "personName");
            e.description   = safeStr(c, "description");
            e.personAge     = safeStr(c, "personAge");
            e.personGender  = safeStr(c, "personGender");
            e.governorate   = safeStr(c, "governorate");
            e.manualAddress = safeStr(c, "manualAddress");
            e.imageUrl      = safeStr(c, "imageUrl");
            e.status        = safeStr(c, "status");
            e.reporterId    = safeStr(c, "reporterId");
            e.faceEmbedding = safeStr(c, "faceEmbedding");

            String rt = safeStr(c, "reportType");
            if (rt.isEmpty()) {
                switch (node) {
                    case "found_persons": rt = "found";    break;
                    case "sightings":     rt = "sighting"; break;
                    default:              rt = "missing";  break;
                }
            }
            e.reportType = rt;

            Double lat = c.child("lat").getValue(Double.class);
            Double lng = c.child("lng").getValue(Double.class);
            e.lat = lat != null ? lat : 0;
            e.lng = lng != null ? lng : 0;

            Long ts = c.child("timestamp").getValue(Long.class);
            e.timestamp = ts != null ? ts : 0;

            Boolean approved = c.child("approved").getValue(Boolean.class);
            e.approved = Boolean.TRUE.equals(approved);

            e.synced      = true;
            e.lastUpdated = System.currentTimeMillis();
            return e;
        } catch (Exception ex) {
            Log.e(TAG, "fromSnapshot error: " + ex.getMessage());
            return null;
        }
    }

    private Map<String, Object> entityToMap(ReportEntity e) {
        Map<String, Object> m = new HashMap<>();
        m.put("reportId",      e.reportId);
        m.put("personName",    e.personName    != null ? e.personName    : "مجهول");
        m.put("description",   e.description   != null ? e.description   : "");
        m.put("personAge",     e.personAge     != null ? e.personAge     : "");
        m.put("governorate",   e.governorate   != null ? e.governorate   : "");
        m.put("manualAddress", e.manualAddress != null ? e.manualAddress : "");
        m.put("lat",           e.lat);
        m.put("lng",           e.lng);
        m.put("imageUrl",      e.imageUrl      != null ? e.imageUrl      : "");
        m.put("reportType",    e.reportType    != null ? e.reportType    : "missing");
        m.put("status",        e.status        != null ? e.status        : "pending");
        m.put("approved",      false);
        m.put("reporterId",    e.reporterId    != null ? e.reporterId    : "");
        m.put("faceEmbedding", e.faceEmbedding != null ? e.faceEmbedding : "");
        m.put("timestamp",     e.timestamp > 0 ? e.timestamp : System.currentTimeMillis());
        return m;
    }

    private String safeStr(DataSnapshot s, String key) {
        Object v = s.child(key).getValue();
        return v != null ? v.toString() : "";
    }

    // ════════════════════════════════════════════════════════
    //  Callbacks
    // ════════════════════════════════════════════════════════

    public interface OnSaveCallback {
        void onSuccess(String reportId);
        void onSavedLocally(String reportId);
    }

    public interface OnLoadMoreCallback {
        /** @param hasMore true إذا كانت هناك صفحات قادمة */
        void onDone(boolean hasMore);
    }
}
