package com.missingpersons.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * EmbeddingMigrationTool — يحدّد السجلات ذات بصمة الوجه القديمة (MobileFaceNet
 * 128-dim) وُيعيد استخراج بصمتها بالنموذج الحالي (AdaFace 512-dim)، لأن
 * المقارنة بين أبعاد مختلفة تُعطي تشابهاً صفرياً دائماً
 * (AdaFaceRecognizer.cosineSimilarity يرفض a.length != b.length) — أي أن
 * بلاغاً قديماً وبلاغاً جديداً لنفس الشخص لن يتطابقا أبداً ما لم تُعَد
 * معالجة الصورة القديمة.
 *
 * ⚠️ [إصلاح] النسخة السابقة من هذه الأداة كانت تُحدد "القديم" عبر وجود
 * الحقل faceEmbedding من عدمه، على افتراض أن faceEmbedding = V2/قديم
 * وembeddings[] = V3/جديد. هذا خاطئ عملياً: المسار الحي الفعلي
 * (ReportActivity/FoundPersonActivity) يكتب بصمة AdaFace 512-dim
 * الحالية مباشرة في نفس حقل faceEmbedding القديم. لو شُغِّلت النسخة
 * السابقة، كانت ستُعلِّم كل سجل حديث وصحيح كأنه "يحتاج إعادة معالجة"
 * فقط لأن الحقل موجود. الآن يُحدَّد القديم فعلياً حسب *أبعاد الشعاع
 * المخزَّن نفسه* بغض النظر عن اسم الحقل الذي خُزِّن فيه.
 *
 * Admin-only — يُستدعى من لوحة الإدارة فقط.
 */
public final class EmbeddingMigrationTool {

    private static final String TAG = "EmbeddingMigrationTool";

    private static final ExecutorService executor    = Executors.newSingleThreadExecutor();
    private static final Handler         mainHandler  = new Handler(Looper.getMainLooper());

    private EmbeddingMigrationTool() {}

    public interface MigrationCallback {
        void onProgress(int processed, int total, int migrated);
        void onComplete(MigrationResult result);
        void onError(String reason);
    }

    // ── Public API: فحص فقط (بدون إعادة معالجة) ────────────────────────────

