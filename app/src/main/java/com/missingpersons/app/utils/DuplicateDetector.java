package com.missingpersons.app.utils;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.database.*;

/**
 * DuplicateDetector — كشف البلاغات المكررة (محسَّن)
 *
 * [مرحلة 2.3] تحسينات:
 *   ✅ Levenshtein distance لمقارنة الأسماء (تشابه نصي)
 *   ✅ مقارنة المحافظة والعمر قبل أي عملية
 *   ✅ checkByNameAndDetails — فحص بدون صورة
 *   ✅ checkByFace — فحص الـ face embedding
 *   ✅ checkByHash — فحص hash الصورة
 *   ✅ checkAll — فحص شامل بالترتيب: hash → face → name
 */
public class DuplicateDetector {

    private static final String TAG = "DuplicateDetector";

    // عتبات
    private static final float  FACE_DUPLICATE_THRESHOLD  = 0.90f; // > 90% مكرر
    private static final float  FACE_SIMILAR_THRESHOLD    = 0.80f; // > 80% مشابه
    private static final double NAME_SIMILARITY_THRESHOLD  = 0.75;  // > 75% تشابه نصي
    private static final int    AGE_TOLERANCE              = 3;     // ± 3 سنوات

    public interface DuplicateCallback {
        void onResult(boolean isDuplicate, String existingReportId, float similarity, String reason);
    }

    // ════════════════════════════════════════════════════════════════════
    //  checkByHash — بـ image hash
    // ════════════════════════════════════════════════════════════════════

