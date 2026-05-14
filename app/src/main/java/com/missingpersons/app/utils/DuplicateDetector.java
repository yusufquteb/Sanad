package com.missingpersons.app.utils;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.database.*;

/**
 * DuplicateDetector — كشف البلاغات المكررة
 *
 * السلوك الجديد:
 *   - إذا وُجد تكرار في المفقودين → isDuplicate=true, reason="missing_already_reported"
 *   - إذا وُجد تكرار في المعثورين → isDuplicate=true, reason="found_already_reported"
 *   - لا نمنع الرفع تلقائياً — القرار عند الـ UI (يُبلَّغ المستخدم وليس رفض صامت)
 */
public class DuplicateDetector {

    private static final String TAG = "DuplicateDetector";

    // عتبات
    private static final float  FACE_DUPLICATE_THRESHOLD  = 0.90f;
    private static final float  FACE_SIMILAR_THRESHOLD    = 0.80f;
    private static final double NAME_SIMILARITY_THRESHOLD  = 0.75;
    private static final int    AGE_TOLERANCE              = 3;

    /**
     * @param isDuplicate   true = تكرار واضح
     * @param existingId    ID البلاغ الموجود مسبقاً
     * @param similarity    نسبة التشابه (0-1)
     * @param reason        سبب النتيجة:
     *                      "image_hash" / "face_match" / "face_similar" / "name_match"
     *                      "missing_already_reported" / "found_already_reported"
     *                      "no_match" / "no_hash" / "no_embedding" / "no_name" / "error"
     */
    public interface DuplicateCallback {
        void onResult(boolean isDuplicate, String existingReportId,
                      float similarity, String reason);
    }

    // ────────────────────────────────────────────────────────────────────
    //  checkByHash
    // ────────────────────────────────────────────────────────────────────

