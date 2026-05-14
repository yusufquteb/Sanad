package com.missingpersons.app.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceLandmark;

/**
 * FaceQualityAnalyzer — تقييم جودة صورة الوجه بنتيجة عائمة (0.0 – 1.0).
 *
 * يحسب نتيجة مركّبة من 5 مقاييس:
 *   30% — الحدة (Blur)
 *   20% — السطوع (Brightness)
 *   20% — حجم الوجه (FaceSize)
 *   15% — الإمالة والدوران (Pose)
 *   15% — انكشاف منطقة العيون (Occlusion)
 *
 * يحل محل ImageQualityEnhancer (Boolean accept/reject) في مسار V2.
 */
public final class FaceQualityAnalyzer {

    private static final String TAG = "FaceQualityAnalyzer";

    public FaceQualityAnalyzer() {}

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * تحليل جودة صورة الوجه المحاذاة.
     *
     * @param alignedFace الصورة المحاذاة بعد FivePointAligner
     * @param detection   نتيجة ML Kit (للـ pose/size/landmarks)
     * @return QualityResult مع نتيجة مركّبة وتفاصيل كل مقياس
     */
    public QualityResult analyze(Bitmap alignedFace, Face detection) {
        if (alignedFace == null) {
            return new QualityResult(0f, 0f, 0f, 0f, 0f, 0f);
        }

        float blurScore       = assessBlur(alignedFace);
        float brightnessScore = assessBrightness(alignedFace);
        float faceSizeScore   = (detection != null) ? assessFaceSize(detection) : 0.5f;
        float poseScore       = (detection != null) ? assessPose(detection) : 0.5f;
        float occlusionScore  = assessOcclusion(alignedFace);

        float composite = blurScore       * 0.30f
                        + brightnessScore * 0.20f
                        + faceSizeScore   * 0.20f
                        + poseScore       * 0.15f
                        + occlusionScore  * 0.15f;

        composite = Math.max(0f, Math.min(1f, composite));

        Log.d(TAG, String.format("جودة: %.2f (blur=%.2f bright=%.2f size=%.2f pose=%.2f occl=%.2f)",
            composite, blurScore, brightnessScore, faceSizeScore, poseScore, occlusionScore));

        return new QualityResult(composite, blurScore, brightnessScore,
            faceSizeScore, poseScore, occlusionScore);
    }

    // ── Quality Dimensions ────────────────────────────────────────────────

    /** حدة الصورة — Laplacian variance محوّل لـ 0.0–1.0 */
    private float assessBlur(Bitmap bmp) {
        float variance = computeLaplacianVariance(bmp);
        // variance ≥ 200 → 1.0 | ≤ 20 → 0.0
        return Math.min(1f, Math.max(0f, (variance - 20f) / 180f));
    }

    /** سطوع الصورة — مثالي 80–180 → 1.0 */
    private float assessBrightness(Bitmap bmp) {
        float mean = computeMeanBrightness(bmp);
        if (mean < 25f || mean > 235f) return 0f;
        if (mean >= 80f && mean <= 180f) return 1f;
        return mean < 80f ? (mean - 25f) / 55f : (235f - mean) / 55f;
    }

    /** حجم الوجه نسبةً إلى الصورة — ≥120×120 → 1.0 | < 60×60 → 0.0 */
    private float assessFaceSize(Face face) {
        Rect box = face.getBoundingBox();
        int area = box.width() * box.height();
        return Math.min(1f, Math.max(0f, (float)(area - 3600) / (14400f - 3600f)));
    }

    /** إمالة الرأس — يستخدم Euler angles من ML Kit */
    private float assessPose(Face face) {
        Float yaw   = face.getHeadEulerAngleY(); // يسار/يمين
        Float pitch = face.getHeadEulerAngleX(); // أعلى/أسفل
        if (yaw == null || pitch == null) return 0.5f;
        float yawScore   = Math.max(0f, 1f - Math.abs(yaw)   / 45f);
        float pitchScore = Math.max(0f, 1f - Math.abs(pitch) / 30f);
        return (yawScore + pitchScore) / 2f;
    }

    /** انكشاف منطقة العيون — هل الجزء العلوي من الصورة فيه تباين كافٍ؟ */
    private float assessOcclusion(Bitmap bmp) {
        // منطقة العيون: الثلث العلوي من الصورة
        int eyeRegionH = bmp.getHeight() / 3;
        float variance = computeRegionVariance(bmp, 0, 0, bmp.getWidth(), eyeRegionH);
        // variance > 150 → عيون مكشوفة ✅ | < 50 → محجوبة ❌
        return Math.min(1f, Math.max(0f, (variance - 50f) / 100f));
    }

    // ── Computation Helpers ───────────────────────────────────────────────

