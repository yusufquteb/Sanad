package com.missingpersons.app.domain.usecase;

import com.missingpersons.app.data.repository.ReportRepository;
import com.missingpersons.app.models.ReportEntity;

import java.util.UUID;

/**
 * SubmitReportUseCase — طبقة الـ Domain
 *
 * مسؤولة عن:
 * - التحقق من صحة البيانات (Validation)
 * - تحضير الـ ReportEntity (تعيين ID، timestamp، إلخ)
 * - تفويض الحفظ لـ ReportRepository
 */
public class SubmitReportUseCase {

    private final ReportRepository repository;

    public SubmitReportUseCase(ReportRepository repository) {
        this.repository = repository;
    }

    // ════════════════════════════════════════════════════════
    //  Execute — إرسال بلاغ جديد
    // ════════════════════════════════════════════════════════

    public void execute(ReportInput input, ReportRepository.OnSaveCallback callback) {

        // 1. Validation
        ValidationResult validation = validate(input);
        if (!validation.isValid) {
            callback.onSavedLocally(validation.errorMessage);
            return;
        }

        // 2. تحضير الـ Entity
        ReportEntity entity = buildEntity(input);

        // 3. حفظ (Offline First)
        repository.saveReportOfflineFirst(entity, callback);
    }

    // ════════════════════════════════════════════════════════
    //  Validation
    // ════════════════════════════════════════════════════════

    private ValidationResult validate(ReportInput input) {
        if (input.personName == null || input.personName.trim().isEmpty()) {
            return ValidationResult.error("اسم الشخص مطلوب");
        }
        if (input.personName.trim().length() < 2) {
            return ValidationResult.error("الاسم يجب أن يكون حرفين على الأقل");
        }
        if (input.reporterId == null || input.reporterId.isEmpty()) {
            return ValidationResult.error("يجب تسجيل الدخول أولاً");
        }
        if (input.reportType == null || input.reportType.isEmpty()) {
            return ValidationResult.error("نوع البلاغ مطلوب");
        }
        if (input.governorate == null || input.governorate.isEmpty()) {
            return ValidationResult.error("المحافظة مطلوبة");
        }
        return ValidationResult.ok();
    }

    // ════════════════════════════════════════════════════════
    //  Build Entity
    // ════════════════════════════════════════════════════════

    private ReportEntity buildEntity(ReportInput input) {
        ReportEntity e = new ReportEntity();
        e.reportId      = input.reportId != null && !input.reportId.isEmpty()
                          ? input.reportId
                          : UUID.randomUUID().toString();
        e.personName    = normalizeName(input.personName);
        e.description   = input.description   != null ? input.description   : "";
        e.personAge     = input.personAge      != null ? input.personAge     : "";
        e.personGender  = input.personGender   != null ? input.personGender  : "";
        e.governorate   = input.governorate    != null ? input.governorate   : "";
        e.manualAddress = input.manualAddress  != null ? input.manualAddress : "";
        e.imageUrl      = input.imageUrl       != null ? input.imageUrl      : "";
        e.faceEmbedding = input.faceEmbedding  != null ? input.faceEmbedding : "";
        e.reportType    = input.reportType;
        e.status        = "pending";
        e.approved      = false;
        e.reporterId    = input.reporterId;
        e.lat           = input.lat;
        e.lng           = input.lng;
        e.timestamp     = System.currentTimeMillis();
        e.synced        = false;
        e.lastUpdated   = System.currentTimeMillis();
        return e;
    }

    /**
     * تطبيع الاسم قبل الحفظ — مهم جداً للـ Matching System (Phase 2)
     * يحول: "أحمد" → "احمد"، "ة" → "ه"، مسافات زائدة محذوفة
     */
    private String normalizeName(String name) {
        if (name == null) return "";
        return name.trim()
                   .replaceAll("[أإآ]", "ا")
                   .replaceAll("ة", "ه")
                   .replaceAll("\\s+", " ");
    }

    // ════════════════════════════════════════════════════════
    //  Input / Output Models
    // ════════════════════════════════════════════════════════

    public static class ReportInput {
        public String reportId;
        public String personName;
        public String personAge;
        public String personGender;
        public String description;
        public String governorate;
        public String manualAddress;
        public String imageUrl;
        public String faceEmbedding;
        public String reportType;   // "missing" | "found" | "sighting"
        public String reporterId;
        public double lat;
        public double lng;
    }

    private static class ValidationResult {
        final boolean isValid;
        final String  errorMessage;

        ValidationResult(boolean isValid, String errorMessage) {
            this.isValid      = isValid;
            this.errorMessage = errorMessage;
        }

        static ValidationResult ok()          { return new ValidationResult(true, null); }
        static ValidationResult error(String msg) { return new ValidationResult(false, msg); }
    }
}