    public static void checkByHash(String imageHash, DuplicateCallback callback) {
        if (imageHash == null || imageHash.isEmpty()) {
            callback.onResult(false, null, 0, "no_hash");
            return;
        }

        FirebaseDatabase.getInstance().getReference("reports")
            .orderByChild("imageHash").equalTo(imageHash)
            .limitToFirst(1)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String existingId = snapshot.getChildren().iterator().next().getKey();
                        Log.d(TAG, "🔴 Hash duplicate found: " + existingId);
                        callback.onResult(true, existingId, 1.0f, "image_hash");
                    } else {
                        callback.onResult(false, null, 0, "no_match");
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {
                    callback.onResult(false, null, 0, "error");
                }
            });
    }

    // ════════════════════════════════════════════════════════════════════
    //  checkByFace — بـ face embedding
    // ════════════════════════════════════════════════════════════════════

    public static void checkByFace(float[] embedding, DuplicateCallback callback) {
        if (embedding == null) {
            callback.onResult(false, null, 0, "no_embedding");
            return;
        }

        FirebaseDatabase.getInstance().getReference("reports")
            .orderByChild("status").equalTo("approved")
            .limitToLast(300)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    float maxSim = 0;
                    String maxId = null;

                    for (DataSnapshot child : snapshot.getChildren()) {
                        String storedEmb = child.child("faceEmbedding").getValue(String.class);
                        if (storedEmb == null || storedEmb.isEmpty()) continue;

                        float[] storedVec = FaceEmbeddingManager.stringToEmbedding(storedEmb);
                        if (storedVec == null) continue;

                        float sim = FaceEmbeddingManager.cosineSimilarity(embedding, storedVec);
                        if (sim > maxSim) {
                            maxSim = sim;
                            maxId = child.getKey();
                        }
                    }

                    Log.d(TAG, "Face check maxSim=" + maxSim + " id=" + maxId);

                    if (maxSim >= FACE_DUPLICATE_THRESHOLD) {
                        callback.onResult(true, maxId, maxSim, "face_match");
                    } else if (maxSim >= FACE_SIMILAR_THRESHOLD) {
                        // مشابه لكن ليس مكرراً — أبلغ بالنسبة فقط
                        callback.onResult(false, maxId, maxSim, "face_similar");
                    } else {
                        callback.onResult(false, null, maxSim, "no_match");
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {
                    callback.onResult(false, null, 0, "error");
                }
            });
    }

    // ════════════════════════════════════════════════════════════════════
    //  checkByNameAndDetails — بالاسم + المحافظة + العمر
    // ════════════════════════════════════════════════════════════════════

    /**
     * @param name       اسم الشخص المفقود
     * @param governorate المحافظة
     * @param age        العمر (0 = غير معروف)
     * @param callback   النتيجة
     */
    public static void checkByNameAndDetails(String name, String governorate, int age,
                                              DuplicateCallback callback) {
        if (name == null || name.trim().isEmpty()) {
            callback.onResult(false, null, 0, "no_name");
            return;
        }

        // فلتر بالمحافظة إذا متوفرة لتقليل حجم البيانات
        com.google.firebase.database.Query query;
        if (governorate != null && !governorate.isEmpty()) {
            query = FirebaseDatabase.getInstance().getReference("reports")
                .orderByChild("location").startAt(governorate).endAt(governorate + "\uf8ff")
                .limitToLast(100);
        } else {
            query = FirebaseDatabase.getInstance().getReference("reports")
                .orderByChild("status").equalTo("approved")
                .limitToLast(200);
        }

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String bestId   = null;
                float  bestScore = 0;

                String nameLower = name.trim().toLowerCase();

                for (DataSnapshot child : snapshot.getChildren()) {
                    String storedName = child.child("personName").getValue(String.class);
                    if (storedName == null || storedName.isEmpty()) continue;

                    // 1. تشابه الاسم
                    double nameSim = stringSimilarity(nameLower, storedName.trim().toLowerCase());
                    if (nameSim < NAME_SIMILARITY_THRESHOLD) continue;

                    // 2. تقارب العمر (إذا متوفر)
                    if (age > 0) {
                        Long storedAge = child.child("personAge").getValue(Long.class);
                        if (storedAge != null && Math.abs(storedAge - age) > AGE_TOLERANCE) {
                            continue; // العمر بعيد — تخطَّ
                        }
                    }

                    float combinedScore = (float) nameSim;
                    if (combinedScore > bestScore) {
                        bestScore = combinedScore;
                        bestId    = child.getKey();
                    }
                }

                Log.d(TAG, "Name check bestScore=" + bestScore + " id=" + bestId);

                if (bestScore >= NAME_SIMILARITY_THRESHOLD && bestId != null) {
                    callback.onResult(bestScore >= 0.92f, bestId, bestScore, "name_match");
                } else {
                    callback.onResult(false, null, 0, "no_match");
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                callback.onResult(false, null, 0, "error");
            }
        });
    }

    // ════════════════════════════════════════════════════════════════════
    //  checkAll — فحص شامل متسلسل (hash → face → name)
    // ════════════════════════════════════════════════════════════════════

    public static void checkAll(String imageHash, float[] embedding,
                                 String name, String governorate, int age,
                                 DuplicateCallback callback) {
        // المرحلة 1: hash
        checkByHash(imageHash, (isDup1, id1, sim1, reason1) -> {
            if (isDup1) { callback.onResult(true, id1, sim1, reason1); return; }

            // المرحلة 2: face
            checkByFace(embedding, (isDup2, id2, sim2, reason2) -> {
                if (isDup2) { callback.onResult(true, id2, sim2, reason2); return; }

                // المرحلة 3: name + details
                checkByNameAndDetails(name, governorate, age, callback);
            });
        });
    }

    // ════════════════════════════════════════════════════════════════════
    //  Levenshtein-based String Similarity
    // ════════════════════════════════════════════════════════════════════

    /**
     * حساب التشابه النصي بين سلسلتين (0.0 — 1.0)
     * باستخدام Levenshtein distance المُعيَّر
     */
    public static double stringSimilarity(String a, String b) {
        if (a == null || b == null) return 0.0;
        if (a.equals(b)) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;

        int distance = levenshtein(a, b);
        int maxLen   = Math.max(a.length(), b.length());
        return 1.0 - (double) distance / maxLen;
    }

    /**
     * Levenshtein distance — أقل عدد عمليات لتحويل a إلى b
     */
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
                    dp[i-1][j-1] + cost
                );
            }
        }
        return dp[la][lb];
    }
}