    /**
     * فحص السجلات في node محدد وتحديد ما يحتاج إعادة معالجة فعلياً
     * (بصمة أبعادها أقل من AdaFaceRecognizer.EMBEDDING_DIM الحالية).
     *
     * @param node     "reports" أو "found_persons"
     * @param dryRun   true = فحص فقط بدون كتابة | false = يضع علامة needsReembedding
     * @param callback نتيجة الفحص
     */
    public static void migrate(String node, boolean dryRun, MigrationCallback callback) {
        FirebaseDatabase.getInstance().getReference(node)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snap) {
                    long total = snap.getChildrenCount();
                    Log.d(TAG, "migrate(" + node + "): total=" + total + " dryRun=" + dryRun);

                    int[] counters = {0, 0, 0}; // processed, alreadyCurrent, needsMigration

                    for (DataSnapshot child : snap.getChildren()) {
                        counters[0]++;
                        int dim = detectStoredDim(child);

                        if (dim <= 0) {
                            // لا بصمة إطلاقاً — ليس شأن هذه الأداة (لم يُكتشف وجه أصلاً)
                        } else if (dim >= AdaFaceRecognizer.EMBEDDING_DIM) {
                            counters[1]++;
                        } else {
                            counters[2]++;
                            Log.d(TAG, "  [" + child.getKey() + "] dim=" + dim
                                + " < " + AdaFaceRecognizer.EMBEDDING_DIM + " → يحتاج إعادة معالجة");
                            if (!dryRun) markNeedsReembedding(node, child.getKey());
                        }

                        if (counters[0] % 50 == 0) {
                            callback.onProgress(counters[0], (int) total, counters[2]);
                        }
                    }

                    MigrationResult result = new MigrationResult(
                        node, (int) total, counters[1], counters[2], dryRun);
                    Log.i(TAG, result.toString());
                    callback.onComplete(result);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError e) {
                    callback.onError(e.getMessage());
                }
            });
    }

    /** إحصاء سريع لأوضاع الـ embeddings بدون كتابة. */
    public static void scan(String node, MigrationCallback callback) {
        migrate(node, true, callback);
    }

    // ── Public API: إعادة الاستخراج الفعلية ────────────────────────────────

    /**
     * تحمّل هذه الدالة صورة كل سجل ذي بصمة قديمة (128-dim)، وتُعيد
     * استخراج بصمته بالنموذج الحالي (AdaFace)، وتكتب النتيجة في نفس
     * حقل faceEmbedding — وهذا هو الإصلاح الفعلي (وليس فقط الوسم) لعدم
     * تطابق بلاغ قديم مع بلاغ جديد لنفس الشخص.
     *
     * تعمل بالكامل على executor خلفي — آمنة للاستدعاء من الشاشة الرئيسية.
     *
     * @param context  أي Context (يُستخدم applicationContext داخلياً)
     * @param node     "reports" أو "found_persons"
     * @param callback تقدّم/نتيجة العملية (onProgress/onComplete تصل على Main thread)
     */
    public static void reembedLegacy(Context context, String node, MigrationCallback callback) {
        Context appCtx = context.getApplicationContext();

        FirebaseDatabase.getInstance().getReference(node)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snap) {
                    List<String> legacyIds = new ArrayList<>();
                    for (DataSnapshot child : snap.getChildren()) {
                        int dim = detectStoredDim(child);
                        if (dim > 0 && dim < AdaFaceRecognizer.EMBEDDING_DIM) {
                            legacyIds.add(child.getKey());
                        }
                    }

                    if (legacyIds.isEmpty()) {
                        callback.onComplete(new MigrationResult(
                            node, (int) snap.getChildrenCount(), 0, 0, false));
                        return;
                    }

                    executor.execute(() -> runReembedBatch(appCtx, node, legacyIds, callback));
                }

                @Override
                public void onCancelled(@NonNull DatabaseError e) {
                    callback.onError(e.getMessage());
                }
            });
    }

    private static void runReembedBatch(Context ctx, String node, List<String> ids,
                                         MigrationCallback callback) {
        int total = ids.size();
        int processed = 0, migrated = 0;

        for (String id : ids) {
            processed++;
            boolean ok = reembedOne(ctx, node, id);
            if (ok) migrated++;

            int p = processed, m = migrated;
            mainHandler.post(() -> callback.onProgress(p, total, m));
        }

        int finalMigrated = migrated;
        mainHandler.post(() ->
            callback.onComplete(new MigrationResult(node, total, 0, finalMigrated, false)));
    }

    /** @return true إذا نجحت إعادة استخراج وحفظ البصمة لهذا السجل */
    private static boolean reembedOne(Context ctx, String node, String id) {
        try {
            DataSnapshot snap = Tasks.await(
                FirebaseDatabase.getInstance().getReference(node).child(id).get());

            String photoUrl = firstNonEmpty(snap, "photoUrl", "imageUrl");
            if (photoUrl == null) {
                Log.w(TAG, "reembed[" + id + "]: لا يوجد رابط صورة");
                return false;
            }

            Bitmap bmp = downloadBitmap(photoUrl);
            if (bmp == null) {
                Log.w(TAG, "reembed[" + id + "]: فشل تحميل الصورة");
                return false;
            }

            float[] emb = FaceEmbeddingManager.extractEmbeddingSync(ctx, bmp);
            if (emb == null) {
                Log.w(TAG, "reembed[" + id + "]: فشل استخراج البصمة (وجه غير واضح؟)");
                return false;
            }

            Map<String, Object> update = new HashMap<>();
            update.put("faceEmbedding", FaceEmbeddingManager.embeddingToString(emb));
            update.put(EmbeddingCleanupUtil.FIELD_EMBEDDING_VERSION, FaceEmbeddingManager.EMBEDDING_VERSION);
            update.put(EmbeddingCleanupUtil.FIELD_MODEL_VERSION, FaceEmbeddingManager.MODEL_VERSION);
            update.put("needsReembedding", false);
            update.put("reembeddedAt", System.currentTimeMillis());

            Tasks.await(FirebaseDatabase.getInstance().getReference(node).child(id)
                .updateChildren(update));

            Log.i(TAG, "✅ reembed[" + id + "]: نجح — dim=" + emb.length);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "reembed[" + id + "] استثناء: " + e.getMessage());
            return false;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * أبعاد البصمة الفعلية المخزَّنة لسجل — يفحص الحقل القديم faceEmbedding
     * ومصفوفة embeddings[].vector معاً (بغض النظر عن أيهما "الحديث" فعلياً
     * في هذا المشروع)، ويُعيد أول شعاع صالح يجده.
     *
     * @return أبعاد الشعاع، أو 0 إذا لم يوجد أي embedding إطلاقاً
     */
    private static int detectStoredDim(DataSnapshot child) {
        String legacyEmb = child.child("faceEmbedding").getValue(String.class);
        if (legacyEmb != null && !legacyEmb.isEmpty()) {
            float[] vec = FaceEmbeddingManager.stringToEmbedding(legacyEmb);
            if (vec != null) return vec.length;
        }

        DataSnapshot embArr = child.child("embeddings");
        if (embArr.exists()) {
            for (DataSnapshot embSnap : embArr.getChildren()) {
                String vecStr = embSnap.child("vector").getValue(String.class);
                if (vecStr == null) continue;
                float[] vec = FaceEmbeddingManager.stringToEmbedding(vecStr);
                if (vec != null) return vec.length;
            }
        }

        return 0;
    }

    private static String firstNonEmpty(DataSnapshot snap, String... keys) {
        for (String key : keys) {
            String v = snap.child(key).getValue(String.class);
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }

    private static Bitmap downloadBitmap(String urlStr) {
        try {
            URL url = new URL(urlStr);
            URLConnection conn = url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            try (InputStream is = conn.getInputStream()) {
                return BitmapFactory.decodeStream(is);
            }
        } catch (Exception e) {
            Log.e(TAG, "downloadBitmap: " + e.getMessage());
            return null;
        }
    }

    private static void markNeedsReembedding(String node, String recordId) {
        Map<String, Object> update = new HashMap<>();
        update.put("needsReembedding", true);
        update.put(EmbeddingCleanupUtil.FIELD_EMBEDDING_VERSION, FaceEmbeddingManager.LEGACY_EMBEDDING_VERSION);

        FirebaseDatabase.getInstance()
            .getReference(node).child(recordId)
            .updateChildren(update)
            .addOnSuccessListener(v ->
                Log.d(TAG, "✅ marked needsReembedding: " + recordId))
            .addOnFailureListener(e ->
                Log.w(TAG, "markNeedsReembedding failed [" + recordId + "]: " + e.getMessage()));
    }

    // ── Result DTO ────────────────────────────────────────────────────────

    public static final class MigrationResult {
        public final String node;
        public final int    totalRecords;
        public final int    alreadyV3;
        public final int    needsMigration;
        public final boolean dryRun;

        public MigrationResult(String node, int total, int v3, int needs, boolean dry) {
            this.node           = node;
            this.totalRecords   = total;
            this.alreadyV3      = v3;
            this.needsMigration = needs;
            this.dryRun         = dry;
        }

        @Override
        public String toString() {
            return "MigrationResult{node=" + node
                + " total=" + totalRecords
                + " alreadyCurrent=" + alreadyV3
                + " needsMigration=" + needsMigration
                + " dryRun=" + dryRun + "}";
        }
    }
}
