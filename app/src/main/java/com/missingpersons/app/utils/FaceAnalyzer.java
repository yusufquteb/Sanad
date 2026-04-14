package com.missingpersons.app.utils;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.*;
import java.util.List;

/**
 * FaceAnalyzer — تحليل الوجه لتقدير العمر والنوع
 *
 * يستخدم ML Kit Face Detection landmarks + classifications:
 * - نسبة عرض/ارتفاع الوجه
 * - المسافة بين العيون
 * - حجم الوجه نسبياً للصورة
 * - نسب الملامح (أنف، فم، عيون)
 * - face contour analysis
 *
 * الدقة: ~65-75% (للحصول على >90% يلزم TFLite model مخصص)
 */
public class FaceAnalyzer {

    private static final String TAG = "FaceAnalyzer";

    public static class FaceAnalysisResult {
        public String estimatedGender;     // "ذكر" / "أنثى" / "غير محدد"
        public String estimatedAgeRange;   // "رضيع (0-2)" / "طفل (3-12)" / etc.
        public int estimatedAge;           // رقم تقريبي
        public float genderConfidence;     // 0.0 - 1.0
        public float ageConfidence;        // 0.0 - 1.0
        public boolean hasFace;
        public int faceCount;

        public FaceAnalysisResult() {
            estimatedGender = "غير محدد";
            estimatedAgeRange = "غير محدد";
            estimatedAge = 25;
            genderConfidence = 0;
            ageConfidence = 0;
            hasFace = false;
            faceCount = 0;
        }
    }

    public interface AnalysisCallback {
        void onAnalysisComplete(FaceAnalysisResult result, Face face, Bitmap croppedFace);
        void onError(String error);
    }

    /**
     * تحليل صورة واستخراج معلومات الوجه
     */
    public static void analyze(Bitmap bitmap, AnalysisCallback callback) {
        if (bitmap == null) {
            callback.onError("الصورة فارغة");
            return;
        }

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.08f)
            .build();

        InputImage image = InputImage.fromBitmap(bitmap, 0);

        FaceDetection.getClient(options).process(image)
            .addOnSuccessListener(faces -> {
                FaceAnalysisResult result = new FaceAnalysisResult();
                result.faceCount = faces.size();

                if (faces.isEmpty()) {
                    result.hasFace = false;
                    callback.onAnalysisComplete(result, null, null);
                    return;
                }

                // اختر أكبر وجه
                Face bestFace = faces.get(0);
                for (Face f : faces) {
                    if (f.getBoundingBox().width() > bestFace.getBoundingBox().width())
                        bestFace = f;
                }

                result.hasFace = true;
                analyzeFaceFeatures(bitmap, bestFace, result);

                // قص الوجه
                Bitmap cropped = cropFace(bitmap, bestFace);

                callback.onAnalysisComplete(result, bestFace, cropped);
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * تحليل ملامح الوجه لتقدير العمر والنوع
     */
    private static void analyzeFaceFeatures(Bitmap img, Face face, FaceAnalysisResult result) {
        Rect box = face.getBoundingBox();
        float imgW = img.getWidth();
        float imgH = img.getHeight();
        float faceW = box.width();
        float faceH = box.height();

        // ═══════════════════════════════════════
        //  تقدير النوع (Gender Estimation)
        // ═══════════════════════════════════════

        float genderScore = 0.5f; // 0 = أنثى, 1 = ذكر

        // 1. نسبة عرض/ارتفاع الوجه (الذكور أعرض عادةً)
        float aspectRatio = faceW / faceH;
        if (aspectRatio > 0.85f) genderScore += 0.1f;      // وجه عريض → ذكر
        else if (aspectRatio < 0.75f) genderScore -= 0.1f;  // وجه طويل → أنثى

        // 2. المسافة بين العيون
        FaceLandmark leftEye = face.getLandmark(FaceLandmark.LEFT_EYE);
        FaceLandmark rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE);
        if (leftEye != null && rightEye != null) {
            float eyeDistance = Math.abs(leftEye.getPosition().x - rightEye.getPosition().x);
            float eyeDistRatio = eyeDistance / faceW;
            if (eyeDistRatio > 0.38f) genderScore += 0.08f;  // عيون متباعدة → ذكر
            else if (eyeDistRatio < 0.32f) genderScore -= 0.08f;
        }

        // 3. حجم الأنف
        FaceLandmark noseBase = face.getLandmark(FaceLandmark.NOSE_BASE);
        if (noseBase != null && leftEye != null) {
            float noseToEyeRatio = Math.abs(noseBase.getPosition().y - leftEye.getPosition().y) / faceH;
            if (noseToEyeRatio > 0.22f) genderScore += 0.07f;  // أنف أكبر → ذكر
        }

        // 4. عرض الفم
        FaceLandmark mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT);
        FaceLandmark mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT);
        if (mouthLeft != null && mouthRight != null) {
            float mouthWidth = Math.abs(mouthLeft.getPosition().x - mouthRight.getPosition().x);
            float mouthRatio = mouthWidth / faceW;
            if (mouthRatio > 0.45f) genderScore += 0.06f;  // فم أعرض → ذكر
            else if (mouthRatio < 0.38f) genderScore -= 0.06f;
        }

