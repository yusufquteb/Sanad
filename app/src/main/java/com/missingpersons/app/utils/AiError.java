package com.missingpersons.app.utils;

import android.util.Log;

/**
 * AiError — رموز أخطاء وحدة الذكاء الاصطناعي وأدوات تسجيلها.
 *
 * الثوابت:
 *   MODEL_NOT_LOADED           — النموذج لم يُحمَّل
 *   NULL_BITMAP                — الصورة فارغة (null)
 *   FACE_NOT_FOUND             — لم يُكتشف وجه
 *   CROP_FAILED                — فشل قص الوجه
 *   EMBEDDING_EXTRACTION_FAILED — فشل استخراج البصمة
 *   IMAGE_TOO_BLURRY           — الصورة ضبابية
 *   IMAGE_TOO_DARK             — الصورة داكنة
 *   FACE_TOO_SMALL             — الوجه صغير جداً
 */
public final class AiError {

    private AiError() {}

    // ── Error codes ───────────────────────────────────────────────────────
    public static final String MODEL_NOT_LOADED            = "MODEL_NOT_LOADED";
    public static final String NULL_BITMAP                 = "NULL_BITMAP";
    public static final String FACE_NOT_FOUND              = "FACE_NOT_FOUND";
    public static final String CROP_FAILED                 = "CROP_FAILED";
    public static final String EMBEDDING_EXTRACTION_FAILED = "EMBEDDING_EXTRACTION_FAILED";
    public static final String IMAGE_TOO_BLURRY            = "IMAGE_TOO_BLURRY";
    public static final String IMAGE_TOO_DARK              = "IMAGE_TOO_DARK";
    public static final String FACE_TOO_SMALL              = "FACE_TOO_SMALL";

    // ── Logging ───────────────────────────────────────────────────────────

    /** تسجيل خطأ بسيط بدون exception. */
    public static void log(String tag, String code, String detail) {
        Log.w(tag, "[AiError] " + code + " — " + detail);
    }

    /** تسجيل خطأ مع Throwable (قد يكون null). */
    public static void logAll(String tag, String code, String detail, Throwable t) {
        if (t != null) {
            Log.e(tag, "[AiError] " + code + " — " + detail, t);
        } else {
            Log.e(tag, "[AiError] " + code + " — " + detail);
        }
        try {
            com.google.firebase.crashlytics.FirebaseCrashlytics c =
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance();
            c.setCustomKey("ai_error_code", code);
            c.setCustomKey("ai_error_detail", detail != null ? detail : "");
            if (t != null) c.recordException(t);
            else           c.recordException(new RuntimeException("AI_ERROR:" + code + " " + detail));
        } catch (Exception ignored) {}
    }

    // ── User-facing messages (Arabic) ─────────────────────────────────────

    public static String toUserMessage(String code) {
        if (code == null) return "خطأ غير معروف";
        switch (code) {
            case MODEL_NOT_LOADED:
                return "نموذج التعرف على الوجه غير متاح حالياً";
            case NULL_BITMAP:
                return "لم يتم تحميل الصورة، يرجى اختيار صورة أخرى";
            case FACE_NOT_FOUND:
                return "لم يُكتشف وجه واضح في الصورة، يرجى استخدام صورة أوضح";
            case CROP_FAILED:
                return "فشل معالجة منطقة الوجه، يرجى المحاولة بصورة مختلفة";
            case EMBEDDING_EXTRACTION_FAILED:
                return "فشل تحليل ملامح الوجه، يرجى استخدام صورة ذات جودة أعلى";
            case IMAGE_TOO_BLURRY:
                return "الصورة غير واضحة (ضبابية)، يرجى التقاط صورة أوضح";
            case IMAGE_TOO_DARK:
                return "الصورة داكنة جداً، يرجى استخدام إضاءة أفضل";
            case FACE_TOO_SMALL:
                return "الوجه صغير جداً في الصورة، يرجى الاقتراب أكثر";
            default:
                return "خطأ في تحليل الصورة (" + code + ")";
        }
    }
}
