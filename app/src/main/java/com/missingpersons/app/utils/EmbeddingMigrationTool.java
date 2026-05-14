package com.missingpersons.app.utils;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * EmbeddingMigrationTool — ترقية سجلات V2 إلى هيكل V3 (multi-embedding).
 *
 * V2: حقل faceEmbedding واحد (MobileFaceNet 128-dim)
 * V3: مصفوفة embeddings[] (AdaFace 512-dim، حتى 5 embeddings)
 *
 * بما أن V2 وV3 يستخدمان نماذج مختلفة (MobileFaceNet vs AdaFace)،
 * فإن المهاجرة الحقيقية تتطلب إعادة معالجة الصور الأصلية.
 * هذه الأداة تُحدد السجلات التي تحتاج إعادة معالجة وتضع علامة needsReembedding=true.
 *
 * Admin-only — يُستدعى من لوحة الإدارة فقط.
 */
public final class EmbeddingMigrationTool {

    private static final String TAG = "EmbeddingMigrationTool";

    private EmbeddingMigrationTool() {}

    public interface MigrationCallback {
        void onProgress(int processed, int total, int migrated);
        void onComplete(MigrationResult result);
        void onError(String reason);
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * فحص السجلات في node محدد وتحديد ما يحتاج إعادة معالجة.
     *
     * @param node     "reports" أو "found_persons"
     * @param dryRun   true = فحص فقط بدون كتابة | false = تحديث Firebase
     * @param callback نتيجة الترقية
     */
    public static void migrate(String node, boolean dryRun, MigrationCallback callback) {
        FirebaseDatabase.getInstance().getReference(node)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snap) {
                    long total = snap.getChildrenCount();
                    Log.d(TAG, "migrate(" + node + "): total=" + total
                        + " dryRun=" + dryRun);

                    int[] counters = {0, 0, 0}; // processed, alreadyV3, needsMigration

                    for (DataSnapshot child : snap.getChildren()) {
                        counters[0]++;
                        boolean hasV3 = child.child("embeddings").exists();
                        boolean hasV2 = child.child("faceEmbedding").exists()
                            && child.child("faceEmbedding").getValue(String.class) != null
                            && !child.child("faceEmbedding").getValue(String.class).isEmpty();

                        if (hasV3) {
                            counters[1]++;
                            // مهاجَر مسبقاً أو يملك embeddings جديدة
                        } else if (hasV2) {
                            counters[2]++;
                            Log.d(TAG, "  [" + child.getKey() + "] V2 → needs reembedding");

                            if (!dryRun) {
                                markNeedsReembedding(node, child.getKey());
                            }
                        }

                        if (counters[0] % 50 == 0) {
                            callback.onProgress(counters[0], (int)total, counters[2]);
                        }
                    }

                    MigrationResult result = new MigrationResult(
                        node, (int)total, counters[1], counters[2], dryRun);
                    Log.i(TAG, result.toString());
                    callback.onComplete(result);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError e) {
                    callback.onError(e.getMessage());
                }
            });
    }

    /**
     * إحصاء سريع لأوضاع الـ embeddings بدون كتابة.
     */
    public static void scan(String node, MigrationCallback callback) {
        migrate(node, true, callback);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static void markNeedsReembedding(String node, String recordId) {
        Map<String, Object> update = new HashMap<>();
        update.put("needsReembedding", true);
        update.put("embeddingVersion", FaceEmbeddingManager.LEGACY_EMBEDDING_VERSION);

        FirebaseDatabase.getInstance()
            .getReference(node).child(recordId)
            .updateChildren(update)
            .addOnSuccessListener(v ->
                Log.d(TAG, "✅ marked needsReembedding: " + recordId))
            .addOnFailureListener(e ->
                Log.w(TAG, "markNeedsReembedding failed [" + recordId + "]: "
                    + e.getMessage()));
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
                + " alreadyV3=" + alreadyV3
                + " needsMigration=" + needsMigration
                + " dryRun=" + dryRun + "}";
        }
    }
}
