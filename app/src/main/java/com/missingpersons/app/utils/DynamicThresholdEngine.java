package com.missingpersons.app.utils;

/**
 * DynamicThresholdEngine — عتبة مطابقة ديناميكية بناءً على جودة الصورة.
 *
 * بدلاً من عتبة ثابتة، تتكيف العتبة مع جودة الصورتين.
 *
 * [إصلاح 2026-07-23] كانت هذه العتبات (0.77/0.72/0.68) مُعايَرة على مقياس
 * نموذج adaface_ir18 المعطوب (اختبار فعلي أثبت أنه لا يميّز أي شيء عن أي
 * شيء — راجع AdaFaceRecognizer.java). بعد التحول لـ mobilefacenet.tflite
 * الحقيقي، نفس الشخص فعلياً (3 صور حقيقية) أعطى 0.5554–0.7531 بينما وجه
 * مقابل خلفية أعطى 0.24–0.32. عُدِّلت العتبات تناسبياً لتقع ضمن هذا
 * النطاق الواقعي، لكنها تقديرية (بيانات محدودة: شخص واحد فقط، بلا أزواج
 * "أشخاص مختلفين" حقيقية) وتحتاج معايرة فعلية قبل الاعتماد الكامل عليها.
 *
 *   - جودة عالية (≥0.70) → عتبة صارمة  0.62f
 *   - جودة متوسطة (≥0.55)→ عتبة مرنة   0.55f
 *   - جودة منخفضة (≥0.45)→ عتبة مرنة   0.48f + Human Review إلزامي
 *   - أقل من 0.45         → رفض (INSUFFICIENT_QUALITY)
 */
public final class DynamicThresholdEngine {

    private DynamicThresholdEngine() {}

    // ── Threshold Computation ─────────────────────────────────────────────

    /**
     * احسب العتبة الديناميكية بناءً على أدنى جودة بين الصورتين.
     *
     * @param queryQuality  جودة صورة الاستعلام (0.0–1.0)
     * @param storedQuality جودة الصورة المخزنة (0.0–1.0)
     * @return العتبة المناسبة (0.48 – 0.62)
     */
    public static float computeThreshold(float queryQuality, float storedQuality) {
        float minQuality = Math.min(queryQuality, storedQuality);
        if (minQuality >= 0.70f) return 0.62f;
        if (minQuality >= 0.55f) return 0.55f;
        return 0.48f;
    }

    /**
     * overload مع QualityResult مباشرةً.
     */
    public static float computeThreshold(
            FaceQualityAnalyzer.QualityResult query,
            FaceQualityAnalyzer.QualityResult stored) {
        return computeThreshold(
            query  != null ? query.score  : 0.5f,
            stored != null ? stored.score : 0.5f);
    }

    // ── Match Classification ───────────────────────────────────────────────

    /**
     * صنّف نتيجة المطابقة بناءً على التشابه والعتبة وجودة الصورة.
     *
     * @param similarity  قيمة Cosine Similarity (0.0–1.0)
     * @param threshold   العتبة الديناميكية من computeThreshold()
     * @param queryScore  جودة صورة الاستعلام
     * @return MatchStatus
     */
    public static MatchStatus classify(float similarity, float threshold, float queryScore) {
        if (queryScore < 0.45f)           return MatchStatus.INSUFFICIENT_QUALITY;
        if (similarity >= threshold + 0.05f) return MatchStatus.AUTO_MATCH;
        if (similarity >= threshold)      return MatchStatus.REVIEW_REQUIRED;
        if (similarity >= 0.40f)          return MatchStatus.REVIEW_REQUIRED; // منطقة رمادية (مُعدَّلة لمقياس mobilefacenet)
        return MatchStatus.AUTO_NO_MATCH;
    }

    /**
     * هل يجب إرسال هذه المطابقة لمراجعة بشرية؟
     */
    public static boolean requiresHumanReview(float similarity, float threshold, float queryScore) {
        MatchStatus status = classify(similarity, threshold, queryScore);
        return status == MatchStatus.REVIEW_REQUIRED;
    }

    // ── MatchStatus ───────────────────────────────────────────────────────

    public enum MatchStatus {
        /** تطابق قوي — إشعار تلقائي */
        AUTO_MATCH,

        /** منطقة رمادية — يُرسل لـ Human Review */
        REVIEW_REQUIRED,

        /** لا تطابق — يبقى في قاعدة البيانات */
        AUTO_NO_MATCH,

        /** جودة الصورة غير كافية — طلب صورة أوضح */
        INSUFFICIENT_QUALITY
    }
}
