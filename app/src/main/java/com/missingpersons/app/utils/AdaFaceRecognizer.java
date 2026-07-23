package com.missingpersons.app.utils;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * AdaFaceRecognizer — محرك التعرف على الوجوه V2
 *
 * يستخدم نموذج TFLite لإنتاج embedding محوّل L2.
 *
 * المعمارية:
 *   - Singleton
 *   - Input:  112×112 RGB — HWC — ((pixel-127.5)/128.0)
 *   - Output: float[N] L2-normalized — N يُكتشف تلقائياً من النموذج
 *   - Threads: 2
 *
 * 🔴🔴🔴 [تحقّق فعلي 2026-07-23] الملف الحالي app/src/main/assets/models/
 * adaface_ir18_112.tflite (46.7MB) **ليس نموذج تعرّف على وجوه صالحاً**:
 *   - إخراجه [1, 1000] — يطابق عدد فئات ImageNet تماماً، وليس embedding
 *     بأبعاد 512 كما تفترض EMBEDDING_DIM أدناه.
 *   - اختُبر فعلياً بصور حقيقية + ضوضاء عشوائية + صور خلفية عبر تشغيل
 *     الملف مباشرة بنفس المعالجة المستخدمة هنا (TFLite، (px-127.5)/128):
 *     تشابه جيب التمام بين **أي محتويين عشوائيين تماماً غير متعلقين**
 *     (ضوضاء مقابل ضوضاء، حائط مقابل وجه، أسود مقابل أبيض) خرج بين
 *     0.97–0.99 — أعلى من كل عتبات المطابقة المستخدمة في التطبيق
 *     (0.72–0.90). أي الملف لا يُميّز أي شيء عن أي شيء؛ "المطابقة" حالياً
 *     كانت ستُطابق كل صورة مع كل صورة أخرى بغض النظر عن الشخص.
 *   - selfTestDiscriminative() أدناه يكتشف هذا تلقائياً عند تحميل النموذج
 *     ويُعطّل isAvailable() إن فشل الفحص — حتى لا تُنتَج مطابقات وهمية.
 *   - **المطلوب**: استبدال الملف بنموذج تعرّف وجوه حقيقي ومُتحقَّق منه
 *     (AdaFace/ArcFace/MobileFaceNet مُصدَّر بشكل صحيح إلى TFLite بمخرج
 *     embedding فعلي 128/256/512-dim)، والتحقق منه بنفس أسلوب الفحص
 *     أعلاه (تشابه منخفض بين محتويين عشوائيين، وعالٍ بين صورتين لنفس
 *     الشخص) قبل تضمينه.
 */
public final class AdaFaceRecognizer {

    private static final String TAG        = "AdaFaceRecognizer";
    private static final String MODEL_FILE = "models/adaface_ir18_112.tflite";
    public  static final int    INPUT_SIZE = 112;
    public  static final int    EMBEDDING_DIM = 512; // 512 لـ AdaFace الحقيقي، 128 للـ placeholder

    private static volatile AdaFaceRecognizer instance;

    private final Interpreter interpreter;
    private final boolean      available;
    private final int          actualEmbeddingDim;

    // ── Singleton ────────────────────────────────────────────────────────
    private AdaFaceRecognizer(Context ctx) {
        boolean ok = false;
        Interpreter interp = null;
        int embDim = EMBEDDING_DIM;
        try {
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(2);
            options.setUseNNAPI(false);
            interp = new Interpreter(loadModelFile(ctx), options);

            // اكتشاف أبعاد الإخراج الفعلية من النموذج
            int[] outputShape = interp.getOutputTensor(0).shape();
            embDim = outputShape[outputShape.length - 1];

            ok = true;
            Log.i(TAG, "✅ model loaded — embedding_dim=" + embDim
                + (embDim < 512 ? " [placeholder — replace with AdaFace IR18 for 512-dim]" : ""));

            // ── فحص تمييزي (self-test) ───────────────────────────────
            // نموذج تعرّف على الوجوه سليم يجب أن يُنتج بصمات مختلفة تماماً
            // لصورتين عشوائيتين مختلفتين (تشابه منخفض جداً). لو أعطى تشابهاً
            // عالياً حتى بين صورتين عشوائيتين لا علاقة بينهما، فهذا يعني أن
            // الملف المحمَّل ليس نموذج تعرّف على وجوه فعلياً (أو أن preprocessing
            // خاطئ تماماً) — وسيُطابق أي صورتين ببعضهما دائماً مهما اختلف
            // الشخصان، مما يجعل ميزة المطابقة كلها عديمة الفائدة (بل ضارة:
            // إشعارات "تطابق" وهمية على كل بلاغ جديد).
            if (!selfTestDiscriminative(interp, embDim)) {
                ok = false;
                Log.e(TAG, "❌❌❌ فشل الفحص التمييزي — النموذج لا يُميّز بين "
                    + "محتويات مختلفة تماماً (نفس البصمة تقريباً لصورتين عشوائيتين). "
                    + "غالباً هذا الملف ليس نموذج AdaFace حقيقياً، أو مُصدَّر بشكل خاطئ. "
                    + "تعطيل المطابقة بالذكاء الاصطناعي حتى يُستبدَل الملف بنموذج تعرّف "
                    + "وجوه صحيح ومُتحقَّق منه.");
                AiError.logAll(TAG, AiError.MODEL_NOT_LOADED,
                    "AdaFaceRecognizer: model failed discriminative self-test — "
                        + "not a valid face embedding model", null);
            }
        } catch (IOException e) {
            Log.e(TAG, "❌ فشل تحميل النموذج من assets/models/");
            AiError.logAll(TAG, AiError.MODEL_NOT_LOADED,
                "AdaFaceRecognizer init: " + e.getMessage(), e);
        } catch (Exception e) {
            Log.e(TAG, "❌ خطأ في تهيئة AdaFaceRecognizer: " + e.getMessage());
            AiError.logAll(TAG, AiError.MODEL_NOT_LOADED, "AdaFaceRecognizer: " + e.getMessage(), e);
        }
        this.interpreter        = interp;
        this.available          = ok;
        this.actualEmbeddingDim = embDim;
    }