    /** تباين مرشح Laplacian على نسخة مصغّرة */
    private float computeLaplacianVariance(Bitmap bmp) {
        Bitmap small = ensureSmall(bmp, 128);
        int w = small.getWidth(), h = small.getHeight();
        if (w < 3 || h < 3) return 0f;

        float[][] gray = toGrayscale(small, w, h);

        double mean = 0, m2 = 0;
        long count = 0;
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                float lap = gray[y-1][x] + gray[y+1][x]
                          + gray[y][x-1] + gray[y][x+1]
                          - 4 * gray[y][x];
                count++;
                double delta = lap - mean;
                mean += delta / count;
                m2   += delta * (lap - mean);
            }
        }
        return count > 1 ? (float)(m2 / (count - 1)) : 0f;
    }

    /** متوسط سطوع الصورة (0–255) */
    private float computeMeanBrightness(Bitmap bmp) {
        Bitmap small = ensureSmall(bmp, 128);
        int w = small.getWidth(), h = small.getHeight();
        int step = Math.max(1, Math.min(w, h) / 32);
        long sum = 0; int count = 0;
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int px = small.getPixel(x, y);
                int r = (px >> 16) & 0xFF, g = (px >> 8) & 0xFF, b = px & 0xFF;
                sum += (long)(0.299f * r + 0.587f * g + 0.114f * b);
                count++;
            }
        }
        return count > 0 ? (float) sum / count : 0f;
    }

    /** تباين منطقة محددة من الصورة */
    private float computeRegionVariance(Bitmap bmp, int rx, int ry, int rw, int rh) {
        rw = Math.min(rw, bmp.getWidth()  - rx);
        rh = Math.min(rh, bmp.getHeight() - ry);
        if (rw <= 0 || rh <= 0) return 0f;

        int step = Math.max(1, Math.min(rw, rh) / 16);
        double mean = 0; long count = 0;
        for (int y = ry; y < ry + rh; y += step) {
            for (int x = rx; x < rx + rw; x += step) {
                int px = bmp.getPixel(x, y);
                int r = (px >> 16) & 0xFF, g = (px >> 8) & 0xFF, bv = px & 0xFF;
                mean += 0.299f * r + 0.587f * g + 0.114f * bv;
                count++;
            }
        }
        if (count == 0) return 0f;
        mean /= count;

        double variance = 0;
        for (int y = ry; y < ry + rh; y += step) {
            for (int x = rx; x < rx + rw; x += step) {
                int px = bmp.getPixel(x, y);
                int r = (px >> 16) & 0xFF, g = (px >> 8) & 0xFF, bv = px & 0xFF;
                double lum = 0.299f * r + 0.587f * g + 0.114f * bv;
                double d = lum - mean;
                variance += d * d;
            }
        }
        return (float)(variance / count);
    }

    private float[][] toGrayscale(Bitmap bmp, int w, int h) {
        float[][] gray = new float[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int px = bmp.getPixel(x, y);
                int r = (px >> 16) & 0xFF, g = (px >> 8) & 0xFF, b = px & 0xFF;
                gray[y][x] = 0.299f * r + 0.587f * g + 0.114f * b;
            }
        }
        return gray;
    }

    private Bitmap ensureSmall(Bitmap src, int maxDim) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxDim && h <= maxDim) return src;
        float scale = (float) maxDim / Math.max(w, h);
        return Bitmap.createScaledBitmap(src, (int)(w * scale), (int)(h * scale), true);
    }

    // ── QualityResult ─────────────────────────────────────────────────────

    public static final class QualityResult {
        public final float score;            // 0.0 – 1.0 المركّب
        public final float blurScore;
        public final float brightnessScore;
        public final float faceSizeScore;
        public final float poseScore;
        public final float occlusionScore;

        public QualityResult(float score, float blur, float brightness,
                             float faceSize, float pose, float occlusion) {
            this.score           = score;
            this.blurScore       = blur;
            this.brightnessScore = brightness;
            this.faceSizeScore   = faceSize;
            this.poseScore       = pose;
            this.occlusionScore  = occlusion;
        }

        public QualityTier getTier() {
            if (score >= 0.70f) return QualityTier.HIGH;
            if (score >= 0.45f) return QualityTier.MEDIUM;
            return QualityTier.LOW;
        }

        /** هل الجودة كافية لحفظ الـ embedding؟ */
        public boolean isAcceptable() { return score >= 0.45f; }

        @Override
        public String toString() {
            return String.format("QualityResult{score=%.2f tier=%s blur=%.2f bright=%.2f size=%.2f pose=%.2f occl=%.2f}",
                score, getTier(), blurScore, brightnessScore, faceSizeScore, poseScore, occlusionScore);
        }

        public enum QualityTier { HIGH, MEDIUM, LOW }
    }
}
