package com.missingpersons.app.utils;

import android.graphics.Bitmap;
import android.util.Log;

/**
 * ImageQualityEnhancer — التحقق من جودة صورة الوجه قبل استخراج الـ embedding.
 *
 * يطبّق ثلاثة شروط:
 *   1. الحجم الأدنى  (MIN_SIZE)
 *   2. الضبابية      (Laplacian variance > BLUR_THRESHOLD)
 *   3. السطوع        (mean brightness بين DARK_THRESHOLD و BRIGHT_THRESHOLD)
 */
public final class ImageQualityEnhancer {

    private static final String TAG = "ImageQualityEnhancer";

    // ── Thresholds ────────────────────────────────────────────────────────
    private static final int   MIN_SIZE         = 32;    // أصغر حجم مقبول (px)
    private static final float BLUR_THRESHOLD   = 35f;   // variance of Laplacian — خُفِّضت من 80 لقبول صور الهاتف الواقعية
    private static final float DARK_THRESHOLD   = 20f;   // متوسط السطوع الأدنى (0-255)
    private static final float BRIGHT_THRESHOLD = 245f;  // متوسط السطوع الأعلى

    private ImageQualityEnhancer() {}

    // ── Result ────────────────────────────────────────────────────────────

    public static class QualityResult {
        public final boolean isAcceptable;
        public final String  errorCode;
        public final float   blurScore;
        public final float   brightness;

        QualityResult(boolean isAcceptable, String errorCode, float blurScore, float brightness) {
            this.isAcceptable = isAcceptable;
            this.errorCode    = errorCode;
            this.blurScore    = blurScore;
            this.brightness   = brightness;
        }

        @Override
        public String toString() {
            return "QualityResult{ok=" + isAcceptable
                + " code=" + errorCode
                + " blur=" + String.format("%.1f", blurScore)
                + " bright=" + String.format("%.1f", brightness) + "}";
        }
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * تحقق كامل من جودة الصورة.
     * يُستدعى بعد crop الوجه وقبل TFLite.
     */
    public static QualityResult validateFull(Bitmap bitmap) {
        if (bitmap == null) {
            return new QualityResult(false, AiError.NULL_BITMAP, 0f, 0f);
        }

        // 1. تحقق الحجم
        if (bitmap.getWidth() < MIN_SIZE || bitmap.getHeight() < MIN_SIZE) {
            Log.w(TAG, "وجه صغير جداً: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            return new QualityResult(false, AiError.FACE_TOO_SMALL, 0f, 0f);
        }

        // حساب السطوع والضبابية على نسخة مصغّرة لتسريع المعالجة
        Bitmap small = ensureSmall(bitmap, 128);

        float brightness = computeBrightness(small);
        float blurScore  = computeLaplacianVariance(small);

        // 2. تحقق السطوع
        if (brightness < DARK_THRESHOLD) {
            Log.w(TAG, "صورة داكنة: brightness=" + brightness);
            return new QualityResult(false, AiError.IMAGE_TOO_DARK, blurScore, brightness);
        }

        // 3. تحقق الضبابية
        if (blurScore < BLUR_THRESHOLD) {
            Log.w(TAG, "صورة ضبابية: blur=" + blurScore);
            return new QualityResult(false, AiError.IMAGE_TOO_BLURRY, blurScore, brightness);
        }

        Log.d(TAG, "✅ جودة مقبولة: blur=" + String.format("%.1f", blurScore)
            + " bright=" + String.format("%.1f", brightness));
        return new QualityResult(true, null, blurScore, brightness);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static Bitmap ensureSmall(Bitmap src, int maxDim) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxDim && h <= maxDim) return src;
        float scale = (float) maxDim / Math.max(w, h);
        return Bitmap.createScaledBitmap(src, (int)(w * scale), (int)(h * scale), true);
    }

    /** متوسط السطوع بقناة الضوء (Y من YUV). */
    private static float computeBrightness(Bitmap bmp) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        long sum = 0;
        int  count = 0;
        int  step = Math.max(1, Math.min(w, h) / 32);
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int px = bmp.getPixel(x, y);
                int r = (px >> 16) & 0xFF;
                int g = (px >> 8)  & 0xFF;
                int b =  px        & 0xFF;
                sum += (int)(0.299f * r + 0.587f * g + 0.114f * b);
                count++;
            }
        }
        return count > 0 ? (float) sum / count : 0f;
    }

    /**
     * تباين مرشح Laplacian — مقياس حدة الصورة.
     * قيمة عالية = صورة واضحة، قيمة منخفضة = ضبابية.
     */
    private static float computeLaplacianVariance(Bitmap bmp) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        if (w < 3 || h < 3) return 0f;

        // تحويل لـ Grayscale
        float[][] gray = new float[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int px = bmp.getPixel(x, y);
                int r = (px >> 16) & 0xFF;
                int g = (px >> 8)  & 0xFF;
                int b =  px        & 0xFF;
                gray[y][x] = 0.299f * r + 0.587f * g + 0.114f * b;
            }
        }

        // تطبيق Laplacian kernel: [0,1,0],[1,-4,1],[0,1,0]
        double mean = 0, m2 = 0;
        long count = 0;
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                float lap = gray[y-1][x] + gray[y+1][x]
                          + gray[y][x-1] + gray[y][x+1]
                          - 4 * gray[y][x];
                // Welford online variance
                count++;
                double delta = lap - mean;
                mean += delta / count;
                m2   += delta * (lap - mean);
            }
        }
        return count > 1 ? (float)(m2 / (count - 1)) : 0f;
    }
}