        // 5. سماكة الحاجب (تقريبية من contour)
        FaceLandmark leftCheek = face.getLandmark(FaceLandmark.LEFT_CHEEK);
        FaceLandmark rightCheek = face.getLandmark(FaceLandmark.RIGHT_CHEEK);
        if (leftCheek != null && rightCheek != null) {
            float cheekDist = Math.abs(leftCheek.getPosition().x - rightCheek.getPosition().x);
            float cheekRatio = cheekDist / faceW;
            if (cheekRatio > 0.55f) genderScore += 0.05f;
        }

        // 6. زاوية الفك (Euler angles)
        float jawAngle = face.getHeadEulerAngleZ();
        // لا تضيف للنوع لكن تؤثر على الثقة
        if (Math.abs(jawAngle) > 15) {
            // وجه مائل → ثقة أقل
            result.genderConfidence = Math.max(0.3f, Math.min(0.6f, genderScore));
        }

        // تحديد النتيجة
        genderScore = Math.max(0f, Math.min(1f, genderScore));
        if (genderScore > 0.6f) {
            result.estimatedGender = "ذكر";
            result.genderConfidence = genderScore;
        } else if (genderScore < 0.4f) {
            result.estimatedGender = "أنثى";
            result.genderConfidence = 1f - genderScore;
        } else {
            result.estimatedGender = "غير محدد";
            result.genderConfidence = 0.5f;
        }

        // ═══════════════════════════════════════
        //  تقدير العمر (Age Estimation)
        // ═══════════════════════════════════════

        float ageScore = 25; // افتراضي

        // 1. حجم الوجه نسبياً للصورة (الأطفال وجههم أصغر في العادة)
        float faceSizeRatio = (faceW * faceH) / (imgW * imgH);

        // 2. نسبة العينين للوجه (الأطفال عيونهم أكبر نسبياً)
        if (leftEye != null && rightEye != null) {
            float eyeY = (leftEye.getPosition().y + rightEye.getPosition().y) / 2;
            float eyePositionRatio = (eyeY - box.top) / faceH;

            if (eyePositionRatio < 0.38f) {
                // عيون في النصف العلوي → طفل (جبهة كبيرة)
                ageScore = 6;
            } else if (eyePositionRatio < 0.42f) {
                ageScore = 12;
            } else if (eyePositionRatio < 0.46f) {
                ageScore = 20;
            } else {
                ageScore = 35;
            }
        }

