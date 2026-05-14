package com.missingpersons.app.utils;

import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

/**
 * TopKCandidateRanker — ترتيب أفضل K مرشحين بدلاً من المطابقة الثنائية.
 *
 * يحسب Composite Score لكل مرشح:
 *   75% تشابه الوجه
 *   10% تقارب العمر
 *    8% نفس المحافظة
 *    7% جودة الـ embedding المخزن
 *
 * يُستخدم من CrossMatchManager لتوفير نتائج مرتبة بدلاً من "تطابق/لا تطابق".
 */
public final class TopKCandidateRanker {

    private static final String TAG       = "TopKCandidateRanker";
    public  static final int    DEFAULT_K = 5;

    private TopKCandidateRanker() {}

    // ── Public API ────────────────────────────────────────────────────────

    public interface RankCallback {
        void onRanked(List<MatchCandidate> topK);
        void onError(String reason);
    }

    /**
     * رتّب أفضل K مرشحين من Firebase مقابل queryEmbedding.
     *
     * @param queryEmbedding embedding الاستعلام (من صورة جديدة)
     * @param context        بيانات سياقية (عمر، محافظة، …)
     * @param node           اسم العقدة في Firebase ("reports" أو "found_persons")
     * @param topK           عدد المرشحين المطلوب
     * @param callback       نتيجة الترتيب
     */
    public static void rankFromFirebase(
            float[] queryEmbedding,
            SearchContext context,
            String node,
            int topK,
            RankCallback callback) {

        if (queryEmbedding == null) {
            callback.onError("null_embedding");
            return;
        }

        FirebaseDatabase.getInstance()
            .getReference(node)
            .orderByChild("status").equalTo("approved")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snap) {
                    List<MatchCandidate> candidates = new ArrayList<>();

                    for (DataSnapshot c : snap.getChildren()) {
                        // دعم multi-embedding (مصفوفة embeddings)
                        float maxSim = 0f;
                        float storedQuality = 0f;

                        // أولاً: جرّب الـ embeddings array (V3)
                        DataSnapshot embArr = c.child("embeddings");
                        if (embArr.exists()) {
                            for (DataSnapshot embSnap : embArr.getChildren()) {
                                String vecStr = embSnap.child("vector").getValue(String.class);
                                if (vecStr == null) continue;
                                float[] vec = FaceEmbeddingManager.stringToEmbedding(vecStr);
                                if (vec == null) continue;
                                float sim = AdaFaceRecognizer.cosineSimilarity(queryEmbedding, vec);
                                if (sim > maxSim) maxSim = sim;
                                Double q = embSnap.child("qualityScore").getValue(Double.class);
                                if (q != null && q.floatValue() > storedQuality)
                                    storedQuality = q.floatValue();
                            }
                        }

                        // fallback: embedding مفرد (V2)
                        if (maxSim == 0f) {
                            String legacyEmb = c.child("faceEmbedding").getValue(String.class);
                            if (legacyEmb != null && !legacyEmb.isEmpty()) {
                                float[] vec = FaceEmbeddingManager.stringToEmbedding(legacyEmb);
                                if (vec != null)
                                    maxSim = AdaFaceRecognizer.cosineSimilarity(queryEmbedding, vec);
                            }
                        }

                        if (maxSim < 0.55f) continue; // تجاهل الضعيف جداً

                        // Composite Score
                        String govStored  = strVal(c, "governorate");
                        Long   ageStored  = c.child("estimatedAge").getValue(Long.class);
                        int    ageInt     = ageStored != null ? ageStored.intValue() : 0;

                        float composite = computeComposite(
                            maxSim, storedQuality,
                            context, govStored, ageInt);

                        candidates.add(new MatchCandidate(
                            c.getKey(),
                            strVal(c, "personName"),
                            maxSim,
                            composite,
                            storedQuality));
                    }

                    // ترتيب تنازلي
                    candidates.sort((a, b) ->
                        Float.compare(b.compositeScore, a.compositeScore));

                    List<MatchCandidate> result =
                        candidates.subList(0, Math.min(topK, candidates.size()));

                    Log.d(TAG, "ranked " + candidates.size() + " → top " + result.size());
                    callback.onRanked(result);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError e) {
                    callback.onError(e.getMessage());
                }
            });
    }

    // ── Composite Score ───────────────────────────────────────────────────

    private static float computeComposite(
            float faceSim, float storedQuality,
            SearchContext ctx, String govStored, int ageStored) {

        float score = faceSim * 0.75f;

        // +10% تقارب العمر
        if (ctx != null && ctx.estimatedAge > 0 && ageStored > 0) {
            float ageDiff = Math.abs(ctx.estimatedAge - ageStored);
            score += (Math.max(0f, 10f - ageDiff) / 10f) * 0.10f;
        }

        // +8% نفس المحافظة
        if (ctx != null && ctx.governorate != null
                && ctx.governorate.equals(govStored)) {
            score += 0.08f;
        }

        // +7% جودة الـ embedding المخزن
        score += storedQuality * 0.07f;

        return Math.min(1f, score);
    }

    private static String strVal(DataSnapshot snap, String key) {
        String v = snap.child(key).getValue(String.class);
        return v != null ? v : "";
    }

    // ── DTOs ──────────────────────────────────────────────────────────────

    public static final class MatchCandidate {
        public final String personId;
        public final String personName;
        public final float  faceSimilarity;
        public final float  compositeScore;
        public final float  storedQuality;

        public MatchCandidate(String id, String name, float face,
                              float composite, float quality) {
            this.personId       = id;
            this.personName     = name;
            this.faceSimilarity = face;
            this.compositeScore = composite;
            this.storedQuality  = quality;
        }

        public int getPercent() { return (int)(faceSimilarity * 100); }
    }

    public static final class SearchContext {
        public String governorate;
        public int    estimatedAge;
        public long   reportTimestamp;

        public SearchContext() {}

        public SearchContext(String gov, int age) {
            this.governorate  = gov;
            this.estimatedAge = age;
        }
    }
}
