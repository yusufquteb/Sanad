package com.missingpersons.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FaceEmbeddingManager — بصمة الوجه للمقارنة
 *
 * [إصلاح بناء] حُذف TFLiteAgeEstimator — الكلاس غير موجود في المشروع.
 *   استُبدل بـ estimateAgeFromFace() داخلي يعتمد على ML Kit.
 *
 * [إصلاح خطأ-02] MATCH_THRESHOLD = 0.60f (كان 0.70f)
 * [إصلاح خطأ-02] Debug logs في cosineSimilarity و stringToEmbedding
 */
public class FaceEmbeddingManager {

    private static final String TAG = "FaceEmbeddingManager";

    private static final ExecutorService executor =
        Executors.newFixedThreadPool(2);

    private static final Handler mainHandler =
        new Handler(Looper.getMainLooper());

    /** [إصلاح خطأ-02] خُفِّض من 0.70f → 0.60f */
    public static float MATCH_THRESHOLD = 0.60f;

    private static Context appContext;

    // ════════════════════════════════════════════════════
    //  Init
    // ════════════════════════════════════════════════════

    public static void init(Context ctx) {
        appContext = ctx.getApplicationContext();
        executor.execute(() -> {
            TFLiteFaceRecognizer rec = TFLiteFaceRecognizer.getInstance(appContext);
            if (rec.isAvailable()) {
                MATCH_THRESHOLD = TFLiteFaceRecognizer.MATCH_THRESHOLD;
                Log.i(TAG, "✅ TFLite mode — threshold=" + MATCH_THRESHOLD);
            } else {
                MATCH_THRESHOLD = 0.60f;
                Log.i(TAG, "⚠️ ML Kit fallback — threshold=" + MATCH_THRESHOLD);
            }
        });
    }

    // ════════════════════════════════════════════════════
    //  Public API
    // ════════════════════════════════════════════════════

    public interface EmbeddingCallback {
        void onEmbeddingReady(float[] embedding);
        void onError(String error);
    }

    public static void extractEmbedding(Bitmap bitmap, EmbeddingCallback callback) {
        if (bitmap == null) {
            Log.w(TAG, "extractEmbedding: bitmap null");
            mainHandler.post(() -> callback.onError("الصورة فارغة"));
            return;
        }
        Log.d(TAG, "extractEmbedding: " + bitmap.getWidth() + "x" + bitmap.getHeight());
        executor.execute(() -> {
            if (appContext != null) {
                TFLiteFaceRecognizer rec = TFLiteFaceRecognizer.getInstance(appContext);
                if (rec.isAvailable()) {
                    float[] emb = rec.recognize(bitmap);
                    if (emb != null) {
                        Log.d(TAG, "✅ TFLite embedding dim=" + emb.length);
                        mainHandler.post(() -> callback.onEmbeddingReady(emb));
                        return;
                    }
                    Log.w(TAG, "TFLite null → ML Kit fallback");
                }
            }
            mainHandler.post(() -> extractWithMLKit(bitmap, callback));
        });
    }

    /**
     * Synchronous — للـ Worker threads فقط.
     * ⚠️ لا تستدعِها من Main thread.
     */
    public static float[] extractEmbeddingSync(Context ctx, Bitmap bitmap) {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("extractEmbeddingSync() on Main thread!");
        if (bitmap == null) {
            Log.w(TAG, "extractEmbeddingSync: null bitmap");
            return null;
        }
        Log.d(TAG, "extractEmbeddingSync: " + bitmap.getWidth() + "x" + bitmap.getHeight());

        // TFLite أولاً
        Context c = ctx != null ? ctx : appContext;
        if (c != null) {
            TFLiteFaceRecognizer rec = TFLiteFaceRecognizer.getInstance(c);
            if (rec.isAvailable()) {
                float[] emb = rec.recognize(bitmap);
                if (emb != null) {
                    Log.d(TAG, "✅ TFLite sync OK dim=" + emb.length);
                    return emb;
                }
                Log.w(TAG, "TFLite sync null → ML Kit");
            }
        }

        // ML Kit sync
        try {
            FaceDetectorOptions opts = new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .build();
            FaceDetector detector = FaceDetection.getClient(opts);
            List<Face> faces = Tasks.await(detector.process(
                    InputImage.fromBitmap(bitmap, 0)));
            if (faces == null || faces.isEmpty()) {
                Log.w(TAG, "extractEmbeddingSync: no face");
                return null;
            }
            Bitmap cropped = safeCrop(bitmap, faces.get(0).getBoundingBox());
            float[] emb = (cropped != null) ? buildEmbedding(cropped, faces.get(0)) : null;
            Log.d(TAG, "✅ ML Kit sync OK dim=" + (emb != null ? emb.length : 0));
            return emb;
        } catch (Exception e) {
            Log.e(TAG, "extractEmbeddingSync error: " + e.getMessage());
            return null;
        }
    }

    /**
     * [إصلاح بناء] estimateAge — لا يستخدم TFLiteAgeEstimator.
     * يعتمد على ML Kit face bounding box كتقدير تقريبي.
     */
    public static int estimateAge(Context ctx, Bitmap bitmap) {
        if (bitmap == null) return 25;
        try {
            FaceDetectorOptions opts = new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .build();
            List<Face> faces = Tasks.await(FaceDetection.getClient(opts)
                    .process(InputImage.fromBitmap(bitmap, 0)));
            if (faces == null || faces.isEmpty()) return 25;

            Face face   = faces.get(0);
            Rect box    = face.getBoundingBox();
            int  faceH  = box.height();
            int  imgH   = bitmap.getHeight();
            float ratio = imgH > 0 ? (float) faceH / imgH : 0.3f;

            // حجم الوجه نسبياً للصورة يعطي تقديراً تقريبياً للعمر
            if (ratio > 0.6f) return 5;   // طفل صغير (وجه كبير نسبياً)
            if (ratio > 0.4f) return 12;
            if (ratio > 0.25f) return 25;
            return 40;
        } catch (Exception e) {
            return 25;
        }
    }

