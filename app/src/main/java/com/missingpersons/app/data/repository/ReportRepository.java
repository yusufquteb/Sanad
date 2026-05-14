package com.missingpersons.app.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
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
 * ReportRepository
 *
 * [إصلاح بناء] حُذف استدعاء dao.countAll() غير الموجود في ReportDao.
 *   السطر المُصلح:  Log.d(TAG, "sync done") بدون استدعاء غير موجود.
 *
 * [إصلاح خطأ-01] syncFromFirebase يفلتر بـ status="approved" (String)
 *   بدلاً من approved=true (Boolean) — هذا هو سبب ظهور Home مليئاً
 *   وBrowse فارغاً.
 *
 * [إصلاح خطأ-01] forceSync() — تُستدعى من BrowseActivity.onResume().
 */
public class ReportRepository {

    private static final String TAG       = "ReportRepository";
    public  static final int    PAGE_SIZE = 30;

    private static final String[] NODES = {"reports", "found_persons", "sightings"};

    private final ReportDao  dao;
    private final Context    ctx;
    private final Executor   executor    = Executors.newSingleThreadExecutor();
    private final Handler    mainHandler = new Handler(Looper.getMainLooper());

    private static volatile ReportRepository instance;

    public static ReportRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (ReportRepository.class) {
                if (instance == null) instance = new ReportRepository(context);
            }
        }
        return instance;
    }

    public ReportRepository(Context context) {
        this.ctx = context.getApplicationContext();
        this.dao = AppDatabase.getInstance(ctx).reportDao();
    }

    // ════════════════════════════════════════════════════
    //  Read — LiveData (Room SSOT)
    // ════════════════════════════════════════════════════

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

    public LiveData<List<ReportEntity>> getReportsByUser(String uid) {
        return dao.getReportsByReporter(uid);
    }

    // ════════════════════════════════════════════════════
    //  [إصلاح خطأ-01] forceSync
    // ════════════════════════════════════════════════════

    public void forceSync(Runnable onDone) {
        Log.d(TAG, "🔄 forceSync() started");
        syncFromFirebase("all", "all", "all", onDone);
    }

    // ════════════════════════════════════════════════════
    //  Sync
    // ════════════════════════════════════════════════════

    public void syncInitial(String type, String gov, String status, Runnable onDone) {
        Log.d(TAG, "syncInitial() type=" + type + " status=" + status);
        syncFromFirebase(type, gov, status, onDone);
    }

    /**
     * [إصلاح خطأ-01] فلتر بـ status="approved" (String)
     * لأن ReportActivity تحفظ: report.put("status","approved")
     * وليس: report.put("approved", true)
     */
    private void syncFromFirebase(String type, String gov, String status, Runnable onDone) {
        AtomicInteger pending   = new AtomicInteger(NODES.length);
        Set<String>   activeIds = Collections.synchronizedSet(new HashSet<>());

        // [إصلاح] مزامنة بلاغات المستخدم الحالي (pending + approved) لتظهر فوراً
        String myUid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
            ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (myUid != null && !myUid.isEmpty()) {
            FirebaseDatabase.getInstance().getReference("reports")
                .orderByChild("reporterId").equalTo(myUid).limitToLast(20)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snap) {
                        List<ReportEntity> mine = new ArrayList<>();
                        for (DataSnapshot c : snap.getChildren()) {
                            ReportEntity e = fromSnapshot(c, "reports");
                            if (e != null) { e.approved = true; mine.add(e); }
                        }
                        if (!mine.isEmpty())
                            executor.execute(() -> dao.insertAll(mine));
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
        }

        for (String node : NODES) {
            Query query = FirebaseDatabase.getInstance()
                    .getReference(node)
                    .orderByChild("status")
                    .equalTo("approved")
                    .limitToLast(PAGE_SIZE);

            final String finalNode = node;
            query.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snap) {
                    Log.d(TAG, "📥 node=" + finalNode + " count=" + snap.getChildrenCount());
                    List<ReportEntity> batch = new ArrayList<>();
                    for (DataSnapshot c : snap.getChildren()) {
                        if (c.getKey() != null) activeIds.add(c.getKey());
                        ReportEntity e = fromSnapshot(c, finalNode);
                        if (e != null) batch.add(e);
                    }
                    Log.d(TAG, "✅ parsed=" + batch.size() + " from node=" + finalNode);
                    executor.execute(() -> {
                        if (!batch.isEmpty()) dao.insertAll(batch);
                        finishNode(pending, onDone);
                    });
                }

                @Override
                public void onCancelled(@NonNull DatabaseError e) {
                    Log.w(TAG, "syncFromFirebase cancelled [" + finalNode + "]: " + e.getMessage());
                    finishNode(pending, onDone);
                }
            });
        }
    }

    // ════════════════════════════════════════════════════
    //  Pagination
    // ════════════════════════════════════════════════════

    public void loadMore(String type, String gov, String status,
                         long cursor, OnLoadMoreCallback onDone) {
        AtomicInteger pending  = new AtomicInteger(NODES.length);
        AtomicInteger totalNew = new AtomicInteger(0);

        for (String node : NODES) {
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference(node);
            Query q = ref.orderByChild("timestamp");
            if (cursor > 0) q = q.endAt(cursor - 1);
            q = q.limitToLast(PAGE_SIZE);

            final String finalNode = node;
            q.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snap) {
                    List<ReportEntity> batch = new ArrayList<>();
                    for (DataSnapshot c : snap.getChildren()) {
                        if (!isApproved(c)) continue;
                        ReportEntity e = fromSnapshot(c, finalNode);
                        if (e != null) batch.add(e);
                    }
                    executor.execute(() -> {
                        if (!batch.isEmpty()) {
                            dao.insertAll(batch);
                            totalNew.addAndGet(batch.size());
                        }
                        if (pending.decrementAndGet() == 0) {
                            boolean hasMore = totalNew.get() >= PAGE_SIZE;
                            mainHandler.post(() -> { if (onDone != null) onDone.onDone(hasMore); });
                        }
                    });
                }

                @Override
                public void onCancelled(@NonNull DatabaseError e) {
                    Log.w(TAG, "loadMore cancelled: " + e.getMessage());
                    if (pending.decrementAndGet() == 0)
                        mainHandler.post(() -> { if (onDone != null) onDone.onDone(false); });
                }
            });
        }
    }

    // ════════════════════════════════════════════════════
    //  Write
    // ════════════════════════════════════════════════════

    public void saveReportOfflineFirst(ReportEntity entity, OnSaveCallback callback) {
        entity.synced      = false;
        entity.lastUpdated = System.currentTimeMillis();
        executor.execute(() -> {
            dao.insertOrUpdate(entity);
            uploadToFirebase(entity, callback);
        });
    }

    private void uploadToFirebase(ReportEntity entity, OnSaveCallback callback) {
        Map<String, Object> data = entityToMap(entity);
        FirebaseDatabase.getInstance()
                .getReference("reports").child(entity.reportId)
                .setValue(data)
                .addOnSuccessListener(v -> {
                    executor.execute(() -> dao.markSynced(entity.reportId));
                    mainHandler.post(() -> { if (callback != null) callback.onSuccess(entity.reportId); });
                })
                .addOnFailureListener(e -> mainHandler.post(() -> {
                    if (callback != null) callback.onSavedLocally(entity.reportId);
                }));
    }

    // ════════════════════════════════════════════════════
    //  Statistics
    // ════════════════════════════════════════════════════

    public void getStats(OnStatsCallback callback) {
        executor.execute(() -> {
            int total   = dao.countApproved();
            int missing = dao.countByType("missing");
            int found   = dao.countByType("found");
            mainHandler.post(() -> { if (callback != null) callback.onStats(total, missing, found); });
        });
    }

    public void getReportCountByUser(String uid, OnCountCallback callback) {
        executor.execute(() -> {
            int count = dao.countByReporter(uid);
            mainHandler.post(() -> { if (callback != null) callback.onCount(count); });
        });
    }

    // ════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════

    /**
     * [إصلاح بناء] حُذف dao.countAll() — الدالة غير موجودة في ReportDao.
     * تم الاستغناء عنها بـ Log بسيط.
     */
    private void finishNode(AtomicInteger pending, Runnable onDone) {
        if (pending.decrementAndGet() == 0) {
            Log.d(TAG, "🏁 sync done — all nodes finished");
            mainHandler.post(() -> { if (onDone != null) onDone.run(); });
        }
    }

    /**
     * [إصلاح خطأ-01] يقبل approved بطريقتين:
     *   1. status == "approved"  (الأحدث)
     *   2. approved == true      (التوافق مع البيانات القديمة)
     */
    private boolean isApproved(DataSnapshot c) {
        String status = c.child("status").getValue(String.class);
        if ("approved".equals(status)) return true;
        Boolean b = c.child("approved").getValue(Boolean.class);
        return Boolean.TRUE.equals(b);
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

            e.approved    = isApproved(c);
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

    // ════════════════════════════════════════════════════
    //  Callbacks
    // ════════════════════════════════════════════════════

    public interface OnSaveCallback {
        void onSuccess(String reportId);
        void onSavedLocally(String reportId);
    }

    public interface OnLoadMoreCallback {
        void onDone(boolean hasMore);
    }

    public interface OnStatsCallback {
        void onStats(int total, int missing, int found);
    }

    public interface OnCountCallback {
        void onCount(int count);
    }
}
