package com.missingpersons.app.utils;

/**
 * EmbeddingCleanupUtil — ثوابت أسماء حقول Firebase الخاصة بالـ embedding.
 *
 * تُستخدم عند كتابة أو قراءة metadata الـ embedding من Firebase
 * لضمان الاتساق في التسمية عبر كل أجزاء التطبيق.
 */
public final class EmbeddingCleanupUtil {

    private EmbeddingCleanupUtil() {}

    /** حقل نسخة الـ embedding في Firebase (القيمة: int مثل 2) */
    public static final String FIELD_EMBEDDING_VERSION  = "embeddingVersion";

    /** حقل نسخة النموذج في Firebase (القيمة: String مثل "mobilefacenet_v1") */
    public static final String FIELD_MODEL_VERSION      = "modelVersion";

    /** حقل نسخة الـ preprocessing في Firebase (القيمة: int مثل 1) */
    public static final String FIELD_PREPROCESSING_VER  = "preprocessingVersion";
}