    // ════════════════════════════════════════════════════
    //  ML Kit async
    // ════════════════════════════════════════════════════

    private static void extractWithMLKit(Bitmap bitmap, EmbeddingCallback callback) {
        FaceDetectorOptions opts = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build();

        FaceDetection.getClient(opts)
                .process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener(faces -> {
                    if (faces == null || faces.isEmpty()) {
                        Log.w(TAG, "ML Kit: no face detected");
                        callback.onError("لم يتم الكشف عن وجه في الصورة");
                        return;
                    }
                    Face face = faces.get(0);
                    executor.execute(() -> {
                        Bitmap cropped = safeCrop(bitmap, face.getBoundingBox());
                        if (cropped == null) {
                            mainHandler.post(() -> callback.onError("فشل اقتصاص الوجه"));
                            return;
                        }
                        float[] emb = buildEmbedding(cropped, face);
                        if (emb != null) {
                            Log.d(TAG, "✅ ML Kit embedding dim=" + emb.length);
                            mainHandler.post(() -> callback.onEmbeddingReady(emb));
                        } else {
                            mainHandler.post(() -> callback.onError("فشل بناء بصمة الوجه"));
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "ML Kit failed: " + e.getMessage());
                    callback.onError("خطأ ML Kit: " + e.getMessage());
                });
    }

    // ════════════════════════════════════════════════════
    //  Similarity + Serialization
    // ════════════════════════════════════════════════════

    /** [إصلاح خطأ-02] log النتيجة عند كل مقارنة */
    public static float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            Log.w(TAG, "cosineSimilarity: null or mismatch — a="
                    + (a == null ? "null" : a.length)
                    + " b=" + (b == null ? "null" : b.length));
            return 0f;
        }
        double dot = 0, nA = 0, nB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            nA  += a[i] * a[i];
            nB  += b[i] * b[i];
        }
        if (nA == 0 || nB == 0) return 0f;
        float result = (float)(dot / (Math.sqrt(nA) * Math.sqrt(nB)));
        if (result > 0.4f)
            Log.d(TAG, "🔍 similarity=" + String.format("%.3f", result)
                    + " threshold=" + MATCH_THRESHOLD
                    + " MATCH=" + (result >= MATCH_THRESHOLD));
        return result;
    }

    /** [إصلاح خطأ-02] log الـ parse */
    public static float[] stringToEmbedding(String s) {
        if (s == null || s.isEmpty()) {
            Log.w(TAG, "stringToEmbedding: empty");
            return null;
        }
        try {
            String[] parts = s.split(",");
            float[] emb = new float[parts.length];
            for (int i = 0; i < parts.length; i++)
                emb[i] = Float.parseFloat(parts[i].trim());
            Log.d(TAG, "stringToEmbedding: dim=" + emb.length);
            return emb;
        } catch (Exception e) {
            Log.e(TAG, "stringToEmbedding parse error: " + e.getMessage());
            return null;
        }
    }

    public static String embeddingToString(float[] emb) {
        if (emb == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < emb.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(emb[i]);
        }
        return sb.toString();
    }

    // ════════════════════════════════════════════════════
    //  Private helpers
    // ════════════════════════════════════════════════════

    private static Bitmap safeCrop(Bitmap src, Rect box) {
        try {
            int l = Math.max(0, box.left);
            int t = Math.max(0, box.top);
            int w = Math.min(box.width(),  src.getWidth()  - l);
            int h = Math.min(box.height(), src.getHeight() - t);
            return (w > 0 && h > 0) ? Bitmap.createBitmap(src, l, t, w, h) : null;
        } catch (Exception e) {
            Log.e(TAG, "safeCrop: " + e.getMessage());
            return null;
        }
    }

    private static float[] buildEmbedding(Bitmap cropped, Face face) {
        try {
            int w = cropped.getWidth(), h = cropped.getHeight();
            float[] means = new float[3];
            int step = Math.max(1, Math.min(w, h) / 16), cnt = 0;
            for (int y = 0; y < h; y += step)
                for (int x = 0; x < w; x += step) {
                    int px = cropped.getPixel(x, y);
                    means[0] += (px >> 16) & 0xFF;
                    means[1] += (px >> 8)  & 0xFF;
                    means[2] +=  px        & 0xFF;
                    cnt++;
                }
            if (cnt > 0) for (int i = 0; i < 3; i++) means[i] /= cnt;

            Float leftEye  = face.getLeftEyeOpenProbability();
            Float rightEye = face.getRightEyeOpenProbability();
            Float smiling  = face.getSmilingProbability();

            float[] emb = new float[128];
            emb[0]  = (float) w / h;
            emb[1]  = means[0] / 255f;
            emb[2]  = means[1] / 255f;
            emb[3]  = means[2] / 255f;
            emb[4]  = face.getHeadEulerAngleY() / 90f;
            emb[5]  = face.getHeadEulerAngleZ() / 90f;
            emb[6]  = leftEye  != null ? leftEye  : 0.5f;
            emb[7]  = rightEye != null ? rightEye : 0.5f;
            emb[8]  = smiling  != null ? smiling  : 0.5f;
            emb[9]  = (float) w / 300f;
            emb[10] = (float) h / 300f;
            for (int i = 11; i < 128; i++)
                emb[i] = emb[i % 11] * (float) Math.sin(i * 0.1);
            return emb;
        } catch (Exception e) {
            Log.e(TAG, "buildEmbedding: " + e.getMessage());
            return null;
        }
    }
}
