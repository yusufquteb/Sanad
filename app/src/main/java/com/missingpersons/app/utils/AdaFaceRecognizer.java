package com.missingpersons.app.utils;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * AdaFaceRecognizer — محرك التعرف على الوجوه V2
 *
 * يستخدم نموذج AdaFace IR18 TFLite لإنتاج embedding حقيقي 512-dim.
 *
 * المعمارية:
 *   - Singleton — instance واحد فقط في التطبيق
 *   - Input:  112×112 RGB — CHW format — ((pixel-127.5)/128.0)
 *   - Output: float[512] L2-normalized
 *   - Threads: 2 (موازنة بين الأداء وحرارة الجهاز)
 *
 * ⚠️ يتطلب وجود الملف: assets/models/adaface_ir18_112.tflite
 *    قم بتنزيله من: https://github.com/mk-minchul/AdaFace (اختر IR18)
 */
public final class AdaFaceRecognizer {

    private static final String TAG        = "AdaFaceRecognizer";
    private static final String MODEL_FILE = "models/adaface_ir18_112.tflite";
    public  static final int    INPUT_SIZE     = 112;
    public  static final int    EMBEDDING_DIM  = 512;

    private static volatile AdaFaceRecognizer instance;

    private final Interpreter interpreter;
    private final boolean      available;

    // ── Singleton ────────────────────────────────────────────────────────
    private AdaFaceRecognizer(Context ctx) {
        boolean ok = false;
        Interpreter interp = null;
        try {
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(2);
            options.setUseNNAPI(false); // NNAPI غير مستقر على بعض الأجهزة
            interp = new Interpreter(loadModelFile(ctx), options);
            ok = true;
            Log.i(TAG, "✅ AdaFace IR18 loaded — embedding_dim=" + EMBEDDING_DIM);
        } catch (IOException e) {
            Log.e(TAG, "❌ فشل تحميل adaface_ir18_112.tflite — تأكد من وجود الملف في assets/models/");
            AiError.logAll(TAG, AiError.MODEL_NOT_LOADED,
                "AdaFaceRecognizer init: " + e.getMessage(), e);
        } catch (Exception e) {
            Log.e(TAG, "❌ خطأ غير متوقع في تهيئة AdaFace: " + e.getMessage());
            AiError.logAll(TAG, AiError.MODEL_NOT_LOADED, "AdaFaceRecognizer: " + e.getMessage(), e);
        }
        this.interpreter = interp;
        this.available   = ok;
    }

    public static AdaFaceRecognizer getInstance(Context ctx) {
        if (instance == null) {
            synchronized (AdaFaceRecognizer.class) {
                if (instance == null)
                    instance = new AdaFaceRecognizer(ctx.getApplicationContext());
            }
        }
        return instance;
    }

    public boolean isAvailable() { return available; }

    // ── Embedding ────────────────────────────────────────────────────────

    /**
     * استخراج embedding من صورة وجه محاذاة (112×112 preferred).
     *
     * @param alignedFace صورة الوجه المحاذاة من FivePointAligner
     * @return float[512] L2-normalized، أو null إذا فشل النموذج
     */
    public float[] embed(Bitmap alignedFace) {
        if (!available || interpreter == null) {
            AiError.log(TAG, AiError.MODEL_NOT_LOADED, "embed: النموذج غير متاح");
            return null;
        }
        if (alignedFace == null) {
            AiError.log(TAG, AiError.NULL_BITMAP, "embed: bitmap null");
            return null;
        }
        try {
            // Resize إلى 112×112
            Bitmap scaled = Bitmap.createScaledBitmap(alignedFace, INPUT_SIZE, INPUT_SIZE, true);

            // تحويل الصورة → CHW float input
            int pixelCount = INPUT_SIZE * INPUT_SIZE;
            int[] pixels   = new int[pixelCount];
            scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

            // CHW: [1, 3, 112, 112]
            float[][][][] input = new float[1][3][INPUT_SIZE][INPUT_SIZE];
            for (int i = 0; i < pixelCount; i++) {
                int h = i / INPUT_SIZE;
                int w = i % INPUT_SIZE;
                input[0][0][h][w] = ((pixels[i] >> 16 & 0xFF) - 127.5f) / 128.0f; // R
                input[0][1][h][w] = ((pixels[i] >>  8 & 0xFF) - 127.5f) / 128.0f; // G
                input[0][2][h][w] = ((pixels[i]       & 0xFF) - 127.5f) / 128.0f; // B
            }

            float[][] output = new float[1][EMBEDDING_DIM];
            interpreter.run(input, output);

            float[] embedding = l2Normalize(output[0]);
            Log.d(TAG, "✅ AdaFace embedding: dim=" + embedding.length);
            return embedding;

        } catch (Exception e) {
            AiError.logAll(TAG, AiError.EMBEDDING_EXTRACTION_FAILED,
                "embed: " + e.getMessage(), e);
            return null;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private float[] l2Normalize(float[] v) {
        double norm = 0.0;
        for (float x : v) norm += (double) x * x;
        norm = Math.sqrt(norm);
        if (norm < 1e-10) return v;
        float[] result = new float[v.length];
        for (int i = 0; i < v.length; i++) result[i] = (float)(v[i] / norm);
        return result;
    }

    private MappedByteBuffer loadModelFile(Context ctx) throws IOException {
        AssetFileDescriptor fd = ctx.getAssets().openFd(MODEL_FILE);
        FileInputStream in = new FileInputStream(fd.getFileDescriptor());
        MappedByteBuffer buf = in.getChannel().map(
            FileChannel.MapMode.READ_ONLY,
            fd.getStartOffset(),
            fd.getDeclaredLength());
        in.close();
        return buf;
    }

    // ── Cosine Similarity ────────────────────────────────────────────────

    /**
     * حساب Cosine Similarity بين embedding-ين L2-normalized.
     * القيمة بين -1.0 و 1.0 (أعلى = أقرب).
     */
    public static float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0f;
        float dot = 0f;
        for (int i = 0; i < a.length; i++) dot += a[i] * b[i];
        return dot; // vectors are already L2-normalized
    }
}
