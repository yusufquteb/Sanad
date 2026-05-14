package com.missingpersons.app.workers;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.FirebaseDatabase;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.missingpersons.app.utils.AdaFaceRecognizer;
import com.missingpersons.app.utils.AiError;
import com.missingpersons.app.utils.CrossMatchManager;
import com.missingpersons.app.utils.EmbeddingCleanupUtil;
import com.missingpersons.app.utils.EmbeddingStabilityValidator;
import com.missingpersons.app.utils.FaceEmbeddingManager;
import com.missingpersons.app.utils.FaceQualityAnalyzer;
import com.missingpersons.app.utils.FivePointAligner;
import com.missingpersons.app.utils.ImagePreprocessor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FaceEmbeddingWorker — معالجة الوجوه في الخلفية عبر WorkManager.
 *
 * Pipeline:
 *   Load → Detect → Align → Quality → Stability → Embed → Save → CrossMatch
 *
 * يُستدعى من ViewModel/Repository بعد اختيار الصورة.
 * يُعيد Result.retry() عند أخطاء مؤقتة، و Result.failure() عند أخطاء دائمة.
 */
public class FaceEmbeddingWorker extends Worker {

    private static final String TAG = "FaceEmbeddingWorker";

    public static final String KEY_IMAGE_URI    = "image_uri";
    public static final String KEY_PERSON_ID    = "person_id";
    public static final String KEY_REPORTER_UID = "reporter_uid";
    public static final String KEY_PERSON_NAME  = "person_name";
    public static final String KEY_IMAGE_TYPE   = "image_type";
    public static final String KEY_NODE         = "firebase_node";  // "reports" | "found_persons"

    public static final String OUT_FAILURE_REASON = "failure_reason";
    public static final String OUT_EMBEDDING_DIM  = "embedding_dim";

    public FaceEmbeddingWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String imageUri    = getInputData().getString(KEY_IMAGE_URI);
        String personId    = getInputData().getString(KEY_PERSON_ID);
        String reporterUid = getInputData().getString(KEY_REPORTER_UID);
        String personName  = getInputData().getString(KEY_PERSON_NAME);
        String imageType   = getInputData().getString(KEY_IMAGE_TYPE);
        String node        = getInputData().getString(KEY_NODE);

        if (imageUri == null || personId == null) {
            return Result.failure(buildOutput("missing_required_input"));
        }
        if (node == null) node = "reports";
        if (imageType == null) imageType = "frontal_clear";

        Context ctx = getApplicationContext();

        try {
            // 1. Load
            Bitmap bitmap = ImagePreprocessor.preprocessFromUri(ctx, Uri.parse(imageUri));
            if (bitmap == null) {
                return Result.failure(buildOutput(AiError.NULL_BITMAP));
            }

            // 2. Detect (ACCURATE + LANDMARK_MODE_ALL)
            FaceDetectorOptions opts = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build();
            FaceDetector detector = FaceDetection.getClient(opts);
            List<Face> faces = Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)));

            if (faces == null || faces.isEmpty()) {
                return Result.failure(buildOutput(AiError.FACE_NOT_FOUND));
            }
            Face face = faces.get(0);

            // 3. Align (5-point)
            Bitmap aligned = new FivePointAligner().align(bitmap, face);

            // 4. Quality Gate
            FaceQualityAnalyzer.QualityResult quality =
                new FaceQualityAnalyzer().analyze(aligned, face);

            if (quality.getTier() == FaceQualityAnalyzer.QualityResult.QualityTier.LOW) {
                Log.w(TAG, "جودة منخفضة: score=" + quality.score);
                return Result.failure(buildOutput("low_quality_" + String.format("%.2f", quality.score)));
            }

            // 5. AdaFace recognizer
            AdaFaceRecognizer recognizer = AdaFaceRecognizer.getInstance(ctx);
            if (!recognizer.isAvailable()) {
                Log.w(TAG, "AdaFace غير متاح — سيُعاد المحاولة");
                return Result.retry();
            }

            // 6. Stability
            if (!new EmbeddingStabilityValidator(recognizer).isStable(aligned)) {
                return Result.failure(buildOutput("unstable_embedding"));
            }

            // 7. Embed
            float[] embedding = recognizer.embed(aligned);
            if (embedding == null) {
                return Result.retry();
            }

            // 8. Save to Firebase
            saveEmbeddingToFirebase(ctx, node, personId, embedding, quality, imageType);

            // 9. CrossMatch
            String embString = FaceEmbeddingManager.embeddingToString(embedding);
            if ("found_persons".equals(node)) {
                CrossMatchManager.matchFoundPersonWithReports(
                    personId,
                    reporterUid != null ? reporterUid : "",
                    embString);
            } else {
                CrossMatchManager.matchReportWithFoundPersons(
                    personId,
                    reporterUid != null ? reporterUid : "",
                    personName  != null ? personName  : "",
                    embString);
            }

            Log.i(TAG, "✅ FaceEmbeddingWorker: تم حفظ embedding للشخص=" + personId
                + " dim=" + embedding.length + " quality=" + quality.score);

            Data output = new Data.Builder()
                .putString(OUT_EMBEDDING_DIM, String.valueOf(embedding.length))
                .build();
            return Result.success(output);

        } catch (Exception e) {
            Log.e(TAG, "FaceEmbeddingWorker: " + e.getMessage(), e);
            AiError.logAll(TAG, AiError.EMBEDDING_EXTRACTION_FAILED,
                "worker exception: " + e.getMessage(), e);
            return Result.retry();
        }
    }

    private void saveEmbeddingToFirebase(
            Context ctx, String node, String personId,
            float[] embedding, FaceQualityAnalyzer.QualityResult quality, String imageType) {

        Map<String, Object> embData = new HashMap<>();
        embData.put("vector",               FaceEmbeddingManager.embeddingToString(embedding));
        embData.put("qualityScore",         quality.score);
        embData.put("imageType",            imageType);
        embData.put(EmbeddingCleanupUtil.FIELD_MODEL_VERSION,       FaceEmbeddingManager.MODEL_VERSION);
        embData.put(EmbeddingCleanupUtil.FIELD_PREPROCESSING_VER,   FaceEmbeddingManager.PREPROCESSING_VERSION);
        embData.put("timestamp",            System.currentTimeMillis());

        Map<String, Object> updates = new HashMap<>();
        updates.put(EmbeddingCleanupUtil.FIELD_EMBEDDING_VERSION, FaceEmbeddingManager.EMBEDDING_VERSION);
        updates.put("lastUpdated", System.currentTimeMillis());

        FirebaseDatabase.getInstance()
            .getReference(node)
            .child(personId)
            .updateChildren(updates);

        FirebaseDatabase.getInstance()
            .getReference(node)
            .child(personId)
            .child("embeddings")
            .push()
            .setValue(embData);
    }

    private Data buildOutput(String reason) {
        return new Data.Builder()
            .putString(OUT_FAILURE_REASON, reason)
            .build();
    }
}
