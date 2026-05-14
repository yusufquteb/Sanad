package com.missingpersons.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FaceEmbeddingManager — استخراج بصمة الوجه
 *
 * [المرحلة 1 — إصلاحات جوهرية]
 *
 * ════════════════════════════════════════════════════════
 * التغييرات الجديدة في هذا الإصدار:
 *
 * ✅ 1. Quality Gate حقيقي قبل استخراج أي embedding
 *    → ImageQualityEnhancer.validateFull() يُطبَّق على الوجه المقصوص
 *    → إذا فشل (ضبابي / داكن / صغير) → onError() مع AiError code
 *    → لا يُحفظ embedding من صورة فاشلة في الجودة أبداً
 *
 * ✅ 2. Face Alignment باستخدام ML Kit Landmarks
 *    → LEFT_EYE و RIGHT_EYE يُستخدمان لحساب زاوية الميل
 *    → الصورة تُدار بالزاوية المعاكسة قبل الـ crop
 *    → يرفع دقة التطابق بشكل كبير خصوصاً للصور المائلة
 *    → إذا فشل الـ alignment (لا landmarks) → يكمل بدونه مع تحذير
 *
 * ✅ 3. embeddingVersion مضاف كـ metadata للـ embedding
 *    → MODEL_VERSION = "mobilefacenet_v1"
 *    → EMBEDDING_VERSION = 2 (يُزاد عند أي تغيير في preprocessing)
 *    → يُرسل مع البيانات لـ Firebase لتمييز الـ embeddings المتوافقة
 *
 * ════════════════════════════════════════════════════════
 * RULES (لا تكسرها):
 *   - لا embedding بدون Quality Gate
 *   - لا embedding بدون TFLite
 *   - لا تغيير EMBEDDING_VERSION بدون إعادة توليد كل الـ embeddings
 *   - لا Math.random() في أي مكان هنا
 * ════════════════════════════════════════════════════════
 */
public class FaceEmbeddingManager {

    private static final String TAG = "FaceEmbeddingManager";

    // ── Versioning — لا تغيّر إلا بعد مراجعة Migration ─────
    /** نسخة النموذج — V2: AdaFace IR18 512-dim */
    public static final String MODEL_VERSION           = "adaface_ir18";

    /** نسخة الـ embedding الحالية — V2: AdaFace 512-dim */
    public static final int    EMBEDDING_VERSION       = 3;

    /** نسخة الـ preprocessing الحالية */
    public static final int    PREPROCESSING_VERSION   = 2;

    /** نسخة MobileFaceNet القديمة — 128-dim — لا تُطابق مع V3 */
    public static final int    LEGACY_EMBEDDING_VERSION = 2;

    // ────────────────────────────────────────────────────────
    public static final float MATCH_THRESHOLD    = 0.82f;

    private static Context       appContext;
    private static final ExecutorService executor     = Executors.newFixedThreadPool(2);
    private static final Handler         mainHandler  = new Handler(Looper.getMainLooper());

    // ════════════════════════════════════════════════════════
    //  Callback
    // ════════════════════════════════════════════════════════

    public interface EmbeddingCallback {
        void onEmbeddingReady(float[] embedding);
        void onError(String errorCode);   // يستخدم AiError constants
    }

    // ════════════════════════════════════════════════════════
    //  Init
    // ════════════════════════════════════════════════════════

    public static void init(Context ctx) {
        appContext = ctx.getApplicationContext();
        executor.execute(() -> {
            TFLiteFaceRecognizer rec = TFLiteFaceRecognizer.getInstance(appContext);
            if (rec.isAvailable()) {
                Log.i(TAG, "✅ TFLite جاهز — model=" + MODEL_VERSION
                    + " embeddingVersion=" + EMBEDDING_VERSION
                    + " threshold=" + MATCH_THRESHOLD);
            } else {
                AiError.logAll(TAG, AiError.MODEL_NOT_LOADED,
                    "FaceEmbeddingManager.init()", null);
            }
        });
    }

    // ════════════════════════════════════════════════════════
    //  extractEmbedding — async (رئيسي)
    // ════════════════════════════════════════════════════════

