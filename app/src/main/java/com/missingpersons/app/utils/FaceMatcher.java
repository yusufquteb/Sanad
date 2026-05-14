package com.missingpersons.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * FaceMatcher — مقارنة الوجوه باستخدام TFLite MobileFaceNet
 *
 * [إصلاح جذري]
 *
 * المشاكل التي تم إصلاحها:
 *
 * 🔴 مشكلة — Pixel Similarity Fallback:
 *    كان الكود يتراجع إلى مقارنة البكسل عند فشل TFLite.
 *    مقارنة البكسل تعطي نتائج عشوائية تماماً:
 *    - صورتان لنفس الشخص بإضاءة مختلفة → 55% تشابه
 *    - صورتان لشخصين مختلفين بخلفية مشابهة → 72% تشابه!
 *    الحل: إذا فشل TFLite → onResult(false, 0, رسالة خطأ) فقط.
 *
 * 🔴 مشكلة — compareFacesSync() يستخدم Pixel دائماً:
 *    الحل: يُعيد 0f دائماً إذا لم يكن TFLite متاحاً.
 *    الاستدعاء المتزامن الحقيقي → استخدم FaceEmbeddingManager.extractEmbeddingSync()
 */
public class FaceMatcher {

    private static final String TAG = "FaceMatcher";
    private TFLiteFaceRecognizer tfliteFaceRecognizer;
    private final Context context;

    public interface MatchCallback {
        void onResult(boolean isMatch, float confidence, String message);
    }

    public FaceMatcher(Context context) {
        this.context = context;
        this.tfliteFaceRecognizer = TFLiteFaceRecognizer.getInstance(context);
    }

    /**
     * مقارنة وجهين باستخدام TFLite فقط.
     *
     * الخطوات:
     * 1. ML Kit يكتشف ويقص الوجه من كل صورة
     * 2. TFLite يستخرج embedding (128 float) لكل وجه
     * 3. Cosine Similarity بين الـ embedding-ين
     * 4. مقارنة مع MATCH_THRESHOLD (0.82f)
     *
     * إذا فشل أي خطوة → onResult(false, 0, رسالة الخطأ)
     * لا pixel fallback، لا نتائج وهمية.
     */
    public void compareFaces(Bitmap bitmap1, Bitmap bitmap2, MatchCallback callback) {
        if (bitmap1 == null || bitmap2 == null) {
            new Handler(Looper.getMainLooper()).post(() ->
                callback.onResult(false, 0f, "خطأ: صورة فارغة"));
            return;
        }

        if (tfliteFaceRecognizer == null || !tfliteFaceRecognizer.isAvailable()) {
            Log.e(TAG, "❌ TFLite غير متاح — لا يمكن إجراء مقارنة موثوقة");
            new Handler(Looper.getMainLooper()).post(() ->
                callback.onResult(false, 0f,
                    "نموذج التعرف على الوجوه غير متاح — لا يمكن المقارنة"));
            return;
        }

        // استخدام ML Kit لاكتشاف وقص الوجوه
        FaceAnalyzer.analyzeAllFaces(bitmap1, new FaceAnalyzer.MultiFaceCallback() {
            @Override
            public void onResult(FaceAnalyzer.MultiFaceResult result1) {
                if (!result1.hasFace || result1.croppedFaces.isEmpty()) {
                    Log.w(TAG, "لم يُكتشف وجه في الصورة الأولى");
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onResult(false, 0f, "لم يُكتشف وجه في الصورة الأولى"));
                    return;
                }

                Bitmap croppedFace1 = result1.croppedFaces.get(0);

                FaceAnalyzer.analyzeAllFaces(bitmap2, new FaceAnalyzer.MultiFaceCallback() {
                    @Override
                    public void onResult(FaceAnalyzer.MultiFaceResult result2) {
                        if (!result2.hasFace || result2.croppedFaces.isEmpty()) {
                            Log.w(TAG, "لم يُكتشف وجه في الصورة الثانية");
                            new Handler(Looper.getMainLooper()).post(() ->
                                callback.onResult(false, 0f, "لم يُكتشف وجه في الصورة الثانية"));
                            return;
                        }

                        Bitmap croppedFace2 = result2.croppedFaces.get(0);

                        float[] embedding1 = tfliteFaceRecognizer.recognize(croppedFace1);
                        float[] embedding2 = tfliteFaceRecognizer.recognize(croppedFace2);

                        if (embedding1 == null || embedding2 == null) {
                            Log.e(TAG, "❌ فشل استخراج embedding من TFLite");
                            new Handler(Looper.getMainLooper()).post(() ->
                                callback.onResult(false, 0f,
                                    "فشل استخراج بصمة الوجه — حاول بصورة أوضح"));
                            return;
                        }

                        float similarity = TFLiteFaceRecognizer.cosineSimilarity(embedding1, embedding2);
                        boolean isMatch = similarity >= TFLiteFaceRecognizer.MATCH_THRESHOLD;

                        Log.d(TAG, "similarity=" + String.format("%.3f", similarity)
                                + " threshold=" + TFLiteFaceRecognizer.MATCH_THRESHOLD
                                + " isMatch=" + isMatch);

                        String message = isMatch
                                ? "✅ تطابق بنسبة " + (int)(similarity * 100) + "%"
                                : "❌ لا تطابق — نسبة التشابه " + (int)(similarity * 100) + "% (الحد الأدنى 82%)";

                        new Handler(Looper.getMainLooper()).post(() ->
                                callback.onResult(isMatch, similarity, message));
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "ML Kit error (bitmap2): " + error);
                        new Handler(Looper.getMainLooper()).post(() ->
                            callback.onResult(false, 0f, "خطأ في تحليل الصورة الثانية: " + error));
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "ML Kit error (bitmap1): " + error);
                new Handler(Looper.getMainLooper()).post(() ->
                    callback.onResult(false, 0f, "خطأ في تحليل الصورة الأولى: " + error));
            }
        });
    }

