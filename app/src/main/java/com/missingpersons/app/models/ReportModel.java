package com.missingpersons.app.models;

import java.util.List;

/**
 * ReportModel — نموذج البلاغ الكامل (v2 — استمارة تحقيقية)
 *
 * الحقول الجديدة مُضافة بتوافق كامل مع الحقول القديمة.
 * Firebase يتجاهل الحقول الفارغة تلقائياً.
 */
public class ReportModel {

    // ═══════════════════════════════════════════
    // الحقول الأساسية
    // ═══════════════════════════════════════════
    private String reportId;
    private String reportType;      // missing / found / sighting / emergency / homeless
    private String status;          // pending / approved / deleted
    private long   timestamp;

    // ═══════════════════════════════════════════
    // الخطوة 2 — البيانات الشخصية
    // ═══════════════════════════════════════════
    private String personName;
    private int    personAge;
    private String gender;          // male / female
    private long   incidentDate;    // تاريخ الواقعة (timestamp)
    private String educationLevel;  // حضانة / ابتدائي / إعدادي / ثانوي / جامعي / خريج

    // ═══════════════════════════════════════════
    // الخطوة 3 — الموقع والتفاصيل التحقيقية
    // ═══════════════════════════════════════════
    private String governorate;     // المحافظة
    private String cityDistrict;    // المدينة / المركز
    private String subDistrict;     // الحي / القرية
    private String landmark;        // علامة مميزة (أمام مسجد / كوبري...)
    private String manualAddress;   // عنوان يدوي كامل (legacy + حقل إضافي)
    private String transportLine;   // آخر وسيلة مواصلات (للمفقودين)
    private String clothesDescription; // وصف الملابس وقت الفقد

    // العلامات الجسمانية
    private List<String> physicalMarkers;  // قائمة: وحمة، جرح، إعاقة...
    private String physicalMarkersNote;    // وصف نصي إضافي

    // الحالة الذهنية
    private String mentalState;     // طبيعي / زهايمر / اضطراب نفسي / فقدان ذاكرة...

    // الإحداثيات
    private double latitude;
    private double longitude;
    private String locationText;    // نص الموقع المُجمَّع

    // ═══════════════════════════════════════════
    // الخطوة 4 — الوسائط والتواصل
    // ═══════════════════════════════════════════
    private String       imageUrl;       // URL أول صورة (توافق قديم)
    private List<String> imageUrls;      // قائمة صور متعددة
    private String       imageHash;      // بصمة الصورة لمنع التكرار
    private String       faceEmbedding;  // بصمة الوجه

    private String  phone;           // رقم التواصل
    private boolean phonePublic;     // إظهار الرقم للعامة
    private String  reporterRelation;// صلة القرابة (والد / فاعل خير / طاقم مستشفى...)
    private boolean chatEnabled;     // السماح بالشات

    private long   foundDate;        // تاريخ العثور (لـ found / emergency / homeless)

    // معلومات إضافية
    private String relativesInfo;    // أسماء أقارب / جيران
    private String description;      // وصف عام (ملابس، ذكريات، معلومات أخرى)

    // ═══════════════════════════════════════════
    // بيانات المُبلِّغ
    // ═══════════════════════════════════════════
    private String reporterId;
    private String reporterName;
    private String reporterEmail;
    private String whatsapp;
    private String reporterDevice;

    // ═══════════════════════════════════════════
    // بيانات الإدارة
    // ═══════════════════════════════════════════
    private boolean approved;
    private long    approvedAt;
    private String  approvedBy;
    private String  editedBy;
    private long    editedAt;
    private boolean banned;

    // ═══════════════════════════════════════════
    // Constructor مطلوب لـ Firebase
    // ═══════════════════════════════════════════
    public ReportModel() {}

    // ═══════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════
    public String  getReportId()           { return reportId; }
    public String  getReportType()         { return reportType; }
    public String  getStatus()             { return status; }
    public long    getTimestamp()          { return timestamp; }

    // الخطوة 2
    public String  getPersonName()         { return personName; }
    public int     getPersonAge()          { return personAge; }
    public String  getGender()             { return gender; }
    public long    getIncidentDate()       { return incidentDate; }
    public String  getEducationLevel()     { return educationLevel; }

    // الخطوة 3
    public String  getGovernorate()        { return governorate; }
    public String  getCityDistrict()       { return cityDistrict; }
    public String  getSubDistrict()        { return subDistrict; }
    public String  getLandmark()           { return landmark; }
    public String  getManualAddress()      { return manualAddress; }
    public String  getTransportLine()      { return transportLine; }
    public String  getClothesDescription() { return clothesDescription; }
    public List<String> getPhysicalMarkers()   { return physicalMarkers; }
    public String  getPhysicalMarkersNote()    { return physicalMarkersNote; }
    public String  getMentalState()        { return mentalState; }
    public double  getLatitude()           { return latitude; }
    public double  getLongitude()          { return longitude; }
    public String  getLocationText()       { return locationText; }

    // الخطوة 4
    public String  getImageUrl()           { return imageUrl; }
    public List<String> getImageUrls()     { return imageUrls; }
    public String  getImageHash()          { return imageHash; }
    public String  getFaceEmbedding()      { return faceEmbedding; }
    public String  getPhone()              { return phone; }
    public boolean isPhonePublic()         { return phonePublic; }
    public String  getReporterRelation()   { return reporterRelation; }
    public boolean isChatEnabled()         { return chatEnabled; }
    public long    getFoundDate()          { return foundDate; }
    public String  getRelativesInfo()      { return relativesInfo; }
    public String  getDescription()        { return description; }