    /**
     * استخراج بصمة الوجه بشكل غير متزامن.
     *
     * Pipeline:
     *   bitmap → ML Kit Detection → Face Alignment → safeCrop
     *         → Quality Gate → TFLite → L2-normalize → callback
     *
     * إذا فشلت أي خطوة → onError(AiError.CODE) — لا embedding وهمي
     */
    public static void extractEmbedding(Bitmap bitmap, EmbeddingCallback callback) {
        if (bitmap == null) {
            AiError.log(TAG, AiError.NULL_BITMAP, "extractEmbedding: bitmap null");
            mainHandler.post(() -> callback.onError(AiError.NULL_BITMAP));
            return;
        }

        executor.execute(() -> {
            // 1. تحقق من TFLite
            TFLiteFaceRecognizer rec = TFLiteFaceRecognizer.getInstance(appContext);
            if (!rec.isAvailable()) {
                AiError.logAll(TAG, AiError.MODEL_NOT_LOADED,
                    "extractEmbedding: TFLite unavailable", null);
                mainHandler.post(() -> callback.onError(AiError.MODEL_NOT_LOADED));
                return;
            }

            try {
                // 2. كشف الوجه مع Landmarks
                FaceDetectorOptions opts = new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .build();
                FaceDetector detector = FaceDetection.getClient(opts);
                List<Face> faces = Tasks.await(
                    detector.process(InputImage.fromBitmap(bitmap, 0)));

                if (faces == null || faces.isEmpty()) {
                    AiError.log(TAG, AiError.FACE_NOT_FOUND, "لم يُكتشف وجه");
                    mainHandler.post(() -> callback.onError(AiError.FACE_NOT_FOUND));
                    return;
                }

                Face face = faces.get(0);

                // 3. Face Alignment (قبل الـ crop)
                Bitmap aligned = alignFace(bitmap, face);

                // 4. Crop الوجه
                Bitmap cropped = safeCrop(aligned, face.getBoundingBox());
                if (cropped == null) {
                    AiError.log(TAG, AiError.CROP_FAILED, "safeCrop returned null");
                    mainHandler.post(() -> callback.onError(AiError.CROP_FAILED));
                    return;
                }

                // 5. Quality Gate — لا تحفظ embedding من صورة سيئة
                ImageQualityEnhancer.QualityResult quality =
                    ImageQualityEnhancer.validateFull(cropped);
                if (!quality.isAcceptable) {
                    AiError.log(TAG, quality.errorCode,
                        "Quality Gate رفض الصورة: " + quality);
                    mainHandler.post(() -> callback.onError(quality.errorCode));
                    return;
                }

                // 6. TFLite → Embedding
                float[] emb = rec.recognize(cropped);
                if (emb == null) {
                    AiError.logAll(TAG, AiError.EMBEDDING_EXTRACTION_FAILED,
                        "TFLite.recognize() → null", null);
                    mainHandler.post(() -> callback.onError(AiError.EMBEDDING_EXTRACTION_FAILED));
                    return;
                }

                Log.d(TAG, "✅ embedding جاهز: dim=" + emb.length
                    + " blur=" + String.format("%.1f", quality.blurScore)
                    + " brightness=" + String.format("%.1f", quality.brightness));
                mainHandler.post(() -> callback.onEmbeddingReady(emb));

            } catch (Exception e) {
                AiError.logAll(TAG, AiError.EMBEDDING_EXTRACTION_FAILED,
                    "extractEmbedding exception: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError(AiError.EMBEDDING_EXTRACTION_FAILED));
            }
        });
    }

    // ════════════════════════════════════════════════════════
    //  extractEmbeddingSync — للاستخدام من Background Thread فقط
    // ════════════════════════════════════════════════════════

    /**
     * نسخة متزامنة — للاستخدام من Workers أو Threads فقط.
     * لا تستدعها من Main Thread.
     *
     * @return float[128] أو null إذا فشلت أي خطوة
     */
    public static float[] extractEmbeddingSync(Context ctx, Bitmap bitmap) {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("extractEmbeddingSync() on Main thread!");

        if (bitmap == null) return null;

        Context context = (ctx != null) ? ctx : appContext;
        TFLiteFaceRecognizer rec = TFLiteFaceRecognizer.getInstance(context);
        if (!rec.isAvailable()) {
            AiError.log(TAG, AiError.MODEL_NOT_LOADED, "extractEmbeddingSync");
            return null;
        }

        try {
            FaceDetectorOptions opts = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build();
            FaceDetector detector = FaceDetection.getClient(opts);
            List<Face> faces = Tasks.await(
                detector.process(InputImage.fromBitmap(bitmap, 0)));

            if (faces == null || faces.isEmpty()) {
                AiError.log(TAG, AiError.FACE_NOT_FOUND, "extractEmbeddingSync");
                return null;
            }

            Face   face    = faces.get(0);
            Bitmap aligned = alignFace(bitmap, face);
            Bitmap cropped = safeCrop(aligned, face.getBoundingBox());
            if (cropped == null) return null;

            // Quality Gate
            ImageQualityEnhancer.QualityResult quality =
                ImageQualityEnhancer.validateFull(cropped);
            if (!quality.isAcceptable) {
                AiError.log(TAG, quality.errorCode, "sync quality gate: " + quality);
                return null;
            }

            float[] emb = rec.recognize(cropped);
            if (emb != null)
                Log.d(TAG, "✅ sync embedding dim=" + emb.length);
            return emb;

        } catch (Exception e) {
            AiError.log(TAG, AiError.EMBEDDING_EXTRACTION_FAILED,
                "extractEmbeddingSync: " + e.getMessage());
            return null;
        }
    }

    // ════════════════════════════════════════════════════════
    //  Face Alignment — التحسين الأهم في المرحلة 2
    // ════════════════════════════════════════════════════════

    /**
     * محاذاة الوجه باستخدام مواضع العينين من ML Kit.
     *
     * الخطوات:
     * 1. احسب الزاوية بين العينين (atan2)
     * 2. أدر الصورة كاملة بالزاوية المعاكسة
     * 3. الـ crop سيحصل على وجه مستقيم دائماً
     *
     * إذا لم تُوجد Landmarks → يُرجع الصورة الأصلية بدون تغيير
     * (لا يُوقف العملية — فقط يُحذّر)
     */
    private static Bitmap alignFace(Bitmap bitmap, Face face) {
        try {
            FaceLandmark leftEyeLM  = face.getLandmark(FaceLandmark.LEFT_EYE);
            FaceLandmark rightEyeLM = face.getLandmark(FaceLandmark.RIGHT_EYE);

            if (leftEyeLM == null || rightEyeLM == null) {
                Log.w(TAG, "⚠️ Alignment: Landmarks غير متاحة — يُكمل بدون alignment");
                return bitmap;
            }

            PointF leftEye  = leftEyeLM.getPosition();
            PointF rightEye = rightEyeLM.getPosition();

            float dx    = rightEye.x - leftEye.x;
            float dy    = rightEye.y - leftEye.y;
            float angle = (float) Math.toDegrees(Math.atan2(dy, dx));

            // إذا الزاوية صغيرة جداً → لا داعي للتدوير (تحسين الأداء)
            if (Math.abs(angle) < 2.0f) {
                return bitmap;
            }

            Matrix matrix = new Matrix();
            matrix.postRotate(-angle, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);

            Bitmap aligned = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

            Log.d(TAG, "↻ Face alignment: angle=" + String.format("%.1f°", angle));
            return aligned;

        } catch (Exception e) {
            Log.w(TAG, "⚠️ Alignment فشل — يُكمل بدون تدوير: " + e.getMessage());
            return bitmap; // fallback آمن
        }
    }

    // ════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════

    private static Bitmap safeCrop(Bitmap src, Rect box) {
        try {
            int margin = (int)(box.width() * 0.20f);
            int l = Math.max(0,              box.left   - margin);
            int t = Math.max(0,              box.top    - margin);
            int r = Math.min(src.getWidth(), box.right  + margin);
            int b = Math.min(src.getHeight(),box.bottom + margin);
            int w = r - l, h = b - t;
            return (w > 10 && h > 10)
                ? Bitmap.createBitmap(src, l, t, w, h)
                : null;
        } catch (Exception e) {
            Log.e(TAG, "safeCrop error: " + e.getMessage());
            return null;
        }
    }

    // ════════════════════════════════════════════════════════
    //  Cosine Similarity
    // ════════════════════════════════════════════════════════

    public static float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0f;
        double dot = 0, nA = 0, nB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            nA  += a[i] * a[i];
            nB  += b[i] * b[i];
        }
        if (nA == 0 || nB == 0) return 0f;
        return (float)(dot / (Math.sqrt(nA) * Math.sqrt(nB)));
    }

    // ════════════════════════════════════════════════════════
    //  Embedding ↔ String (Firebase)
    // ════════════════════════════════════════════════════════

    public static float[] stringToEmbedding(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            String[] parts = s.split(",");
            if (parts.length < 32) {
                Log.w(TAG, "stringToEmbedding: dim=" + parts.length + " (يتوقع 128)");
                return null;
            }
            float[] emb = new float[parts.length];
            for (int i = 0; i < parts.length; i++)
                emb[i] = Float.parseFloat(parts[i].trim());
            return emb;
        } catch (Exception e) {
            Log.e(TAG, "stringToEmbedding error: " + e.getMessage());
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

    // ════════════════════════════════════════════════════════
    //  Age / Gender (للعرض فقط — لا يؤثر على المطابقة)
    // ════════════════════════════════════════════════════════

    public static int estimateAge(Context context, Bitmap bitmap) {
        if (bitmap == null) return 0;
        try {
            FaceDetectorOptions opts = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build();
            FaceDetector detector = FaceDetection.getClient(opts);
            List<Face> faces = Tasks.await(
                detector.process(InputImage.fromBitmap(bitmap, 0)));
            if (faces == null || faces.isEmpty()) return 0;
            return analyzeAgeFromFace(faces.get(0));
        } catch (Exception e) {
            Log.e(TAG, "estimateAge: " + e.getMessage());
            return 0;
        }
    }

    public static float[] estimateGenderConfidence(Bitmap bitmap) {
        if (bitmap == null) return new float[]{0.5f, 0.5f};
        try {
            FaceDetectorOptions opts = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build();
            FaceDetector detector = FaceDetection.getClient(opts);
            List<Face> faces = Tasks.await(
                detector.process(InputImage.fromBitmap(bitmap, 0)));
            if (faces == null || faces.isEmpty()) return new float[]{0.5f, 0.5f};
            return analyzeGenderFromFace(faces.get(0));
        } catch (Exception e) {
            return new float[]{0.5f, 0.5f};
        }
    }

    private static int analyzeAgeFromFace(Face face) {
        float ageScore = 25;
        android.graphics.Rect box = face.getBoundingBox();
        FaceLandmark leftEye  = face.getLandmark(FaceLandmark.LEFT_EYE);
        FaceLandmark rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE);
        if (leftEye != null && rightEye != null) {
            float eyeY  = (leftEye.getPosition().y + rightEye.getPosition().y) / 2;
            float ratio = (eyeY - box.top) / (float) box.height();
            if      (ratio < 0.38f) ageScore = 6;
            else if (ratio < 0.42f) ageScore = 12;
            else if (ratio < 0.46f) ageScore = 20;
            else                    ageScore = 35;
        }
        Float lo = face.getLeftEyeOpenProbability(), ro = face.getRightEyeOpenProbability();
        if (lo != null && ro != null) {
            float avg = (lo + ro) / 2;
            if (avg > 0.85f) ageScore -= 5;
            else if (avg < 0.5f) ageScore += 10;
        }
        Float smile = face.getSmilingProbability();
        if (smile != null && smile > 0.8f && ageScore > 15) ageScore -= 3;
        return Math.max(1, Math.min(80, Math.round(ageScore)));
    }

    private static float[] analyzeGenderFromFace(Face face) {
        android.graphics.Rect box = face.getBoundingBox();
        float gs = 0.5f;
        float ar = (float) box.width() / box.height();
        if (ar > 0.85f) gs += 0.1f; else if (ar < 0.75f) gs -= 0.1f;
        FaceLandmark le = face.getLandmark(FaceLandmark.LEFT_EYE);
        FaceLandmark re = face.getLandmark(FaceLandmark.RIGHT_EYE);
        if (le != null && re != null) {
            float ed  = Math.abs(le.getPosition().x - re.getPosition().x);
            float edr = ed / box.width();
            if (edr > 0.38f) gs += 0.08f; else if (edr < 0.32f) gs -= 0.08f;
        }
        gs = Math.max(0f, Math.min(1f, gs));
        if (gs > 0.6f)      return new float[]{gs, 1 - gs};
        else if (gs < 0.4f) return new float[]{1 - gs, gs};
        else                return new float[]{0.5f, 0.5f};
    }
}
