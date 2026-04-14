package com.missingpersons.app.activities;

import android.app.Application;
import android.location.Location;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.*;
import com.missingpersons.app.data.repository.ReportRepository;
import com.missingpersons.app.models.ReportEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * BrowseViewModel — الحالة الكاملة لصفحة التصفح
 *
 * ══════════════════════════════════════════════════════════
 * [إصلاح خطأ-01] إضافة forceSync() لإعادة الجلب من Firebase
 * عند كل onResume() في BrowseActivity لضمان مزامنة Room.
 *
 * المعمارية:
 *   • ReportRepository هو الوسيط الوحيد للبيانات
 *   • Room SSOT: LiveData من Room يُحدَّث تلقائياً
 *   • Cursor-based pagination عبر Firebase
 *   • Filter/Sort state محفوظة عبر rotation
 * ══════════════════════════════════════════════════════════
 */
public class BrowseViewModel extends AndroidViewModel {

    private static final String TAG = "BrowseViewModel";

    public static final int SORT_NEWEST  = 0;
    public static final int SORT_SMART   = 1;
    public static final int SORT_NEAREST = 2;

    // ── Repository ───────────────────────────────────────────
    private final ReportRepository repository;

    // ── Filter state ────────────────────────────────────────
    final MutableLiveData<String>  typeFilter   = new MutableLiveData<>("all");
    final MutableLiveData<String>  govFilter    = new MutableLiveData<>("all");
    final MutableLiveData<String>  searchQuery  = new MutableLiveData<>("");
    final MutableLiveData<String>  statusFilter = new MutableLiveData<>("all");
    final MutableLiveData<Integer> sortMode     = new MutableLiveData<>(SORT_NEWEST);

    // ── Sync / pagination state ──────────────────────────────
    public final MutableLiveData<Boolean> isSyncing     = new MutableLiveData<>(false);
    public final MutableLiveData<Boolean> isLoadingMore = new MutableLiveData<>(false);
    public final MutableLiveData<Boolean> hasMore       = new MutableLiveData<>(true);
    public final MutableLiveData<Boolean> isOffline     = new MutableLiveData<>(false);

    // أقدم timestamp في القائمة الحالية — يُستخدم كـ cursor للصفحة التالية
    private long currentCursor = 0L;

    // User location for geo sort
    double userLat = 0, userLng = 0;

    // ── Combined filter trigger ──────────────────────────────
    private final MediatorLiveData<FilterKey> filterKey = new MediatorLiveData<>();
    public  final LiveData<List<ReportEntity>> reports;

    public BrowseViewModel(@NonNull Application app) {
        super(app);
        repository = ReportRepository.getInstance(app);

        filterKey.addSource(typeFilter,   v -> resetAndTrigger());
        filterKey.addSource(govFilter,    v -> resetAndTrigger());
        filterKey.addSource(searchQuery,  v -> triggerUpdate());
        filterKey.addSource(statusFilter, v -> resetAndTrigger());
        triggerUpdate();

        // SwitchMap: whenever filters change → re-query Room (SSOT)
        reports = Transformations.switchMap(filterKey, fk ->
            repository.getFilteredReports(fk.type, fk.gov, fk.query, fk.status)
        );
    }

    /** فلتر تغيّر → أعد تعيين الـ cursor وابدأ من أول */
    private void resetAndTrigger() {
        currentCursor = 0L;
        hasMore.setValue(true);
        triggerUpdate();
    }

    private void triggerUpdate() {
        filterKey.setValue(new FilterKey(
            str(typeFilter,   "all"),
            str(govFilter,    "all"),
            str(searchQuery,  ""),
            str(statusFilter, "all")
        ));
    }

    // ════════════════════════════════════════════════════════
    //  [إصلاح خطأ-01] forceSync — يُستدعى من BrowseActivity.onResume()
    //  يعيد الجلب من Firebase دائماً لضمان أن Room محدّث
    // ════════════════════════════════════════════════════════

    public void forceSync() {
        if (Boolean.TRUE.equals(isSyncing.getValue())) {
            Log.d(TAG, "forceSync() skipped — already syncing");
            return;
        }
        Log.d(TAG, "🔄 forceSync() called");
        isSyncing.setValue(true);
        isOffline.setValue(false);
        repository.forceSync(() -> {
            isSyncing.setValue(false);
            Log.d(TAG, "✅ forceSync() done");
        });
    }

