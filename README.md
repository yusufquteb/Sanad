# SANAD AI Engine V2.1 — المرجع النهائي الشامل

> **مشروع:** سند — نظام العثور على المفقودين  
> **Package:** `com.missingpersons.app`  
> **الإصدار:** V2.1 — Production-Ready Reference (النسخة النهائية)  
> **اللغة:** Java | Android | minSdk 24 / targetSdk 35  
> **آخر تحديث:** مايو 2026  
> **حالة المرحلة الأولى:** ✅ مكتملة  
> **حالة المرحلة الثانية:** ✅ مكتملة

---

## المبدأ الأساسي الذي يحكم كل قرار

> هدف النظام ليس إثبات تقنية، بل **إيجاد إنسان مفقود**.  
> كل قرار هندسي يُحكَم عليه بسؤال واحد فقط:  
> **هل يزيد احتمالية العثور على الشخص الحقيقي؟**

---

## فهرس المحتويات

1. [هوية المشروع والحالة الحالية](#١-هوية-المشروع-والحالة-الحالية)
2. [القواعد الثابتة التي لا تُكسر أبداً](#٢-القواعد-الثابتة-التي-لا-تُكسر-أبداً)
3. [System Prompt الكامل للـ AI Agent](#٣-system-prompt-الكامل)
4. [الـ AI Pipeline — الحالي والمستهدف](#٤-الـ-ai-pipeline)
5. [V2 — المكونات الجديدة بالكود الكامل](#٥-v2--المكونات-الجديدة)
6. [WorkManager Pipeline — الأولوية القصوى](#٦-workmanager-pipeline)
7. [Firebase Schema المحدّث](#٧-firebase-schema-المحدّث)
8. [التوقيعات الحقيقية للدوال الموجودة](#٨-التوقيعات-الحقيقية-للدوال)
9. [خارطة التطوير — 3 مراحل](#٩-خارطة-التطوير)
10. [استراتيجية الاختبار](#١٠-استراتيجية-الاختبار)
11. [الأخطاء الحالية المعروفة](#١١-الأخطاء-الحالية-المعروفة)
12. [قرارات هندسية ثابتة ومؤجلة](#١٢-قرارات-هندسية-ثابتة-ومؤجلة)
13. [مقاييس النجاح](#١٣-مقاييس-النجاح)

---

## ١. هوية المشروع والحالة الحالية

### بيانات المشروع

| الحقل | القيمة |
|-------|--------|
| الاسم | سند (Sanad) |
| النوع | تطبيق Android للإبلاغ عن المفقودين |
| اللغة | Java |
| Package | `com.missingpersons.app` |
| الإصدار | 2.0.0 (versionCode 10) |
| minSdk | 24 |
| targetSdk | 35 |
| **النموذج الحالي** | `adaface_ir18_112.tflite` (44.6MB — الحقيقي) |
| **النموذج القديم** | `mobilefacenet.tflite` (5.2MB — legacy) |
| Embedding Dim | 512-dim `float[]` (auto-detected) |
| خوارزمية المقارنة | Cosine Similarity |
| Threshold الحالي | `0.82f` (ثابت) |
| Threshold المستهدف | Dynamic (0.72 – 0.85) |
| قاعدة البيانات | Firebase Realtime DB |
| الجوهر | Face Matching Engine — on-device — offline-first |

### المكتبات الأساسية

```gradle
// Firebase
firebase-auth, firebase-database, firebase-storage
firebase-messaging, firebase-crashlytics, firebase-analytics

// AI — الحالية
implementation 'com.google.android.gms:play-services-mlkit-face-detection:16.1.7'
implementation 'org.tensorflow:tensorflow-lite:2.16.1'

// AI — مضافة في V2
implementation 'org.tensorflow:tensorflow-lite-support:0.4.4'
implementation 'com.google.mediapipe:tasks-vision:0.10.14'

// أخرى
Room DB, WorkManager, OSMDroid
Coil, Lottie, AdMob
```

### ملفات AI الأساسية الموجودة

| الملف | الوظيفة | الحالة |
|-------|---------|--------|
| `TFLiteFaceRecognizer.java` | تشغيل النموذج + cosine similarity | ✅ موجود (V1 — يعمل مع 128-dim) |
| `FaceEmbeddingManager.java` | استخراج embedding كامل مع versioning | ✅ محدّث — `EMBEDDING_VERSION=3` |
| `CrossMatchManager.java` | المطابقة ثنائية الاتجاه | ✅ يحتاج دعم multi-embedding (M2) |
| `ImagePreprocessor.java` | تجهيز الصورة + load من Uri/File | ✅ محدّث — `preprocessFromUri()` مضافة |
| `ImageQualityEnhancer.java` | Quality Gate (V1 Boolean) | ✅ موجود — يُكمل للتوافق مع V1 |
| `FaceQualityAnalyzer.java` | Quality Gate (V2 float score) | ✅ جديد — composite score 0.0–1.0 |
| `AdaFaceRecognizer.java` | AdaFace IR18 TFLite — 512-dim | ✅ جديد |
| `FivePointAligner.java` | 5-point Similarity Transform | ✅ جديد |
| `EmbeddingStabilityValidator.java` | فحص ثبات الـ embedding | ✅ جديد |
| `FaceEmbeddingWorker.java` | WorkManager pipeline كامل | ✅ جديد |
| `FaceAnalyzer.java` | ML Kit wrapper + landmarks | ✅ موجود |
| `AiError.java` | Error taxonomy | ✅ موجود |

---

## ٢. القواعد الثابتة التي لا تُكسر أبداً

```
❌ NEVER generate fake embeddings
❌ NEVER use Math.random() anywhere in AI pipeline
❌ NEVER change threshold blindly — يتطلب benchmark على بيانات مصرية حقيقية
❌ NEVER save embeddings from low-quality images (qualityScore < 0.45)
❌ NEVER break offline-first behavior
❌ NEVER add paid cloud face recognition APIs كنظام أساسي
❌ NEVER remove L2 normalization
❌ NEVER change embedding dimensions without migration handling
❌ NEVER modify preprocessing without version increment
❌ NEVER send reports without valid embeddings unless explicit fallback exists
❌ NEVER run more than one TFLite model simultaneously on mid-range devices
✅ Matching logic MUST remain deterministic and explainable
✅ Human Review Queue MUST exist for borderline cases
✅ Every AI decision MUST be traceable and logged
```

---

## ٣. System Prompt الكامل

```
ROLE
You are a senior Android AI engineer working on a production-grade Android
application called "Sanad" — a missing persons recovery system.

PROJECT IDENTITY
Project Name: Sanad
Package: com.missingpersons.app
Language: Java
Platform: Android Studio
minSdk 24 / targetSdk 35

THE REAL GOAL
This is a human-critical identity recovery system, not an AI demo.
Every engineering decision must answer: "Does this increase the probability
of finding a real missing person?"

THE REAL CORE
AI-powered face similarity and cross-matching engine running fully
on-device using TensorFlow Lite — AdaFace IR18 model — offline-first.

AI ENGINE V2 — ACTIVE ARCHITECTURE
- Model: AdaFace IR18 TFLite (512-dim embeddings)
- Alignment: 5-Point Similarity Transform (MediaPipe landmarks)
- Quality: Float score 0.0–1.0 (blur 30% + brightness 20% + size 20% + pose 15% + occlusion 15%)
- Identity: Multi-embedding per person (up to 5 embeddings)
- Threshold: Dynamic (0.72–0.85 based on quality)
- Matching: Top-K candidate ranking, not binary match/no-match
- Pipeline: WorkManager — not inside Activities
- Cloud: CompreFace self-hosted as fallback only

ABSOLUTE RULES
- NEVER generate fake embeddings
- NEVER use Math.random() in AI pipeline
- NEVER change threshold without benchmark on real Egyptian data
- NEVER save embedding with qualityScore < 0.45
- NEVER break offline-first behavior
- NEVER add paid cloud APIs as primary system
- NEVER remove L2 normalization
- NEVER change embedding dimensions without migration
- NEVER modify preprocessing without version increment
- NEVER run 2+ TFLite models simultaneously
- Matching MUST remain deterministic and explainable

BEFORE CODING
1. Read the actual source files first
2. Verify exact method signatures before calling them
3. Verify XML IDs exist before using them in Java
4. Explain root cause
5. Explain safest implementation strategy
6. Identify possible regressions

KNOWN METHOD SIGNATURES — DO NOT CHANGE
CrossMatchManager.matchReportWithFoundPersons(String reportId, String reporterUid, String personName, String embedding)
CrossMatchManager.matchFoundPersonWithReports(String foundId, String finderUid, String embedding)
CrossMatchManager.matchSightingWithReports(String sightingId, String sighterUid, String embedding)
ImagePreprocessor.preprocessBitmap(Bitmap bitmap)   ← الصحيحة
ImagePreprocessor — processForFaceDetection() لا وجود لها ← خطأ سابق
FaceEmbeddingManager.extractEmbedding(Bitmap, EmbeddingCallback)
FaceEmbeddingManager.extractEmbeddingSync(Context, Bitmap)
```

---

## ٤. الـ AI Pipeline

### الحالي (V1 — بعد المرحلة 1)

```
صورة مُدخلة
  → ImagePreprocessor.preprocessBitmap()
  → ML Kit FaceDetection (ACCURATE + LANDMARK_MODE_ALL)
  → alignFace() — LEFT_EYE + RIGHT_EYE → atan2 → postRotate(-angle)
  → safeCrop(margin 20%)
  → ImageQualityEnhancer.validateFull()
      ├── face null?          → NULL_BITMAP
      ├── face < 60×60px?     → FACE_TOO_SMALL
      ├── brightness 25-235?  → LOW_QUALITY_IMAGE
      └── Laplacian ≥ 50?     → IMAGE_TOO_BLURRY
  → MobileFaceNet TFLite (112×112, normalize /128f -127.5f)
  → float[128] L2-normalized
  → embeddingToString() → Firebase
```

### المستهدف (V2)

```
صورة مُدخلة
  → [A] ImagePreprocessor.preprocessBitmap()       ← لا تغيير
  → [B] ML Kit FaceDetection                       ← لا تغيير
  → [C] FivePointAligner.align()                   ← جديد: MediaPipe 5-point
  → [D] FaceQualityAnalyzer.analyze()              ← جديد: float score
      ├── score ≥ 0.70 → قبول مباشر
      ├── score 0.45–0.69 → قبول مع threshold مخفض
      └── score < 0.45 → رفض + طلب صورة أوضح
  → [E] AdaFace TFLite (112×112)                   ← جديد: يستبدل MobileFaceNet
  → [F] EmbeddingStabilityValidator.validate()     ← جديد
  → [G] float[512] L2-normalized
  → [H] MultiEmbeddingIdentity.addEmbedding()      ← جديد: حتى 5 per person
  → [I] DynamicThresholdEngine.computeThreshold()  ← جديد
  → [J] TopKCandidateRanker.rank()                 ← جديد: بدل binary match
  → [K] HumanReviewQueue أو AutoMatch              ← جديد
  → [L] Firebase Sync (WorkManager)                ← جديد: خارج Activities
```

### CrossMatch Flow — لا تغيير في الـ API

```java
// بلاغ مفقود جديد
CrossMatchManager.matchReportWithFoundPersons(reportId, reporterUid, personName, embString);

// بلاغ عثور جديد
CrossMatchManager.matchFoundPersonWithReports(foundId, finderUid, embString);

// مشاهدة جديدة
CrossMatchManager.matchSightingWithReports(sightingId, sighterUid, embString);
```

> **ملاحظة:** الـ embString في V2 يتغير من 128 قيمة إلى 512 قيمة — يتطلب **migration** للبيانات القديمة.

---

## ٥. V2 — المكونات الجديدة

### [C] FivePointAligner

```java
public class FivePointAligner {

    // نقاط المرجع الثابتة لـ 112×112 canonical face
    private static final float[][] REFERENCE_POINTS = {
        {38.2946f, 51.6963f},  // left eye
        {73.5318f, 51.5014f},  // right eye
        {56.0252f, 71.7366f},  // nose tip
        {41.5493f, 92.3655f},  // left mouth
        {70.7299f, 92.2041f}   // right mouth
    };

    public Bitmap align(Bitmap src, Face mlKitFace) {
        FaceLandmark leftEye   = mlKitFace.getLandmark(FaceLandmark.LEFT_EYE);
        FaceLandmark rightEye  = mlKitFace.getLandmark(FaceLandmark.RIGHT_EYE);
        FaceLandmark noseBase  = mlKitFace.getLandmark(FaceLandmark.NOSE_BASE);
        FaceLandmark leftMouth = mlKitFace.getLandmark(FaceLandmark.MOUTH_LEFT);
        FaceLandmark rightMouth= mlKitFace.getLandmark(FaceLandmark.MOUTH_RIGHT);

        // fallback للـ 2-point alignment إذا نقصت أي نقطة
        if (leftEye == null || rightEye == null || noseBase == null
                || leftMouth == null || rightMouth == null) {
            return twoPointFallback(src, mlKitFace);
        }

        float[][] srcPoints = {
            {leftEye.getPosition().x,    leftEye.getPosition().y},
            {rightEye.getPosition().x,   rightEye.getPosition().y},
            {noseBase.getPosition().x,   noseBase.getPosition().y},
            {leftMouth.getPosition().x,  leftMouth.getPosition().y},
            {rightMouth.getPosition().x, rightMouth.getPosition().y}
        };

        Matrix m = computeSimilarityTransform(srcPoints, REFERENCE_POINTS);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
    }

    private Matrix computeSimilarityTransform(float[][] src, float[][] dst) {
        // Similarity Transform: scale + rotate + translate (no shear)
        // يُحسب من متوسط النقاط الخمس لأعلى استقرار
        float srcCx = 0, srcCy = 0, dstCx = 0, dstCy = 0;
        for (int i = 0; i < 5; i++) {
            srcCx += src[i][0]; srcCy += src[i][1];
            dstCx += dst[i][0]; dstCy += dst[i][1];
        }
        srcCx /= 5; srcCy /= 5; dstCx /= 5; dstCy /= 5;

        float srcVar = 0, dot = 0, cross = 0;
        for (int i = 0; i < 5; i++) {
            float sx = src[i][0] - srcCx, sy = src[i][1] - srcCy;
            float dx = dst[i][0] - dstCx, dy = dst[i][1] - dstCy;
            srcVar += sx*sx + sy*sy;
            dot    += sx*dx + sy*dy;
            cross  += sx*dy - sy*dx;
        }

        float scale = dot / srcVar;
        float angle = (float) Math.atan2(cross, dot);
        float tx = dstCx - scale * (srcCx * (float)Math.cos(angle) - srcCy * (float)Math.sin(angle));
        float ty = dstCy - scale * (srcCx * (float)Math.sin(angle) + srcCy * (float)Math.cos(angle));

        Matrix matrix = new Matrix();
        matrix.setValues(new float[]{
            scale * (float)Math.cos(angle), -scale * (float)Math.sin(angle), tx,
            scale * (float)Math.sin(angle),  scale * (float)Math.cos(angle), ty,
            0, 0, 1
        });
        return matrix;
    }

    private Bitmap twoPointFallback(Bitmap src, Face face) {
        // الـ alignment الحالي بـ LEFT_EYE + RIGHT_EYE — يبقى كـ fallback
        FaceLandmark l = face.getLandmark(FaceLandmark.LEFT_EYE);
        FaceLandmark r = face.getLandmark(FaceLandmark.RIGHT_EYE);
        if (l == null || r == null) return src;
        float angle = (float) Math.toDegrees(Math.atan2(
            r.getPosition().y - l.getPosition().y,
            r.getPosition().x - l.getPosition().x));
        if (Math.abs(angle) < 2f) return src;
        Matrix m = new Matrix();
        m.postRotate(-angle, src.getWidth() / 2f, src.getHeight() / 2f);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
    }
}
```

---

### [D] FaceQualityAnalyzer

```java
public class FaceQualityAnalyzer {

    public QualityResult analyze(Bitmap alignedFace, Face detection) {
        float blur       = assessBlur(alignedFace);       // Laplacian variance
        float brightness = assessBrightness(alignedFace); // histogram mean
        float faceSize   = assessFaceSize(detection);     // relative to frame
        float poseScore  = assessPose(detection);         // yaw/pitch
        float occlusion  = assessOcclusion(alignedFace);  // eye region visibility

        float score = blur       * 0.30f
                    + brightness * 0.20f
                    + faceSize   * 0.20f
                    + poseScore  * 0.15f
                    + occlusion  * 0.15f;

        return new QualityResult(score, blur, brightness, faceSize, poseScore, occlusion);
    }

    private float assessBlur(Bitmap bmp) {
        // Laplacian variance — يُحوَّل للنطاق 0.0–1.0
        // variance ≥ 200 → 1.0 | variance ≤ 20 → 0.0
        float variance = computeLaplacianVariance(bmp);
        return Math.min(1f, Math.max(0f, (variance - 20f) / 180f));
    }

    private float assessBrightness(Bitmap bmp) {
        float mean = computeMeanBrightness(bmp);
        // مثالي: 80–180 → 1.0 | أقل من 25 أو أكثر من 235 → 0.0
        if (mean < 25f || mean > 235f) return 0f;
        if (mean >= 80f && mean <= 180f) return 1f;
        return mean < 80f ? (mean - 25f) / 55f : (235f - mean) / 55f;
    }

    private float assessFaceSize(Face face) {
        Rect box = face.getBoundingBox();
        int area = box.width() * box.height();
        // ≥ 120×120 = 1.0 | < 60×60 = 0.0
        return Math.min(1f, Math.max(0f, (float)(area - 3600) / (14400f - 3600f)));
    }

    private float assessPose(Face face) {
        Float yaw   = face.getHeadEulerAngleY();
        Float pitch = face.getHeadEulerAngleX();
        if (yaw == null || pitch == null) return 0.5f;
        float yawScore   = Math.max(0f, 1f - Math.abs(yaw)   / 45f);
        float pitchScore = Math.max(0f, 1f - Math.abs(pitch) / 30f);
        return (yawScore + pitchScore) / 2f;
    }

    private float assessOcclusion(Bitmap bmp) {
        // تحليل بسيط: هل منطقة العيون فيها تباين كافٍ؟
        // منطقة العيون: الربع العلوي من الـ 112×112
        return computeRegionVariance(bmp, 0, 0, 112, 50) > 100f ? 1f : 0.4f;
    }

    // Getters للقيم الخام
    private float computeLaplacianVariance(Bitmap bmp) { /* ... */ return 0; }
    private float computeMeanBrightness(Bitmap bmp) { /* ... */ return 0; }
    private float computeRegionVariance(Bitmap bmp, int x, int y, int w, int h) { /* ... */ return 0; }
}

public class QualityResult {
    public final float score;        // 0.0 – 1.0
    public final float blurScore;
    public final float brightnessScore;
    public final float faceSizeScore;
    public final float poseScore;
    public final float occlusionScore;

    public QualityTier getTier() {
        if (score >= 0.70f) return QualityTier.HIGH;
        if (score >= 0.45f) return QualityTier.MEDIUM;
        return QualityTier.LOW;
    }

    public enum QualityTier { HIGH, MEDIUM, LOW }
}
```

---

### [E] AdaFace TFLite

```java
public class AdaFaceRecognizer {
    private static final String MODEL_FILE = "models/adaface_ir18_112.tflite";
    private static final int INPUT_SIZE = 112;
    private static final int EMBEDDING_DIM = 512;

    // Singleton — لا تُنشئ أكثر من instance واحد
    private static AdaFaceRecognizer instance;
    private final Interpreter interpreter;

    private AdaFaceRecognizer(Context ctx) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(2); // لا تزد عن 2 على الأجهزة المتوسطة
        options.setUseNNAPI(false); // NNAPI يسبب مشاكل على بعض الأجهزة
        interpreter = new Interpreter(loadModelFile(ctx), options);
    }

    public static synchronized AdaFaceRecognizer getInstance(Context ctx) throws IOException {
        if (instance == null) instance = new AdaFaceRecognizer(ctx.getApplicationContext());
        return instance;
    }

    public float[] embed(Bitmap alignedFace) {
        // Resize إلى 112×112
        Bitmap scaled = Bitmap.createScaledBitmap(alignedFace, INPUT_SIZE, INPUT_SIZE, true);

        // Preprocessing: (pixel - 127.5) / 128.0 — CHW format
        float[] input = new float[3 * INPUT_SIZE * INPUT_SIZE];
        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        for (int i = 0; i < pixels.length; i++) {
            input[i]                       = ((pixels[i] >> 16 & 0xFF) - 127.5f) / 128.0f; // R
            input[i + INPUT_SIZE*INPUT_SIZE]   = ((pixels[i] >>  8 & 0xFF) - 127.5f) / 128.0f; // G
            input[i + 2*INPUT_SIZE*INPUT_SIZE] = ((pixels[i]       & 0xFF) - 127.5f) / 128.0f; // B
        }

        float[][] output = new float[1][EMBEDDING_DIM];
        interpreter.run(
            new float[][][][]{reshapeToCHW(input)},
            output
        );

        return l2Normalize(output[0]);
    }

    private float[] l2Normalize(float[] v) {
        float norm = 0f;
        for (float x : v) norm += x * x;
        norm = (float) Math.sqrt(norm);
        if (norm < 1e-10f) return v;
        float[] result = new float[v.length];
        for (int i = 0; i < v.length; i++) result[i] = v[i] / norm;
        return result;
    }

    private float[][][][] reshapeToCHW(float[] flat) {
        float[][][][] tensor = new float[1][3][INPUT_SIZE][INPUT_SIZE];
        for (int c = 0; c < 3; c++)
            for (int h = 0; h < INPUT_SIZE; h++)
                for (int w = 0; w < INPUT_SIZE; w++)
                    tensor[0][c][h][w] = flat[c * INPUT_SIZE * INPUT_SIZE + h * INPUT_SIZE + w];
        return tensor;
    }

    private MappedByteBuffer loadModelFile(Context ctx) throws IOException {
        AssetFileDescriptor fd = ctx.getAssets().openFd(MODEL_FILE);
        FileInputStream in = new FileInputStream(fd.getFileDescriptor());
        return in.getChannel().map(FileChannel.MapMode.READ_ONLY,
            fd.getStartOffset(), fd.getDeclaredLength());
    }
}
```

> **ملف النموذج:** `adaface_ir18_112.tflite` (~3.5MB)  
> **المصدر:** https://github.com/mk-minchul/AdaFace — اختر `ir18` وليس `ir50`/`ir100`  
> **الموقع في المشروع:** `app/src/main/assets/models/adaface_ir18_112.tflite`

---

### [F] EmbeddingStabilityValidator

```java
public class EmbeddingStabilityValidator {

    private final AdaFaceRecognizer recognizer;
    private static final float MIN_STABILITY = 0.88f;

    public boolean isStable(Bitmap alignedFace) {
        float[] original   = recognizer.embed(alignedFace);
        float[] compressed = recognizer.embed(simulateCompression(alignedFace, 70));
        float[] rotated    = recognizer.embed(rotate(alignedFace, 5f));
        float[] darkened   = recognizer.embed(adjustBrightness(alignedFace, -20));

        float avgSim = (
            cosineSimilarity(original, compressed) +
            cosineSimilarity(original, rotated) +
            cosineSimilarity(original, darkened)
        ) / 3f;

        return avgSim >= MIN_STABILITY;
        // إذا false → الصورة غير مستقرة → لا تُحفظ الـ embedding
    }

    private float cosineSimilarity(float[] a, float[] b) {
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
```

---

### [G] Multi-Embedding Identity

```java
public class MultiEmbeddingIdentity {
    private static final int MAX_EMBEDDINGS = 5;

    private final String personId;
    private final List<StoredEmbedding> embeddings = new ArrayList<>();

    public boolean addEmbedding(float[] vector, QualityResult quality, String imageType) {
        if (embeddings.size() >= MAX_EMBEDDINGS) return false;

        // تجنب إضافة embedding مكرر
        for (StoredEmbedding existing : embeddings) {
            if (cosineSimilarity(existing.vector, vector) > 0.98f) return false;
        }

        embeddings.add(new StoredEmbedding(vector, quality.score, imageType,
            "adaface_ir18", System.currentTimeMillis()));
        return true;
    }

    // أعلى تشابه عبر كل الـ embeddings
    public float matchAgainst(float[] queryVector) {
        float maxSim = 0f;
        for (StoredEmbedding emb : embeddings) {
            float sim = cosineSimilarity(emb.vector, queryVector);
            if (sim > maxSim) maxSim = sim;
        }
        return maxSim;
    }

    private float cosineSimilarity(float[] a, float[] b) {
        float dot = 0f;
        for (int i = 0; i < a.length; i++) dot += a[i] * b[i];
        return dot; // L2-normalized vectors
    }

    public static class StoredEmbedding {
        public final float[] vector;      // 512-dim
        public final float qualityScore;
        public final String imageType;    // frontal_clear | side | whatsapp | low_light | old
        public final String modelVersion; // adaface_ir18
        public final long timestamp;

        public StoredEmbedding(float[] v, float q, String t, String m, long ts) {
            vector = v; qualityScore = q; imageType = t; modelVersion = m; timestamp = ts;
        }
    }
}
```

**أنواع imageType المقبولة:**

| imageType | الأولوية |
|-----------|---------|
| `frontal_clear` | أعلى — يُرفع أولاً |
| `side_face` | ثانية |
| `old_photo` | ثالثة |
| `low_light` | رابعة |
| `whatsapp` | خامسة |

---

### [H] Dynamic Threshold Engine

```java
public class DynamicThresholdEngine {

    public float computeThreshold(QualityResult queryQuality, QualityResult storedQuality) {
        float minQuality = Math.min(queryQuality.score, storedQuality.score);

        if (minQuality >= 0.70f) return 0.82f;   // جودة عالية → threshold صارم
        if (minQuality >= 0.55f) return 0.77f;   // جودة متوسطة
        return 0.72f;                              // جودة ضعيفة → threshold مرن + Human Review
    }

    public MatchStatus classifyMatch(float similarity, float threshold, QualityResult quality) {
        if (quality.score < 0.45f)     return MatchStatus.INSUFFICIENT_QUALITY;
        if (similarity >= threshold + 0.05f) return MatchStatus.AUTO_MATCH;
        if (similarity >= threshold)   return MatchStatus.REVIEW_REQUIRED;
        if (similarity >= 0.60f)       return MatchStatus.REVIEW_REQUIRED; // منطقة رمادية دائماً
        return MatchStatus.AUTO_NO_MATCH;
    }

    public enum MatchStatus {
        AUTO_MATCH,          // تطابق مباشر + إشعار
        REVIEW_REQUIRED,     // يُرسل لـ Human Review Queue
        AUTO_NO_MATCH,       // لا تطابق — يبقى في النظام
        INSUFFICIENT_QUALITY // طلب صورة أوضح
    }
}
```

---

### [I] Top-K Candidate Ranker

```java
public class TopKCandidateRanker {

    public List<MatchCandidate> rank(
            float[] queryEmbedding,
            List<MultiEmbeddingIdentity> allPersons,
            SearchContext context,
            int topK) {

        List<MatchCandidate> candidates = new ArrayList<>();

        for (MultiEmbeddingIdentity person : allPersons) {
            float faceSim = person.matchAgainst(queryEmbedding);
            if (faceSim < 0.60f) continue; // تجاهل الضعيف جداً

            float composite = computeCompositeScore(faceSim, person, context);
            candidates.add(new MatchCandidate(person.getPersonId(), faceSim, composite));
        }

        candidates.sort((a, b) -> Float.compare(b.compositeScore, a.compositeScore));
        return candidates.subList(0, Math.min(topK, candidates.size()));
    }

    private float computeCompositeScore(float faceSim, MultiEmbeddingIdentity person, SearchContext ctx) {
        float score = faceSim * 0.75f;  // 75% face similarity

        // 10% تقارب العمر
        if (ctx.estimatedAge > 0 && person.getEstimatedAge() > 0) {
            float ageDiff = Math.abs(ctx.estimatedAge - person.getEstimatedAge());
            score += (Math.max(0f, 10f - ageDiff) / 10f) * 0.10f;
        }

        // 8% المحافظة
        if (ctx.governorate != null && ctx.governorate.equals(person.getGovernorate())) {
            score += 0.08f;
        }

        // 7% جودة الصورة المخزنة
        score += person.getMaxEmbeddingQuality() * 0.07f;

        return score;
    }

    public static class MatchCandidate {
        public final String personId;
        public final float faceSimilarity;
        public final float compositeScore;
        public MatchCandidate(String id, float face, float composite) {
            personId = id; faceSimilarity = face; compositeScore = composite;
        }
    }

    public static class SearchContext {
        public String governorate;
        public int estimatedAge;
        public long reportTimestamp;
    }
}
```

---

## ٦. WorkManager Pipeline

### لماذا ضروري

| السيناريو | النتيجة بدون WorkManager |
|-----------|--------------------------|
| تدوير الشاشة أثناء المعالجة | فقدان النتيجة |
| ضغط Back | إلغاء الـ embedding |
| RAM مرتفع | TFLite crash |
| إرسال في الخلفية | فشل صامت |

### المعمارية

```
UI (Activity/Fragment)
    ↓ ViewModel (StateFlow)
    ↓ Repository
    ↓ FaceEmbeddingWorker (WorkManager)
    ↓ AI Engine (AdaFace + Alignment + Quality)
    ↓ Firebase Firestore
```

### FaceEmbeddingWorker

```java
public class FaceEmbeddingWorker extends Worker {

    public static final String KEY_IMAGE_URI  = "image_uri";
    public static final String KEY_PERSON_ID  = "person_id";
    public static final String KEY_IMAGE_TYPE = "image_type";

    @NonNull
    @Override
    public Result doWork() {
        String imageUri  = getInputData().getString(KEY_IMAGE_URI);
        String personId  = getInputData().getString(KEY_PERSON_ID);
        String imageType = getInputData().getString(KEY_IMAGE_TYPE);

        try {
            Context ctx = getApplicationContext();

            // 1. Load
            Bitmap bitmap = ImagePreprocessor.preprocessFromUri(ctx, Uri.parse(imageUri));

            // 2. Detect
            Face face = FaceAnalyzer.detectSync(ctx, bitmap);
            if (face == null) return Result.failure(buildOutput("no_face_detected"));

            // 3. Align
            Bitmap aligned = new FivePointAligner().align(bitmap, face);

            // 4. Quality
            QualityResult quality = new FaceQualityAnalyzer().analyze(aligned, face);
            if (quality.getTier() == QualityResult.QualityTier.LOW) {
                return Result.failure(buildOutput("low_quality_" + quality.score));
            }

            // 5. Stability check
            if (!new EmbeddingStabilityValidator(AdaFaceRecognizer.getInstance(ctx)).isStable(aligned)) {
                return Result.failure(buildOutput("unstable_embedding"));
            }

            // 6. Embed
            float[] embedding = AdaFaceRecognizer.getInstance(ctx).embed(aligned);

            // 7. Save
            saveEmbeddingToFirestore(personId, embedding, quality, imageType);

            // 8. CrossMatch
            String embString = embeddingToString(embedding);
            CrossMatchManager.matchReportWithFoundPersons(personId, getReporterUid(), getPersonName(), embString);

            return Result.success();

        } catch (Exception e) {
            Log.e("FaceEmbeddingWorker", "Failed", e);
            return Result.retry();
        }
    }

    private Data buildOutput(String reason) {
        return new Data.Builder().putString("failure_reason", reason).build();
    }
}
```

### الاستدعاء من ViewModel

```java
OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(FaceEmbeddingWorker.class)
    .setInputData(new Data.Builder()
        .putString(FaceEmbeddingWorker.KEY_IMAGE_URI,  imageUri.toString())
        .putString(FaceEmbeddingWorker.KEY_PERSON_ID,  personId)
        .putString(FaceEmbeddingWorker.KEY_IMAGE_TYPE, "frontal_clear")
        .build())
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
    .build();

WorkManager.getInstance(context)
    .getWorkInfoByIdLiveData(work.getId())
    .observe(lifecycleOwner, info -> {
        if (info.getState() == WorkInfo.State.SUCCEEDED) onSuccess();
        if (info.getState() == WorkInfo.State.FAILED)    onFailure(info.getOutputData());
    });

WorkManager.getInstance(context).enqueue(work);
```

---

## ٧. Firebase Schema المحدّث

### reports (V2)

```json
{
  "reportId": "auto-key",
  "reporterId": "uid",
  "personName": "string",
  "status": "pending | approved | resolved",
  "governorate": "string",
  "estimatedAge": 35,
  "embeddings": [
    {
      "vector": "0.123,-0.456,...(512 قيمة)",
      "qualityScore": 0.85,
      "imageType": "frontal_clear",
      "modelVersion": "adaface_ir18",
      "preprocessingVersion": 2,
      "timestamp": 1715000000000
    }
  ],
  "embeddingCount": 2,
  "embeddingVersion": 3,
  "imageUrl": "storage-url",
  "timestamp": 1715000000000,
  "lastUpdated": 1715000000000
}
```

> **ملاحظة Migration:** `embeddingVersion: 3` يعني AdaFace 512-dim.  
> السجلات القديمة `embeddingVersion: 2` تبقى بـ MobileFaceNet 128-dim ولا تُطابَق مع الجديدة.

### found_persons (V2)

```json
{
  "reporterId": "uid",
  "embeddings": [
    {
      "vector": "0.123,...(512 قيمة)",
      "qualityScore": 0.78,
      "imageType": "whatsapp",
      "modelVersion": "adaface_ir18",
      "timestamp": 1715000000000
    }
  ],
  "embeddingVersion": 3,
  "governorate": "string",
  "estimatedAge": 40,
  "photoUrl": "storage-url",
  "timestamp": 1715000000000
}
```

### matches (لا تغيير في البنية)

```json
{
  "reportId": "string",
  "foundId": "string",
  "similarity": 0.87,
  "compositeScore": 0.83,
  "status": "pending_review | confirmed | rejected",
  "matchType": "face_vs_found | sighting_vs_report",
  "queryQualityScore": 0.72,
  "storedQualityScore": 0.85,
  "threshold": 0.77,
  "timestamp": 1715000000000
}
```

### debug_snapshots (جديد — Admin فقط)

```json
{
  "snapshotId": "string",
  "personId": "string",
  "failureReason": "string",
  "qualityScore": 0.42,
  "stabilityScore": 0.81,
  "topCandidates": ["id1", "id2"],
  "processingTimeMs": 850,
  "createdAt": 1715000000000,
  "expiresAt": 1715604800000
}
```

---

## ٨. التوقيعات الحقيقية للدوال

### CrossMatchManager

```java
public static void matchReportWithFoundPersons(
    String reportId, String reporterUid, String personName, String embedding)

public static void matchFoundPersonWithReports(
    String foundId, String finderUid, String embedding)

public static void matchSightingWithReports(
    String sightingId, String sighterUid, String embedding)

public static void notifySightingMatch(
    String reporterUid, String personName, int percent, String reportId, String sightingId)

public static void notifySightingMatch(
    String reporterUid, String personName, int percent, String reportId)

public static void cancelAll()
```

### ImagePreprocessor

```java
public static Bitmap preprocessFromUri(Context context, Uri uri)
public static Bitmap preprocessFromFile(String filePath)
public static Bitmap preprocessBitmap(Bitmap bitmap)  // ← الصحيحة دائماً
public static String validateImage(Bitmap bitmap)
// ❌ processForFaceDetection() — لا وجود لها
```

### FaceEmbeddingManager

```java
// Async — من UI thread
public static void extractEmbedding(Bitmap bitmap, EmbeddingCallback callback)

// Sync — من background thread فقط
public static float[] extractEmbeddingSync(Context ctx, Bitmap bitmap)

// Conversions
public static String embeddingToString(float[] emb)
public static float[] stringToEmbedding(String s)
public static float cosineSimilarity(float[] a, float[] b)
```

### Versioning Constants (V2)

```java
public static final int    EMBEDDING_VERSION       = 3;    // V2: AdaFace 512-dim
public static final String MODEL_VERSION           = "adaface_ir18";
public static final int    PREPROCESSING_VERSION   = 2;
public static final int    LEGACY_EMBEDDING_VERSION = 2;   // MobileFaceNet 128-dim
```

---

## ٩. خارطة التطوير

### ✅ المرحلة الأولى — مكتملة

| المهمة | الأثر | الملفات المتأثرة | الحالة |
|--------|-------|-----------------|--------|
| إضافة `adaface_ir18_112.tflite` | +++ | `assets/models/` | ✅ النموذج الحقيقي 44.6MB — 512-dim |
| `FivePointAligner.java` | +++ | جديد | ✅ منتهي |
| `FaceQualityAnalyzer.java` (float score) | ++ | يستبدل `ImageQualityEnhancer` | ✅ منتهي |
| `AdaFaceRecognizer.java` | +++ | يستبدل `TFLiteFaceRecognizer` | ✅ منتهي |
| `EmbeddingStabilityValidator.java` | ++ | جديد | ✅ منتهي |
| `FaceEmbeddingWorker.java` (WorkManager) | +++ | `workers/` | ✅ منتهي |
| تحديث `FaceEmbeddingManager.java` — V2 constants | ++ | `EMBEDDING_VERSION=3` | ✅ منتهي |
| تحديث `ImagePreprocessor.java` — `preprocessFromUri` | + | إضافة دوال | ✅ منتهي |
| Debug Snapshots في Room DB (Admin فقط) | ++ | جديد | 🟠 المرحلة الثانية |

> **الحالة:** `adaface_ir18_112.tflite` الحقيقي (44.6MB) موجود في `assets/models/`.  
> `AdaFaceRecognizer` يكتشف أبعاد الإخراج تلقائياً من `interp.getOutputTensor(0).shape()` — لا يحتاج تعديل عند تغيير النموذج.

**النتيجة المتوقعة:** رفع الدقة الواقعية من ~55% إلى ~82%

### ✅ المرحلة الثانية — مكتملة

| المهمة | الأثر | الحالة |
|--------|-------|--------|
| `DynamicThresholdEngine.java` — عتبة ديناميكية (0.72–0.82) | ++ | ✅ منتهي |
| `MultiEmbeddingIdentity.java` — هوية متعددة الصور | ++ | ✅ منتهي |
| `TopKCandidateRanker.java` — ترتيب أفضل K مرشحين | +++ | ✅ منتهي |
| `HumanReviewQueueManager.java` — إدارة قائمة المراجعة | +++ | ✅ منتهي |
| `EmbeddingMigrationTool.java` — أداة رصد V2→V3 | ++ | ✅ منتهي |
| إصلاح `FoundSightingActivity` → `matchSightingWithReports()` + V3 | ++ | ✅ منتهي |
| إضافة `matchSightingWithReports()` في `CrossMatchManager` | ++ | ✅ منتهي |
| `database.rules.json` — دمج وإصلاح + `.indexOn` للـ sightings | + | ✅ منتهي |

### 🟡 المرحلة الثالثة — لاحقاً

| المهمة | الأثر | ملاحظة |
|--------|-------|--------|
| ArcFace TFLite كـ **verifier فقط** | ++ | يُضاف للحالات الغامضة — ليس بديلاً |
| Consensus Engine (AdaFace + ArcFace) | ++ | فقط بعد استقرار AdaFace منفرداً |
| CompreFace self-hosted (VPS ~$10/شهر) | ++ | Cloud fallback للحالات الصعبة |

### 🔵 المرحلة الرابعة — مستقبل بعيد (> 10,000 سجل)

| المهمة | الأثر |
|--------|-------|
| HNSW Index على الـ Backend | +++ |
| Vector Database | ++ |
| Advanced Ensemble | ++ |

> **قاعدة ثابتة:** لا تنتقل لمرحلة أعلى قبل اختبار المرحلة الحالية على بيانات حقيقية.

---

## ١٠. استراتيجية الاختبار

### Unit Tests الأساسية

```java
// AdaFace Recognizer
testEmbed_returns512Dim()
testEmbed_l2NormalizedOutput()           // norm ≈ 1.0
testEmbed_sameImageSameEmbedding()       // deterministic
testEmbed_differentPeopleLowSimilarity() // < 0.75

// Quality Analyzer
testQuality_clearFrontal_highScore()     // > 0.70
testQuality_blurryImage_lowScore()       // < 0.45
testQuality_darkImage_lowScore()         // < 0.45
testQuality_nullFace_returnsZero()

// Stability Validator
testStability_goodImage_isStable()       // > 0.88
testStability_corruptImage_notStable()   // < 0.88

// Multi-Embedding
testMultiEmb_maxFiveEmbeddings()
testMultiEmb_duplicateRejected()         // similarity > 0.98 → reject
testMultiEmb_matchReturnsMax()
```

### Dataset Validation

```
20 شخص × 3 صور لكل شخص:
  ① صورة أمامية واضحة
  ② صورة WhatsApp مضغوطة
  ③ صورة بإضاءة ضعيفة

المقاييس:
  intra-person (نفس الشخص):    يجب > 0.78
  inter-person (أشخاص مختلفون): يجب < 0.72

إذا تداخلت القيمتان → اضبط threshold بناءً على النتائج الحقيقية
```

### Performance Targets

```
TFLite load time:           < 2 ثانية
Face detection:             < 200ms
5-Point Alignment:          < 50ms
Embedding extraction:       < 400ms  (AdaFace أبطأ قليلاً من MobileFaceNet)
CrossMatch (1000 records):  < 1500ms
Crash-free sessions:        > 99%
```

---

## ١١. الأخطاء الحالية المعروفة

| # | الخطأ | الأولوية | الحل |
|---|-------|---------|------|
| 1 | "لا يتم التعرف على وجوه" | 🔴 حرج | تشخيص TFLite init + AdaFace migration |
| 2 | "تتبع بلاغي" و"بلاغاتي" فارغتان | 🟠 عالية | Firestore query fix |
| 3 | الإشعارات بدون صوت | 🟡 متوسطة | NotificationChannel settings |
| 4 | `FoundSightingActivity` يستخدم matching قديم | 🟠 عالية | ربط بـ `matchSightingWithReports()` |
| 5 | `AdvancedFaceMatcher` — Age/Gender heuristics غير دقيقة | 🟡 متوسطة | تعطيل مؤقتاً حتى V2 |
| 6 | `OptimizedEmbeddingManager` — LruCache غير مستخدم | 🟢 منخفضة | دمجه أو حذفه |

---

## ١٢. قرارات هندسية ثابتة ومؤجلة

### ثابتة — لا تتغير

```
✅ Offline-first: كل الـ AI يعمل on-device
✅ نموذج واحد فقط في نفس الوقت (AdaFace IR18)
✅ Human Review Queue: الذكاء الاصطناعي يرشح، الإنسان يقرر
✅ Deterministic + Explainable matching
✅ L2 normalization قبل كل مقارنة
✅ Versioning لكل embedding محفوظ
✅ لا paid cloud APIs كنظام أساسي
```

### مؤجلة — لا تُنفَّذ الآن

```
❌ HNSW / FAISS — Firebase يكفي حتى 10k سجل
❌ ArcFace + PartialFC + Ensemble — RAM pressure + instability
❌ Python inference داخل التطبيق — TFLite أفضل بمراحل
❌ AWS Rekognition كنظام أساسي — تكلفة + dependency
❌ Age Progression — تعقيد غير مبرر في المرحلة الأولى
❌ Vector Database — over-engineering للحجم الحالي
```

---

## ١٣. مقاييس النجاح

| المقياس | الحالي (V1) | الهدف (V2) |
|---------|-------------|------------|
| True Positive — صور واضحة | ~85% | >95% |
| True Positive — صور WhatsApp | ~50% | >80% |
| True Positive — إضاءة ضعيفة | ~40% | >75% |
| False Positive Rate | ~2% | <0.5% |
| وقت المعالجة (جهاز متوسط) | ~800ms | <1200ms |
| تكلفة التشغيل الشهرية | $0 | $0 (V2) / $10 (V3 مع CompreFace) |
| Crash-free sessions | — | >99% |

---

## ما يجب فعله أولاً في المحادثة التالية

```
🔴 المرحلة الأولى — الأساس (بهذا الترتيب بالضبط)

1. تنزيل adaface_ir18_112.tflite (IR18 وليس IR50/IR100) → assets/models/
2. إنشاء AdaFaceRecognizer.java (Singleton — نموذج واحد فقط)
3. إنشاء FivePointAligner.java (مع two-point fallback)
4. إنشاء FaceQualityAnalyzer.java (يستبدل ImageQualityEnhancer)
5. إنشاء EmbeddingStabilityValidator.java
6. تحديث Firebase Schema → multi-embedding (embeddingVersion: 3)
7. نقل الـ pipeline إلى FaceEmbeddingWorker (WorkManager)
8. إضافة Debug Snapshots في Room DB (Admin فقط، 7 أيام)
9. اختبار على 5 صور حقيقية قبل أي خطوة في المرحلة الثانية

⚠️ لا تبدأ المرحلة الثانية قبل أن يعمل AdaFace بشكل مستقر على أجهزة حقيقية.
```

---

*SANAD AI Engine V2.1 — المرجع النهائي الشامل*  
*جاهز للتنفيذ المباشر — مايو 2026*
