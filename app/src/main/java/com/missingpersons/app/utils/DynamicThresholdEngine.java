package com.missingpersons.app.utils;

/**
 * DynamicThresholdEngine — عتبة مطابقة ديناميكية بناءً على جودة الصورة.
 *
 * بدلاً من عتبة ثابتة، تتكيف العتبة مع جودة الصورتين (AdaFace IR18):
 *   - جودة عالية (≥0.70) → عتبة صارمة  0.77f
 *   - جودة متوسطة (≥0.55)→ عتبة مرنة   0.72f
 *   - جودة منخفضة (≥0.45)→ عتبة مرنة   0.68f + Human Review إلزامي
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
     * @return العتبة المناسبة (0.68 – 0.77)
     */
    public static float computeThreshold(float queryQuality, float storedQuality) {
        float minQuality = Math.min(queryQuality, storedQuality);
        if (minQuality >= 0.70f) return 0.77f;
        if (minQuality >= 0.55f) return 0.72f;
        return 0.68f;
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
        if (similarity >= 0.60f)          return MatchStatus.REVIEW_REQUIRED; // منطقة رمادية
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