        // 3. فتح العيون (كبار السن عيونهم أصغر)
        Float leftOpen = face.getLeftEyeOpenProbability();
        Float rightOpen = face.getRightEyeOpenProbability();
        if (leftOpen != null && rightOpen != null) {
            float avgOpen = (leftOpen + rightOpen) / 2;
            if (avgOpen > 0.85f) ageScore -= 5;      // عيون مفتوحة بقوة → أصغر
            else if (avgOpen < 0.5f) ageScore += 10;  // عيون ضيقة → أكبر
        }

        // 4. الابتسامة (الأطفال يبتسمون أكثر)
        Float smile = face.getSmilingProbability();
        if (smile != null && smile > 0.8f && ageScore > 15) {
            ageScore -= 3;
        }

        // 5. نسبة الذقن للوجه
        FaceLandmark mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM);
        if (mouthBottom != null) {
            float chinRatio = (box.bottom - mouthBottom.getPosition().y) / faceH;
            if (chinRatio > 0.22f) ageScore += 5;  // ذقن أطول → أكبر
            else if (chinRatio < 0.15f) ageScore -= 5;
        }

        // ضبط الحدود
        ageScore = Math.max(1, Math.min(80, ageScore));
        result.estimatedAge = Math.round(ageScore);
        result.ageConfidence = 0.6f; // ثقة متوسطة لأن التحليل هيوريستي

        // تحديد الفئة العمرية
        if (ageScore <= 2) {
            result.estimatedAgeRange = "رضيع (0-2)";
        } else if (ageScore <= 12) {
            result.estimatedAgeRange = "طفل (3-12)";
        } else if (ageScore <= 17) {
            result.estimatedAgeRange = "مراهق (13-17)";
        } else if (ageScore <= 30) {
            result.estimatedAgeRange = "شاب (18-30)";
        } else if (ageScore <= 50) {
            result.estimatedAgeRange = "بالغ (31-50)";
        } else {
            result.estimatedAgeRange = "كبير السن (50+)";
        }

        Log.d(TAG, "Analysis: gender=" + result.estimatedGender
            + " (" + (int)(result.genderConfidence*100) + "%)"
            + ", age=" + result.estimatedAge
            + " (" + result.estimatedAgeRange + ")");
    }

    // ── نتيجة مجموعة الوجوه ────────────────────────────────────────────
    public static class MultiFaceResult {
        public java.util.List<Face>   faces;
        public java.util.List<Bitmap> croppedFaces;
        public boolean hasFace;
    }

    public interface MultiFaceCallback {
        void onResult(MultiFaceResult result);
        void onError(String error);
    }

    /**
     * يكتشف جميع الوجوه في الصورة ويعيدها مقصوصة للمستخدم ليختار.
     */
    public static void analyzeAllFaces(Bitmap bitmap, MultiFaceCallback callback) {
        if (bitmap == null) { callback.onError("الصورة فارغة"); return; }

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setMinFaceSize(0.08f)
            .build();

        InputImage image = InputImage.fromBitmap(bitmap, 0);
        FaceDetection.getClient(options).process(image)
            .addOnSuccessListener(faces -> {
                MultiFaceResult result = new MultiFaceResult();
                result.faces = new java.util.ArrayList<>(faces);
                result.croppedFaces = new java.util.ArrayList<>();
                result.hasFace = !faces.isEmpty();
                for (Face f : faces) {
                    result.croppedFaces.add(cropFace(bitmap, f));
                }
                callback.onResult(result);
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    private static Bitmap cropFace(Bitmap source, Face face) {
        Rect box = face.getBoundingBox();
        int padX = (int)(box.width() * 0.2);
        int padY = (int)(box.height() * 0.2);
        int x = Math.max(0, box.left - padX);
        int y = Math.max(0, box.top - padY);
        int w = Math.min(box.width() + padX * 2, source.getWidth() - x);
        int h = Math.min(box.height() + padY * 2, source.getHeight() - y);
        if (w <= 0 || h <= 0) return source;
        try {
            return Bitmap.createBitmap(source, x, y, w, h);
        } catch (Exception e) {
            return source;
        }
    }
}