    // بيانات المُبلِّغ
    public String  getReporterId()         { return reporterId; }
    public String  getReporterName()       { return reporterName; }
    public String  getReporterEmail()      { return reporterEmail; }
    public String  getWhatsapp()           { return whatsapp; }
    public String  getReporterDevice()     { return reporterDevice; }

    // إدارة
    public boolean isApproved()            { return approved; }
    public long    getApprovedAt()         { return approvedAt; }
    public String  getApprovedBy()         { return approvedBy; }
    public String  getEditedBy()           { return editedBy; }
    public long    getEditedAt()           { return editedAt; }
    public boolean isBanned()              { return banned; }

    // ═══════════════════════════════════════════
    // SETTERS
    // ═══════════════════════════════════════════
    public void setReportId(String v)            { this.reportId = v; }
    public void setReportType(String v)          { this.reportType = v; }
    public void setStatus(String v)              { this.status = v; }
    public void setTimestamp(long v)             { this.timestamp = v; }

    public void setPersonName(String v)          { this.personName = v; }
    public void setPersonAge(int v)              { this.personAge = v; }
    public void setGender(String v)              { this.gender = v; }
    public void setIncidentDate(long v)          { this.incidentDate = v; }
    public void setEducationLevel(String v)      { this.educationLevel = v; }

    public void setGovernorate(String v)         { this.governorate = v; }
    public void setCityDistrict(String v)        { this.cityDistrict = v; }
    public void setSubDistrict(String v)         { this.subDistrict = v; }
    public void setLandmark(String v)            { this.landmark = v; }
    public void setManualAddress(String v)       { this.manualAddress = v; }
    public void setTransportLine(String v)       { this.transportLine = v; }
    public void setClothesDescription(String v)  { this.clothesDescription = v; }
    public void setPhysicalMarkers(List<String> v)   { this.physicalMarkers = v; }
    public void setPhysicalMarkersNote(String v)     { this.physicalMarkersNote = v; }
    public void setMentalState(String v)         { this.mentalState = v; }
    public void setLatitude(double v)            { this.latitude = v; }
    public void setLongitude(double v)           { this.longitude = v; }
    public void setLocationText(String v)        { this.locationText = v; }

    public void setImageUrl(String v)            { this.imageUrl = v; }
    public void setImageUrls(List<String> v)     { this.imageUrls = v; }
    public void setImageHash(String v)           { this.imageHash = v; }
    public void setFaceEmbedding(String v)       { this.faceEmbedding = v; }
    public void setPhone(String v)               { this.phone = v; }
    public void setPhonePublic(boolean v)        { this.phonePublic = v; }
    public void setReporterRelation(String v)    { this.reporterRelation = v; }
    public void setChatEnabled(boolean v)        { this.chatEnabled = v; }
    public void setFoundDate(long v)             { this.foundDate = v; }
    public void setRelativesInfo(String v)       { this.relativesInfo = v; }
    public void setDescription(String v)         { this.description = v; }

    public void setReporterId(String v)          { this.reporterId = v; }
    public void setReporterName(String v)        { this.reporterName = v; }
    public void setReporterEmail(String v)       { this.reporterEmail = v; }
    public void setWhatsapp(String v)            { this.whatsapp = v; }
    public void setReporterDevice(String v)      { this.reporterDevice = v; }

    public void setApproved(boolean v)           { this.approved = v; }
    public void setApprovedAt(long v)            { this.approvedAt = v; }
    public void setApprovedBy(String v)          { this.approvedBy = v; }
    public void setEditedBy(String v)            { this.editedBy = v; }
    public void setEditedAt(long v)              { this.editedAt = v; }
    public void setBanned(boolean v)             { this.banned = v; }

    // ═══════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════

    /** أول URL صورة متاح — يدعم الحقل القديم والجديد */
    public String getFirstImageUrl() {
        if (imageUrls != null && !imageUrls.isEmpty()) return imageUrls.get(0);
        return imageUrl != null ? imageUrl : "";
    }

    /** عنوان كامل مُجمَّع من المحافظة والمدينة والحي */
    public String buildFullAddress() {
        StringBuilder sb = new StringBuilder();
        if (governorate != null && !governorate.isEmpty()) sb.append(governorate);
        if (cityDistrict != null && !cityDistrict.isEmpty()) {
            if (sb.length() > 0) sb.append(" — ");
            sb.append(cityDistrict);
        }
        if (subDistrict != null && !subDistrict.isEmpty()) {
            if (sb.length() > 0) sb.append(" — ");
            sb.append(subDistrict);
        }
        if (landmark != null && !landmark.isEmpty()) {
            if (sb.length() > 0) sb.append(" (");
            sb.append(landmark).append(")");
        }
        return sb.toString();
    }

    public boolean isPending()        { return "pending".equals(status); }
    public boolean isApprovedStatus() { return "approved".equals(status); }
    public boolean isMissing()        { return "missing".equals(reportType); }
    public boolean isFound()          { return "found".equals(reportType); }
    public boolean isSighting()       { return "sighting".equals(reportType); }
    public boolean isEmergency()      { return "emergency".equals(reportType); }
    public boolean isHomeless()       { return "homeless".equals(reportType); }
}
