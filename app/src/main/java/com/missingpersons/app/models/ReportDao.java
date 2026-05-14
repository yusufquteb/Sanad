package com.missingpersons.app.models;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;

/**
 * ReportDao — واجهة استعلامات قاعدة البيانات المحلية (Room)
 *
 * مُحدَّث في Phase 1 بإضافة:
 * - getReportsByReporter()  → للـ ProfileViewModel
 * - countByType()           → للـ HomeViewModel stats
 * - countByReporter()       → للـ ProfileViewModel
 * - countApproved()         → alias لـ getApprovedCount
 */
@Dao
public interface ReportDao {

    // ─── إدراج / تحديث ────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(ReportEntity report);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ReportEntity> reports);

    // ─── قراءة (LiveData = تحديث تلقائي للـ UI) ───────────────

    /** كل البلاغات المعتمدة — الأحدث أولاً */
    @Query("SELECT * FROM reports WHERE approved = 1 ORDER BY timestamp DESC")
    LiveData<List<ReportEntity>> getApprovedReports();

    /** كل البلاغات المعتمدة (للـ Workers — بدون LiveData) */
    @Query("SELECT * FROM reports WHERE approved = 1 ORDER BY timestamp DESC")
    List<ReportEntity> getApprovedReportsSync();

    /**
     * استعلام مرن للـ Browse (LiveData — يتحدث تلقائياً)
     */
    @Query("SELECT * FROM reports " +
           "WHERE approved = 1 " +
           "AND status != 'resolved' " +
           "AND status != 'deleted' " +
           "AND (:type = 'all' OR reportType = :type) " +
           "AND (:gov = 'all' OR governorate = :gov) " +
           "AND (:status = 'all' OR status = :status) " +
           "AND (:q = '' OR personName LIKE '%' || :q || '%' " +
           "     OR reportId LIKE '%' || :q || '%' " +
           "     OR manualAddress LIKE '%' || :q || '%') " +
           "ORDER BY timestamp DESC")
    LiveData<List<ReportEntity>> getFilteredReports(String type, String gov, String q, String status);

    /**
     * Cursor-based pagination
     */
    @Query("SELECT * FROM reports " +
           "WHERE approved = 1 " +
           "AND status != 'resolved' " +
           "AND status != 'deleted' " +
           "AND (:type = 'all' OR reportType = :type) " +
           "AND (:gov = 'all' OR governorate = :gov) " +
           "AND (:status = 'all' OR status = :status) " +
           "AND (:q = '' OR personName LIKE '%' || :q || '%' " +
           "     OR reportId LIKE '%' || :q || '%' " +
           "     OR manualAddress LIKE '%' || :q || '%') " +
           "AND (:cursor = 0 OR timestamp < :cursor) " +
           "ORDER BY timestamp DESC " +
           "LIMIT :pageSize")
    List<ReportEntity> getFilteredReportsPaged(String type, String gov, String q,
                                               String status, long cursor, int pageSize);

    /** أقدم timestamp لمجموعة فلاتر */
    @Query("SELECT MIN(timestamp) FROM reports " +
           "WHERE approved = 1 " +
           "AND (:type = 'all' OR reportType = :type) " +
           "AND (:gov = 'all' OR governorate = :gov) " +
           "AND (:status = 'all' OR status = :status)")
    long getOldestTimestamp(String type, String gov, String status);

    /** بلاغات مستخدم معين (LiveData) — جديد Phase 1 */
    @Query("SELECT * FROM reports WHERE reporterId = :uid ORDER BY timestamp DESC")
    LiveData<List<ReportEntity>> getReportsByReporter(String uid);

    /** حذف كل السجلات التي لم تعد موجودة في Firebase */
    @Query("DELETE FROM reports WHERE reportId NOT IN (:activeIds)")
    void deleteNotInList(List<String> activeIds);

    /** جلب كل الـ IDs المخزنة محلياً */
    @Query("SELECT reportId FROM reports")
    List<String> getAllIds();

    /** البلاغات التي لم تُزامن بعد */
    @Query("SELECT * FROM reports WHERE synced = 0")
    List<ReportEntity> getPendingSync();

    /** بلاغ محدد */
    @Query("SELECT * FROM reports WHERE reportId = :id LIMIT 1")
    ReportEntity getById(String id);

    /** بحث بالاسم أو المحافظة */
    @Query("SELECT * FROM reports WHERE approved = 1 AND " +
           "(personName LIKE '%' || :query || '%' OR governorate LIKE '%' || :query || '%') " +
           "ORDER BY timestamp DESC")
    LiveData<List<ReportEntity>> search(String query);

    /** آخر N بلاغ (للـ Widget) */
    @Query("SELECT * FROM reports WHERE approved = 1 ORDER BY timestamp DESC LIMIT :n")
    List<ReportEntity> getLatestN(int n);

    /** آخر N بلاغ حسب النوع (missing/found/sighting) — للـ Widget */
    @Query("SELECT * FROM reports WHERE approved = 1 AND reportType = :type ORDER BY timestamp DESC LIMIT :n")
    List<ReportEntity> getLatestByType(String type, int n);

    // ─── تحديث ────────────────────────────────────────────────

    @Query("UPDATE reports SET synced = 1 WHERE reportId = :id")
    void markSynced(String id);

    @Query("UPDATE reports SET status = :status, lastUpdated = :ts WHERE reportId = :id")
    void updateStatus(String id, String status, long ts);

    @Query("UPDATE reports SET approved = 1, status = 'approved', lastUpdated = :ts WHERE reportId = :id")
    void markApproved(String id, long ts);

    // ─── حذف ──────────────────────────────────────────────────

    @Query("DELETE FROM reports WHERE reportId = :id")
    void delete(String id);

    @Query("DELETE FROM reports WHERE approved = 0 AND synced = 1 AND timestamp < :cutoff")
    void cleanOld(long cutoff);

    // ─── إحصائيات — جديد Phase 1 ──────────────────────────────

    /** عدد البلاغات المعتمدة (alias + الاسم الجديد) */
    @Query("SELECT COUNT(*) FROM reports WHERE approved = 1")
    int getApprovedCount();

    @Query("SELECT COUNT(*) FROM reports WHERE approved = 1")
    int countApproved();

    /** عدد البلاغات حسب النوع (missing / found / sighting) */
    @Query("SELECT COUNT(*) FROM reports WHERE approved = 1 AND reportType = :type")
    int countByType(String type);

    /** عدد بلاغات مستخدم معين */
    @Query("SELECT COUNT(*) FROM reports WHERE reporterId = :uid")
    int countByReporter(String uid);

    @Query("SELECT COUNT(*) FROM reports WHERE status = 'resolved'")
    int getResolvedCount();
}