    // ════════════════════════════════════════════════════════
    //  Sync — Initial
    // ════════════════════════════════════════════════════════

    public void syncInitial() {
        if (Boolean.TRUE.equals(isSyncing.getValue())) return;
        isSyncing.setValue(true);
        isOffline.setValue(false);

        repository.syncInitial(
            str(typeFilter,   "all"),
            str(govFilter,    "all"),
            str(statusFilter, "all"),
            () -> isSyncing.setValue(false)
        );
    }

    // ════════════════════════════════════════════════════════
    //  Pagination — Load More (Cursor-based)
    // ════════════════════════════════════════════════════════

    public void loadMore() {
        if (Boolean.TRUE.equals(isLoadingMore.getValue())) return;
        if (!Boolean.TRUE.equals(hasMore.getValue())) return;
        if (Boolean.TRUE.equals(isSyncing.getValue())) return;

        isLoadingMore.setValue(true);

        List<ReportEntity> cur = reports.getValue();
        if (cur != null && !cur.isEmpty()) {
            long oldest = Long.MAX_VALUE;
            for (ReportEntity r : cur)
                if (r.timestamp > 0 && r.timestamp < oldest) oldest = r.timestamp;
            if (oldest < Long.MAX_VALUE) currentCursor = oldest;
        }

        repository.loadMore(
            str(typeFilter,   "all"),
            str(govFilter,    "all"),
            str(statusFilter, "all"),
            currentCursor,
            moreAvailable -> {
                isLoadingMore.setValue(false);
                hasMore.setValue(moreAvailable);
            }
        );
    }

    // ════════════════════════════════════════════════════════
    //  Filter / Sort setters
    // ════════════════════════════════════════════════════════

    public void setType(String t)   { typeFilter.setValue(t); }
    public void setGov(String g)    { govFilter.setValue(g); }
    public void setSearch(String q) { searchQuery.setValue(q); }
    public void setSort(int s)      { sortMode.setValue(s); }
    public void setStatus(String s) { statusFilter.setValue(s); }

    public void resetFilters() {
        typeFilter.setValue("all");
        govFilter.setValue("all");
        searchQuery.setValue("");
        statusFilter.setValue("all");
        sortMode.setValue(SORT_NEWEST);
    }

    public String getType()   { return str(typeFilter,   "all"); }
    public String getGov()    { return str(govFilter,    "all"); }
    public String getStatus() { return str(statusFilter, "all"); }
    public int    getSort() {
        Integer s = sortMode.getValue();
        return s != null ? s : SORT_NEWEST;
    }

    // ════════════════════════════════════════════════════════
    //  Sort
    // ════════════════════════════════════════════════════════

    public List<ReportEntity> sortedList(List<ReportEntity> raw) {
        if (raw == null) return new ArrayList<>();
        List<ReportEntity> list = new ArrayList<>(raw);
        int sort = getSort();

        if (sort == SORT_SMART) {
            long now = System.currentTimeMillis();
            Collections.sort(list, (a, b) ->
                Double.compare(smartScore(b, now), smartScore(a, now)));
        } else if (sort == SORT_NEAREST && (userLat != 0 || userLng != 0)) {
            Collections.sort(list, (a, b) -> {
                float[] da = new float[1], db2 = new float[1];
                Location.distanceBetween(userLat, userLng, a.lat, a.lng, da);
                Location.distanceBetween(userLat, userLng, b.lat, b.lng, db2);
                return Float.compare(da[0], db2[0]);
            });
        }
        return list;
    }

    private double smartScore(ReportEntity e, long now) {
        double score = 0;
        if ("emergency".equals(e.reportType)) score += 1000;
        else if ("missing".equals(e.reportType)) score += 500;
        long ageDays = (now - e.timestamp) / (1000L * 60 * 60 * 24);
        score += Math.max(0, 500 - ageDays * 10);
        if (!"resolved".equals(e.status)) score += 200;
        return score;
    }

    // ════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════

    private String str(LiveData<String> ld, String def) {
        String v = ld.getValue();
        return v != null ? v : def;
    }

    static class FilterKey {
        final String type, gov, query, status;
        FilterKey(String t, String g, String q, String s) {
            type = t; gov = g; query = q; status = s;
        }
    }
}
