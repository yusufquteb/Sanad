package com.missingpersons.app.domain.model;

/**
 * Person — نموذج موحد لطبقة الـ Domain
 *
 * يُستخدم في:
 * - MatchingEngine (Phase 2)
 * - GetReportsUseCase
 * - SubmitReportUseCase
 *
 * مستقل تماماً عن Firebase أو Room — طبقة نظيفة.
 */
public class Person {

    public String id;
    public String personName;
    public int    personAge;
    public String description;
    public String governorate;
    public String reportType;   // "missing" | "found" | "sighting"
    public String status;       // "pending" | "approved" | "closed"
    public String imageUrl;
    public double lat;
    public double lng;
    public long   timestamp;
    public String reporterId;
    public String faceEmbedding;

    public Person() {}

    public Person(
            String id,
            String personName,
            int personAge,
            String description,
            String governorate,
            String reportType,
            String status,
            String imageUrl,
            double lat,
            double lng,
            long timestamp,
            String reporterId) {
        this.id           = id;
        this.personName   = personName;
        this.personAge    = personAge;
        this.description  = description;
        this.governorate  = governorate;
        this.reportType   = reportType;
        this.status       = status;
        this.imageUrl     = imageUrl;
        this.lat          = lat;
        this.lng          = lng;
        this.timestamp    = timestamp;
        this.reporterId   = reporterId;
    }

    /** هل الشخص مفقود؟ */
    public boolean isMissing() {
        return "missing".equals(reportType);
    }

    /** هل الشخص تم العثور عليه؟ */
    public boolean isFound() {
        return "found".equals(reportType);
    }

    /** هل البلاغ معتمد؟ */
    public boolean isApproved() {
        return "approved".equals(status);
    }

    /** هل البلاغ مغلق (تم حل القضية)؟ */
    public boolean isClosed() {
        return "closed".equals(status);
    }

    /** هل يملك إحداثيات صالحة؟ */
    public boolean hasLocation() {
        return lat != 0 && lng != 0;
    }
}
