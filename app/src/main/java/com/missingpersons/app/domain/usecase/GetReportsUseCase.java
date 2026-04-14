package com.missingpersons.app.domain.usecase;

import androidx.lifecycle.LiveData;

import com.missingpersons.app.data.repository.ReportRepository;
import com.missingpersons.app.models.ReportEntity;

import java.util.List;

/**
 * GetReportsUseCase — طبقة الـ Domain
 *
 * تعزل ViewModel عن تفاصيل ReportRepository.
 * تحتوي منطق الفلترة والترتيب المخصص للتطبيق.
 *
 * الاستخدام في ViewModel:
 *   GetReportsUseCase useCase = new GetReportsUseCase(repo);
 *   LiveData<List<ReportEntity>> reports = useCase.execute("missing", "القاهرة");
 */
public class GetReportsUseCase {

    private final ReportRepository repository;

    public GetReportsUseCase(ReportRepository repository) {
        this.repository = repository;
    }

    // ════════════════════════════════════════════════════════
    //  Execute — الحالة الافتراضية (كل البلاغات المعتمدة)
    // ════════════════════════════════════════════════════════

    public LiveData<List<ReportEntity>> execute() {
        return repository.getApprovedReports();
    }

    // ════════════════════════════════════════════════════════
    //  Execute — مع فلترة
    // ════════════════════════════════════════════════════════

    public LiveData<List<ReportEntity>> execute(String type, String governorate) {
        return repository.getFilteredReports(type, governorate, null, "approved");
    }

    public LiveData<List<ReportEntity>> execute(String type, String governorate,
                                                  String searchQuery, String status) {
        return repository.getFilteredReports(type, governorate, searchQuery, status);
    }

    // ════════════════════════════════════════════════════════
    //  Execute — بلاغات مستخدم معين
    // ════════════════════════════════════════════════════════

    public LiveData<List<ReportEntity>> executeForUser(String uid) {
        return repository.getReportsByUser(uid);
    }

    // ════════════════════════════════════════════════════════
    //  Search
    // ════════════════════════════════════════════════════════

    public LiveData<List<ReportEntity>> search(String query) {
        return repository.search(query);
    }

    // ════════════════════════════════════════════════════════
    //  Sync — تحميل البيانات من Firebase → Room
    // ════════════════════════════════════════════════════════

    public void sync(String type, String governorate, String status, Runnable onDone) {
        repository.syncInitial(type, governorate, status, onDone);
    }

    public void loadMore(String type, String governorate, String status,
                          long cursor, ReportRepository.OnLoadMoreCallback callback) {
        repository.loadMore(type, governorate, status, cursor, callback);
    }
}
