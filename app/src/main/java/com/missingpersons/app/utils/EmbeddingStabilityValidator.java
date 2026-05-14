package com.missingpersons.app.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.Log;

import java.io.ByteArrayOutputStream;

/**
 * EmbeddingStabilityValidator — يتحقق من ثبات الـ embedding تحت تشوهات طفيفة.
 *
 * يُشغّل AdaFace على 4 نسخ من الصورة (الأصلية + 3 تشوهات)
 * ويحسب متوسط Cosine Similarity بين الأصلية وكل تشوه.
 * إذا كان المتوسط < 0.88 → الصورة غير مستقرة → لا تُحفظ الـ embedding.
 */
public final class EmbeddingStabilityValidator {

    private static final String TAG          = "EmbeddingStabilityValidator";
    private static final float  MIN_STABILITY = 0.88f;

    private final AdaFaceRecognizer recognizer;

    public EmbeddingStabilityValidator(AdaFaceRecognizer recognizer) {
        this.recognizer = recognizer;
    }

    /**
     * هل الـ embedding مستقر؟
     *
     * @param alignedFace الصورة المحاذاة (من FivePointAligner)
     * @return true إذا كان متوسط التشابه ≥ 0.88
     */
    public boolean isStable(Bitmap alignedFace) {
        if (alignedFace == null || !recognizer.isAvailable()) return false;

        try {
            float[] original   = recognizer.embed(alignedFace);
            float[] compressed = recognizer.embed(simulateCompression(alignedFace, 70));
            float[] rotated    = recognizer.embed(rotate(alignedFace, 5f));
            float[] darkened   = recognizer.embed(adjustBrightness(alignedFace, -20));

            if (original == null || compressed == null || rotated == null || darkened == null) {
                Log.w(TAG, "فشل استخراج embedding أثناء التحقق من الاستقرار");
                return false;
            }

            float avgSim = (
                cosineSimilarity(original, compressed) +
                cosineSimilarity(original, rotated)    +
                cosineSimilarity(original, darkened)
            ) / 3f;

            Log.d(TAG, String.format("stability avgSim=%.3f (min=%.2f) → %s",
                avgSim, MIN_STABILITY, avgSim >= MIN_STABILITY ? "مستقر ✅" : "غير مستقر ❌"));

            return avgSim >= MIN_STABILITY;

        } catch (Exception e) {
            Log.w(TAG, "خطأ في التحقق من الاستقرار: " + e.getMessage());
            return false;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0f;
        float dot = 0f;
        for (int i = 0; i < a.length; i++) dot += a[i] * b[i];
        return dot; // vectors are already L2-normalized
    }

    private Bitmap simulateCompression(Bitmap src, int quality) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        src.compress(Bitmap.CompressFormat.JPEG, quality, out);
        byte[] bytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    private Bitmap rotate(Bitmap src, float degrees) {
        Matrix m = new Matrix();
        m.postRotate(degrees, src.getWidth() / 2f, src.getHeight() / 2f);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
    }

    private Bitmap adjustBrightness(Bitmap src, int delta) {
        Bitmap result = src.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(result);
        ColorMatrix cm = new ColorMatrix();
        cm.set(new float[]{
            1, 0, 0, 0, delta,
            0, 1, 0, 0, delta,
            0, 0, 1, 0, delta,
            0, 0, 0, 1, 0
        });
        Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(src, 0, 0, paint);
        return result;
    }
}
