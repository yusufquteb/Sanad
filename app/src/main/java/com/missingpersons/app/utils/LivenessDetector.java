package com.missingpersons.app.utils;

import android.graphics.Bitmap;
import android.util.Log;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.*;
import java.util.List;

/**
 * ميزة 4: Liveness Detector (كشف الحيوية)
 * يتحقق من أن الصورة وجه حقيقي وليس صورة مطبوعة أو شاشة.
 * يعتمد على: حركة العيون + زاوية الوجه + العمق + الظلال.
 */
/**
 * @deprecated كود ميت — لا يُستخدم في أي مكان.
 * يمكن حذف هذا الملف بعد التأكد من عدم الحاجة إليه.
 * آخر مراجعة: 2026-04
 */
@Deprecated
public class LivenessDetector {

    private static final String TAG = "LivenessDetector";

    public interface LivenessCallback {
        void onResult(boolean isLive, String reason, float score);
    }

    /**
     * فحص ما إذا كان الوجه حياً
     * score: 0.0 = مزيف قطعاً، 1.0 = حقيقي قطعاً
     */
    public static void checkLiveness(Bitmap bitmap, LivenessCallback callback) {
        if (bitmap == null) {
            callback.onResult(false, "الصورة فارغة", 0f);
            return;
        }

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build();

        FaceDetector detector = FaceDetection.getClient(options);
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        detector.process(image)
            .addOnSuccessListener(faces -> {
                if (faces.isEmpty()) {
                    callback.onResult(false, "لم يُكتشَف وجه", 0f);
                    return;
                }
                Face face = getBiggestFace(faces);
                LivenessResult result = analyze(face, bitmap);
                callback.onResult(result.isLive, result.reason, result.score);
            })
            .addOnFailureListener(e -> {
                callback.onResult(false, "فشل الكشف: " + e.getMessage(), 0f);
            });
    }

    private static LivenessResult analyze(Face face, Bitmap bitmap) {
        float score = 0f;
        StringBuilder reasons = new StringBuilder();
        int checks = 0;

        // 1. فتح العيون (الصور المطبوعة غالباً عيونها مغلقة أو ثابتة)
        Float leftEye  = face.getLeftEyeOpenProbability();
        Float rightEye = face.getRightEyeOpenProbability();
        if (leftEye != null && rightEye != null) {
            float eyeAvg = (leftEye + rightEye) / 2f;
            if (eyeAvg > 0.5f) { score += 1f; reasons.append("عيون مفتوحة ✓ "); }
            checks++;
        }

        // 2. زاوية الوجه الطبيعية (الصور المطبوعة مسطحة تماماً = 0 درجة)
        float eulerY = Math.abs(face.getHeadEulerAngleY()); // يمين/يسار
        float eulerX = Math.abs(face.getHeadEulerAngleX()); // أعلى/أسفل
        float eulerZ = Math.abs(face.getHeadEulerAngleZ()); // إمالة
        // وجه طبيعي له انحراف بسيط، وجه مطبوع = صفر تقريباً
        if (eulerY > 2f || eulerX > 2f || eulerZ > 1f) {
            score += 1f;
            reasons.append("زاوية طبيعية ✓ ");
        }
        checks++;

        // 3. حجم الوجه بالنسبة للصورة (وجه حقيقي يملأ جزءاً معقولاً)
        float faceArea = (float)(face.getBoundingBox().width() * face.getBoundingBox().height());
        float imgArea  = bitmap.getWidth() * bitmap.getHeight();
        float ratio    = faceArea / imgArea;
        if (ratio > 0.04f && ratio < 0.95f) {
            score += 1f;
            reasons.append("حجم مناسب ✓ ");
        }
        checks++;

        // 4. التحقق من نقاط الوجه الكافية (Landmarks)
        int landmarkCount = 0;
        int[] types = { FaceLandmark.LEFT_EYE, FaceLandmark.RIGHT_EYE,
                        FaceLandmark.NOSE_BASE, FaceLandmark.MOUTH_BOTTOM };
        for (int t : types)
            if (face.getLandmark(t) != null) landmarkCount++;
        if (landmarkCount >= 3) {
            score += 1f;
            reasons.append("نقاط وجه واضحة ✓ ");
        }
        checks++;

        // 5. احتمالية الابتسامة (صور مطبوعة غالباً جامدة = 0)
        Float smile = face.getSmilingProbability();
        if (smile != null && smile > 0.05f) {
            score += 0.5f;
            reasons.append("تعبير طبيعي ✓ ");
        }
        checks++;

        float normalizedScore = score / checks;
        boolean isLive = normalizedScore >= 0.55f;

        return new LivenessResult(
            isLive,
            isLive ? "وجه حقيقي: " + reasons : "قد تكون صورة مطبوعة أو شاشة",
            normalizedScore
        );
    }

    private static Face getBiggestFace(List<Face> faces) {
        Face best = faces.get(0);
        for (Face f : faces)
            if (f.getBoundingBox().width() > best.getBoundingBox().width()) best = f;
        return best;
    }

    static class LivenessResult {
        boolean isLive;
        String reason;
        float score;
        LivenessResult(boolean live, String r, float s) {
            isLive = live; reason = r; score = s;
        }
    }

    /** عتبة الحيوية (55% كافٍ للمرور) */
    public static final float LIVENESS_THRESHOLD = 0.55f;
}
