package com.missingpersons.app.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * MultiEmbeddingIdentity — هوية شخص بعدة embeddings (حتى 5).
 *
 * كل شخص يمكن أن يمتلك حتى 5 embeddings من صور مختلفة:
 *   frontal_clear، side_face، old_photo، low_light، whatsapp.
 *
 * عند المطابقة → يُرجع أعلى Cosine Similarity عبر كل الـ embeddings.
 * تُرفض الـ embeddings المكررة (similarity > 0.98).
 */
public final class MultiEmbeddingIdentity {

    public static final int MAX_EMBEDDINGS = 5;

    private final String personId;
    private final List<StoredEmbedding> embeddings = new ArrayList<>();

    // بيانات إضافية للـ composite score في TopKCandidateRanker
    private String governorate;
    private int    estimatedAge;

    public MultiEmbeddingIdentity(String personId) {
        this.personId = personId;
    }

    public String getPersonId() { return personId; }

    public void setGovernorate(String gov) { this.governorate = gov; }
    public String getGovernorate() { return governorate; }

    public void setEstimatedAge(int age) { this.estimatedAge = age; }
    public int getEstimatedAge() { return estimatedAge; }

    // ── Embedding Management ──────────────────────────────────────────────

    /**
     * أضف embedding جديداً إذا لم يتجاوز الحد الأقصى ولم يكن مكرراً.
     *
     * @return true إذا أُضيف، false إذا رُفض (ممتلئ أو مكرر)
     */
    public boolean addEmbedding(float[] vector, float qualityScore,
                                String imageType, String modelVersion) {
        if (vector == null) return false;
        if (embeddings.size() >= MAX_EMBEDDINGS) return false;

        // رفض المكرر
        for (StoredEmbedding existing : embeddings) {
            if (cosineSimilarity(existing.vector, vector) > 0.98f) return false;
        }

        embeddings.add(new StoredEmbedding(
            vector, qualityScore, imageType, modelVersion, System.currentTimeMillis()));
        return true;
    }

    /**
     * أعلى تشابه بين queryVector وأي embedding مخزن.
     */
    public float matchAgainst(float[] queryVector) {
        float maxSim = 0f;
        for (StoredEmbedding emb : embeddings) {
            float sim = cosineSimilarity(emb.vector, queryVector);
            if (sim > maxSim) maxSim = sim;
        }
        return maxSim;
    }

    /**
     * أعلى qualityScore عبر كل الـ embeddings (للـ composite score).
     */
    public float getMaxEmbeddingQuality() {
        float max = 0f;
        for (StoredEmbedding emb : embeddings) {
            if (emb.qualityScore > max) max = emb.qualityScore;
        }
        return max;
    }

    public int getEmbeddingCount() { return embeddings.size(); }

    public List<StoredEmbedding> getEmbeddings() { return embeddings; }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0f;
        float dot = 0f;
        for (int i = 0; i < a.length; i++) dot += a[i] * b[i];
        return dot; // L2-normalized
    }

    // ── StoredEmbedding ───────────────────────────────────────────────────

    public static final class StoredEmbedding {
        public final float[] vector;       // N-dim L2-normalized
        public final float   qualityScore; // 0.0–1.0
        public final String  imageType;    // frontal_clear | side_face | old_photo | low_light | whatsapp
        public final String  modelVersion; // adaface_ir18
        public final long    timestamp;

        public StoredEmbedding(float[] vector, float qualityScore,
                               String imageType, String modelVersion, long timestamp) {
            this.vector       = vector;
            this.qualityScore = qualityScore;
            this.imageType    = imageType;
            this.modelVersion = modelVersion;
            this.timestamp    = timestamp;
        }
    }
}