    /**
     * مقارنة متزامنة — تُعيد 0f إذا لم يكن TFLite متاحاً.
     *
     * ⚠️ يجب استدعاؤها من Thread غير الـ Main (مثلاً WorkManager).
     * للحصول على نتيجة حقيقية، استخدم:
     *   float[] emb1 = FaceEmbeddingManager.extractEmbeddingSync(ctx, bitmap1);
     *   float[] emb2 = FaceEmbeddingManager.extractEmbeddingSync(ctx, bitmap2);
     *   float sim = FaceEmbeddingManager.cosineSimilarity(emb1, emb2);
     */
    public float compareFacesSync(Bitmap bitmap1, Bitmap bitmap2) {
        // لا pixel fallback — نُعيد 0f إذا لم يكن TFLite متاحاً
        if (tfliteFaceRecognizer == null || !tfliteFaceRecognizer.isAvailable()) {
            Log.e(TAG, "compareFacesSync: TFLite غير متاح");
            return 0f;
        }
        if (bitmap1 == null || bitmap2 == null) return 0f;

        float[] emb1 = tfliteFaceRecognizer.recognize(bitmap1);
        float[] emb2 = tfliteFaceRecognizer.recognize(bitmap2);
        if (emb1 == null || emb2 == null) return 0f;

        return TFLiteFaceRecognizer.cosineSimilarity(emb1, emb2);
    }

    /**
     * هل نسبة التشابه كافية للتطابق؟
     * يستخدم نفس العتبة كـ TFLiteFaceRecognizer (0.82f)
     */
    public boolean isMatch(float similarity) {
        return similarity >= TFLiteFaceRecognizer.MATCH_THRESHOLD;
    }

    public void release() {
        if (tfliteFaceRecognizer != null) {
            tfliteFaceRecognizer.close();
        }
    }
}
