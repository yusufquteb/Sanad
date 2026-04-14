package com.missingpersons.app.models;

import androidx.annotation.NonNull;
import androidx.room.*;

/**
 * ReportEntity — جدول البلاغات في Room DB
 *
 * يُخزّن البلاغات محلياً للقراءة الفورية بدون انتظار Firebase.
 * المزامنة مع Firebase تحدث في الخلفية عبر OfflineSyncManager.
 */
@Entity(tableName = "reports")
public class ReportEntity {

    @PrimaryKey @NonNull
    public String reportId = "";

    public String  personName;
    public String  description;
    public String  personAge;
    public String  personGender;
    public String  governorate;
    public String  manualAddress;
    public double  lat;
    public double  lng;
    public String  imageUrl;      // أول صورة فقط للعرض
    public String  reportType;    // missing / found / sighting
    public String  status;        // pending / approved / resolved / deleted
    public boolean approved;
    public String  reporterId;
    public long    timestamp;
    public String  faceEmbedding;
    public boolean synced;        // تم رفعه لـ Firebase؟
    public long    lastUpdated;   // وقت آخر تحديث محلي

    // constructor فارغ مطلوب لـ Room
    public ReportEntity() {}

    // Helper: هل البلاغ معلق للرفع؟
    public boolean needsSync() { return !synced; }
}