    public static void checkByHash(String imageHash, DuplicateCallback callback) {
        if (imageHash == null || imageHash.isEmpty()) {
            callback.onResult(false, null, 0, "no_hash");
            return;
        }

        // فحص في reports (مفقود)
        FirebaseDatabase.getInstance().getReference("reports")
            .orderByChild("imageHash").equalTo(imageHash).limitToFirst(1)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String existingId = snapshot.getChildren().iterator().next().getKey();
                        Log.d(TAG, "🔴 Hash duplicate in reports: " + existingId);
                        // تكرار في المفقودين
                        callback.onResult(true, existingId, 1.0f,
                            "missing_already_reported");
                        return;
                    }
                    // فحص في found_persons (معثور)
                    FirebaseDatabase.getInstance().getReference("found_persons")
                        .orderByChild("imageHash").equalTo(imageHash).limitToFirst(1)
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot2) {
                                if (snapshot2.exists()) {
                                    String existingId = snapshot2.getChildren()
                                        .iterator().next().getKey();
                                    Log.d(TAG, "🟡 Hash duplicate in found_persons: " + existingId);
                                    // تكرار في المعثورين
                                    callback.onResult(true, existingId, 1.0f,
                                        "found_already_reported");
                                } else {
                                    callback.onResult(false, null, 0, "no_match");
                                }
                            }
                            @Override public void onCancelled(@NonNull DatabaseError e) {
                                callback.onResult(false, null, 0, "error");
                            }
                        });
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {
                    callback.onResult(false, null, 0, "error");
                }
            });
    }

    // ────────────────────────────────────────────────────────────────────
    //  checkByFace
    // ────────────────────────────────────────────────────────────────────

    public static void checkByFace(float[] embedding, DuplicateCallback callback) {
        if (embedding == null) {
            callback.onResult(false, null, 0, "no_embedding");
            return;
        }

        // فحص في reports
        FirebaseDatabase.getInstance().getReference("reports")
            .orderByChild("status").equalTo("approved").limitToLast(300)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    float maxSimMissing = 0;
                    String maxIdMissing = null;

                    for (DataSnapshot child : snapshot.getChildren()) {
                        String storedEmb = child.child("faceEmbedding").getValue(String.class);
                        if (storedEmb == null || storedEmb.isEmpty()) continue;
                        float[] storedVec = FaceEmbeddingManager.stringToEmbedding(storedEmb);
                        if (storedVec == null) continue;
                        float sim = FaceEmbeddingManager.cosineSimilarity(embedding, storedVec);
                        if (sim > maxSimMissing) {
                            maxSimMissing = sim;
                            maxIdMissing = child.getKey();
                        }
                    }

                    Log.d(TAG, "Face check reports maxSim=" + maxSimMissing + " id=" + maxIdMissing);

                    if (maxSimMissing >= FACE_DUPLICATE_THRESHOLD) {
                        callback.onResult(true, maxIdMissing, maxSimMissing,
                            "missing_already_reported");
                        return;
                    }

                    // [إصلاح] نسخ القيم إلى final لاستخدامها داخل inner class
                    final float   fSimMissing = maxSimMissing;
                    final String  fIdMissing  = maxIdMissing;

                    // فحص في found_persons
                    FirebaseDatabase.getInstance().getReference("found_persons")
                        .limitToLast(300)
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot2) {
                                float  maxSimFound = 0;
                                String maxIdFound  = null;

                                for (DataSnapshot child : snapshot2.getChildren()) {
                                    String storedEmb = child.child("faceEmbedding")
                                        .getValue(String.class);
                                    if (storedEmb == null || storedEmb.isEmpty()) continue;
                                    float[] sv = FaceEmbeddingManager
                                        .stringToEmbedding(storedEmb);
                                    if (sv == null) continue;
                                    float sim = FaceEmbeddingManager
                                        .cosineSimilarity(embedding, sv);
                                    if (sim > maxSimFound) {
                                        maxSimFound = sim;
                                        maxIdFound  = child.getKey();
                                    }
                                }

                                Log.d(TAG, "Face check found maxSim=" + maxSimFound
                                    + " id=" + maxIdFound);

                                if (maxSimFound >= FACE_DUPLICATE_THRESHOLD) {
                                    callback.onResult(true, maxIdFound, maxSimFound,
                                        "found_already_reported");
                                } else {
                                    // [إصلاح] نسخ final لأقصى تشابه بين المجموعتين
                                    final float  combinedMax = Math.max(fSimMissing, maxSimFound);
                                    final String combinedId  = fSimMissing >= maxSimFound
                                        ? fIdMissing : maxIdFound;

                                    if (combinedMax >= FACE_SIMILAR_THRESHOLD) {
                                        callback.onResult(false, combinedId,
                                            combinedMax, "face_similar");
                                    } else {
                                        callback.onResult(false, null,
                                            combinedMax, "no_match");
                                    }
                                }
                            }
                            @Override public void onCancelled(@NonNull DatabaseError e) {
                                callback.onResult(false, null, 0, "error");
                            }
                        });
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {
                    callback.onResult(false, null, 0, "error");
                }
            });
    }

    // ────────────────────────────────────────────────────────────────────
    //  checkByNameAndDetails
    // ────────────────────────────────────────────────────────────────────

    public static void checkByNameAndDetails(String name, String governorate, int age,
                                              DuplicateCallback callback) {
        if (name == null || name.trim().isEmpty()) {
            callback.onResult(false, null, 0, "no_name");
            return;
        }

        com.google.firebase.database.Query query;
        if (governorate != null && !governorate.isEmpty()) {
            query = FirebaseDatabase.getInstance().getReference("reports")
                .orderByChild("location").startAt(governorate)
                .endAt(governorate + "\uf8ff").limitToLast(100);
        } else {
            query = FirebaseDatabase.getInstance().getReference("reports")
                .orderByChild("status").equalTo("approved").limitToLast(200);
        }

        final String nameLower = name.trim().toLowerCase();

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String bestId   = null;
                float  bestScore = 0;

                for (DataSnapshot child : snapshot.getChildren()) {
                    String storedName = child.child("personName").getValue(String.class);
                    if (storedName == null || storedName.isEmpty()) continue;
                    double nameSim = stringSimilarity(nameLower,
                        storedName.trim().toLowerCase());
                    if (nameSim < NAME_SIMILARITY_THRESHOLD) continue;
                    if (age > 0) {
                        Long storedAge = child.child("personAge").getValue(Long.class);
                        if (storedAge != null && Math.abs(storedAge - age) > AGE_TOLERANCE)
                            continue;
                    }
                    if ((float) nameSim > bestScore) {
                        bestScore = (float) nameSim;
                        bestId    = child.getKey();
                    }
                }

                Log.d(TAG, "Name check bestScore=" + bestScore + " id=" + bestId);

                if (bestScore >= NAME_SIMILARITY_THRESHOLD && bestId != null) {
                    callback.onResult(bestScore >= 0.92f, bestId, bestScore,
                        "missing_already_reported");
                } else {
                    callback.onResult(false, null, 0, "no_match");
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                callback.onResult(false, null, 0, "error");
            }
        });
    }

    // ────────────────────────────────────────────────────────────────────
    //  checkAll — hash → face → name
    // ────────────────────────────────────────────────────────────────────

    public static void checkAll(String imageHash, float[] embedding,
                                 String name, String governorate, int age,
                                 DuplicateCallback callback) {
        checkByHash(imageHash, (isDup1, id1, sim1, reason1) -> {
            if (isDup1) { callback.onResult(true, id1, sim1, reason1); return; }
            checkByFace(embedding, (isDup2, id2, sim2, reason2) -> {
                if (isDup2) { callback.onResult(true, id2, sim2, reason2); return; }
                checkByNameAndDetails(name, governorate, age, callback);
            });
        });
    }

    // ────────────────────────────────────────────────────────────────────
    //  String Similarity (Levenshtein)
    // ────────────────────────────────────────────────────────────────────

    public static double stringSimilarity(String a, String b) {
        if (a == null || b == null) return 0.0;
        if (a.equals(b)) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        int distance = levenshtein(a, b);
        int maxLen   = Math.max(a.length(), b.length());
        return 1.0 - (double) distance / maxLen;
    }

    private static int levenshtein(String a, String b) {
        int la = a.length(), lb = b.length();
        int[][] dp = new int[la + 1][lb + 1];
        for (int i = 0; i <= la; i++) dp[i][0] = i;
        for (int j = 0; j <= lb; j++) dp[0][j] = j;
        for (int i = 1; i <= la; i++) {
            for (int j = 1; j <= lb; j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i-1][j] + 1, dp[i][j-1] + 1),
                    dp[i-1][j-1] + cost);
            }
        }
        return dp[la][lb];
    }
}