    /**
     * يُشغّل النموذج على نمطين اصطناعيين مختلفين تماماً ويتحقق أن البصمتين
     * الناتجتين ليستا شبه متطابقتين. عتبة 0.9 متحفّظة عمداً (أعلى من أي عتبة
     * مطابقة مستخدمة في التطبيق) — أي تشابه فوقها بين محتويين عشوائيين
     * تماماً يعني قطعاً أن النموذج معطوب أو غير مناسب لهذه المهمة.
     */
    private static boolean selfTestDiscriminative(Interpreter interp, int embDim) {
        try {
            float[] a = runRaw(interp, embDim, syntheticPattern(0));
            float[] b = runRaw(interp, embDim, syntheticPattern(1));
            float sim = cosineSimilarity(l2NormalizeStatic(a), l2NormalizeStatic(b));
            Log.i(TAG, "🔎 self-test: similarity(pattern_A, pattern_B)=" + sim);
            return sim < 0.9f;
        } catch (Exception e) {
            Log.w(TAG, "selfTestDiscriminative: تعذّر التنفيذ — " + e.getMessage());
            return true; // لا نُعطّل النموذج بسبب فشل الفحص نفسه، فقط بسبب نتيجته
        }
    }

    private static int[] syntheticPattern(int seed) {
        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        for (int y = 0; y < INPUT_SIZE; y++) {
            for (int x = 0; x < INPUT_SIZE; x++) {
                int r = (x * 7  + y * 13 + seed * 97)  % 256;
                int g = (x * 31 + y * 3  + seed * 61)  % 256;
                int b = (x * 17 + y * 23 + seed * 131) % 256;
                pixels[y * INPUT_SIZE + x] = (0xFF << 24) | (r << 16) | (g << 8) | b;
            }
        }
        return pixels;
    }

    private static float[] runRaw(Interpreter interp, int embDim, int[] pixels) {
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(pixels.length * 3 * 4);
        inputBuffer.order(ByteOrder.nativeOrder());
        for (int px : pixels) {
            inputBuffer.putFloat(((px >> 16 & 0xFF) - 127.5f) / 128.0f);
            inputBuffer.putFloat(((px >>  8 & 0xFF) - 127.5f) / 128.0f);
            inputBuffer.putFloat(((px       & 0xFF) - 127.5f) / 128.0f);
        }
        inputBuffer.rewind();
        float[][] output = new float[1][embDim];
        interp.run(inputBuffer, output);
        return output[0];
    }

    private static float[] l2NormalizeStatic(float[] v) {
        double norm = 0.0;
        for (float x : v) norm += (double) x * x;
        norm = Math.sqrt(norm);
        if (norm < 1e-10) return v;
        float[] result = new float[v.length];
        for (int i = 0; i < v.length; i++) result[i] = (float) (v[i] / norm);
        return result;
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

    /** الأبعاد الفعلية للـ embedding من النموذج المحمّل */
    public int getEmbeddingDim() { return actualEmbeddingDim; }

    // ── Embedding ────────────────────────────────────────────────────────

    /**
     * استخراج embedding من صورة وجه محاذاة (112×112 preferred).
     *
     * @param alignedFace صورة الوجه المحاذاة من FivePointAligner
     * @return float[N] L2-normalized، أو null إذا فشل
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
            Bitmap scaled = Bitmap.createScaledBitmap(alignedFace, INPUT_SIZE, INPUT_SIZE, true);

            // HWC format: pixel-by-pixel [R, G, B] — متوافق مع TFLite NHWC
            int pixelCount = INPUT_SIZE * INPUT_SIZE;
            int[] pixels   = new int[pixelCount];
            scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

            ByteBuffer inputBuffer = ByteBuffer.allocateDirect(pixelCount * 3 * 4);
            inputBuffer.order(ByteOrder.nativeOrder());
            for (int px : pixels) {
                inputBuffer.putFloat(((px >> 16 & 0xFF) - 127.5f) / 128.0f); // R
                inputBuffer.putFloat(((px >>  8 & 0xFF) - 127.5f) / 128.0f); // G
                inputBuffer.putFloat(((px       & 0xFF) - 127.5f) / 128.0f); // B
            }
            inputBuffer.rewind();

            float[][] output = new float[1][actualEmbeddingDim];
            interpreter.run(inputBuffer, output);

            float[] embedding = l2Normalize(output[0]);
            Log.d(TAG, "✅ embedding: dim=" + embedding.length);
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
     * Cosine Similarity بين embedding-ين L2-normalized.
     * القيمة بين -1.0 و 1.0 (أعلى = أقرب).
     */
    public static float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0f;
        float dot = 0f;
        for (int i = 0; i < a.length; i++) dot += a[i] * b[i];
        return dot; // vectors are already L2-normalized
    }
}
