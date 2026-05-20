package com.missingpersons.app.utils;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.database.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * CrossMatchManager — مقارنة تلقائية ثنائية الاتجاه
 *
 * [إصلاح جذري]
 *
 * المشاكل التي تم إصلاحها:
 *
 * 🔴 كان MATCH_THRESHOLD يُقرأ من FaceEmbeddingManager.MATCH_THRESHOLD
 *    التي كانت = 0.60f، وهي عتبة منخفضة جداً.
 *    الآن يُقرأ من TFLiteFaceRecognizer.MATCH_THRESHOLD (0.82f).
 *
 * 🔴 تم إضافة فحص أبعاد الـ embedding:
 *    إذا كان الـ embedding المخزن أبعاده != 128 → يُتجاهل تماماً.
 *    (البيانات القديمة في Firebase قد تحتوي على embeddings وهمية
 *    من النسخة السابقة التي كانت تضع Math.random())
 *
 * 🔴 تم رفع حد الـ similarity المعروض في اللوج للتمييز الواضح.
 */
public class CrossMatchManager {

    private static final String TAG = "CrossMatchManager";

    /**
     * عتبة المطابقة الموحدة — مصدر الحقيقة الوحيد هو FaceEmbeddingManager
     */
    private static float getMatchThreshold() {
        return FaceEmbeddingManager.MATCH_THRESHOLD; // 0.72f
    }

    /**
     * الحد الأدنى لأبعاد الـ embedding المقبول.
     * TFLite MobileFaceNet ينتج 128 float.
     * أي embedding بأبعاد أقل = وهمي من النسخة القديمة → يُرفض.
     */
    private static final int MIN_EMBEDDING_DIM = 128;

    private static final CopyOnWriteArrayList<DatabaseReference> activeRefs =
        new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<ValueEventListener> activeListeners =
        new CopyOnWriteArrayList<>();

    public interface MatchFoundCallback {
        void onMatchFound(String matchedId, String personName, float similarity);
    }

    // ════════════════════════════════════════════════════════
    //  matchReportWithFoundPersons
    // ════════════════════════════════════════════════════════

    public static void matchReportWithFoundPersons(String reportId, String reporterUid,
                                                    String personName, String embedding) {
        Log.d(TAG, "matchReportWithFoundPersons: reportId=" + reportId
                + " embNull=" + (embedding == null || embedding.isEmpty()));

        if (embedding == null || embedding.isEmpty()) {
            Log.w(TAG, "⚠️ embedding فارغة — لن يحدث match");
            return;
        }

        float[] reportVec = FaceEmbeddingManager.stringToEmbedding(embedding);
        if (reportVec == null) {
            Log.w(TAG, "⚠️ فشل parse embedding");
            return;
        }
        if (reportVec.length < MIN_EMBEDDING_DIM) {
            Log.w(TAG, "⚠️ embedding أبعاده " + reportVec.length
                    + " < " + MIN_EMBEDDING_DIM + " — يبدو وهمياً من نسخة قديمة، تجاهل");
            return;
        }
        Log.d(TAG, "✅ reportVec dim=" + reportVec.length
                + " threshold=" + getMatchThreshold());

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("found_persons");

        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                removeTracked(ref, this);
                long total = snapshot.getChildrenCount();
                Log.d(TAG, "🔍 found_persons candidates: " + total);
                if (total == 0) { Log.w(TAG, "⚠️ لا candidates"); return; }

                int compared = 0, skipped = 0, matched = 0;
                for (DataSnapshot child : snapshot.getChildren()) {
                    String storedEmb = child.child("faceEmbedding").getValue(String.class);
                    if (storedEmb == null || storedEmb.isEmpty()) { skipped++; continue; }

                    float[] storedVec = FaceEmbeddingManager.stringToEmbedding(storedEmb);
                    if (storedVec == null) { skipped++; continue; }

                    // ← الإصلاح الجذري: رفض embeddings الوهمية القديمة
                    if (storedVec.length < MIN_EMBEDDING_DIM) {
                        Log.w(TAG, "  [" + child.getKey() + "] dim=" + storedVec.length
                                + " < " + MIN_EMBEDDING_DIM + " → مُتجاهَل (embedding وهمي)");
                        skipped++;
                        continue;
                    }

                    float sim = FaceEmbeddingManager.cosineSimilarity(reportVec, storedVec);
                    compared++;
                    Log.d(TAG, "  [" + child.getKey() + "] score="
                            + String.format("%.3f", sim)
                            + " threshold=" + getMatchThreshold()
                            + (sim >= getMatchThreshold() ? " ✅ MATCH" : " ❌"));

                    if (sim >= getMatchThreshold()) {
                        matched++;
                        String foundReporterId = child.child("reporterId").getValue(String.class);
                        String foundId         = child.getKey();
                        int    percent         = (int)(sim * 100);
                        Log.i(TAG, "🎉 MATCH! " + reportId + " ↔ " + foundId
                                + " = " + percent + "%");

                        if (foundReporterId != null)
                            sendMatchNotif(foundReporterId,
                                "🔍 بلاغ مفقود جديد يطابق الشخص الذي وجدته!",
                                personName, percent, reportId, foundId, "report_matches_found");
                        if (reporterUid != null)
                            sendMatchNotif(reporterUid,
                                "🎉 شخص وجده مستخدم آخر يطابق المفقود!",
                                personName, percent, reportId, foundId, "found_matches_report");
                        sendAdminMatchNotif(personName, percent, reportId, foundId,
                                "report_matches_found");
                        saveMatchRecord(reportId, foundId, sim, reporterUid, foundReporterId);
                    }
                }
                Log.d(TAG, "done: compared=" + compared
                        + " skipped=" + skipped + " matched=" + matched);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError e) {
                removeTracked(ref, this);
                Log.e(TAG, "matchReportWithFoundPersons cancelled: " + e.getMessage());
            }
        };
        trackAndListen(ref, listener);
    }

    // ════════════════════════════════════════════════════════
    //  matchFoundPersonWithReports
    // ════════════════════════════════════════════════════════

    public static void matchFoundPersonWithReports(String foundId, String finderUid,
                                                    String embedding) {
        Log.d(TAG, "matchFoundPersonWithReports: foundId=" + foundId
                + " embNull=" + (embedding == null || embedding.isEmpty()));

        if (embedding == null || embedding.isEmpty()) {
            Log.w(TAG, "⚠️ embedding فارغة");
            return;
        }

        float[] foundVec = FaceEmbeddingManager.stringToEmbedding(embedding);
        if (foundVec == null) {
            Log.w(TAG, "⚠️ فشل parse embedding");
            return;
        }
        if (foundVec.length < MIN_EMBEDDING_DIM) {
            Log.w(TAG, "⚠️ embedding أبعاده " + foundVec.length
                    + " < " + MIN_EMBEDDING_DIM + " — يبدو وهمياً، تجاهل");
            return;
        }
        Log.d(TAG, "✅ foundVec dim=" + foundVec.length
                + " threshold=" + getMatchThreshold());

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("reports");
        Query query = ref.orderByChild("status").equalTo("approved");

        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                removeTracked(ref, this);
                long total = snapshot.getChildrenCount();
                Log.d(TAG, "🔍 approved reports candidates: " + total);
                if (total == 0) { Log.w(TAG, "⚠️ لا candidates"); return; }

                int compared = 0, skipped = 0, matched = 0;
                for (DataSnapshot child : snapshot.getChildren()) {
                    String storedEmb = child.child("faceEmbedding").getValue(String.class);
                    if (storedEmb == null || storedEmb.isEmpty()) { skipped++; continue; }

                    float[] storedVec = FaceEmbeddingManager.stringToEmbedding(storedEmb);
                    if (storedVec == null) { skipped++; continue; }

                    // ← الإصلاح الجذري: رفض embeddings الوهمية القديمة
                    if (storedVec.length < MIN_EMBEDDING_DIM) {
                        Log.w(TAG, "  [" + child.getKey() + "] dim=" + storedVec.length
                                + " < " + MIN_EMBEDDING_DIM + " → مُتجاهَل");
                        skipped++;
                        continue;
                    }

                    float sim = FaceEmbeddingManager.cosineSimilarity(foundVec, storedVec);
                    compared++;
                    Log.d(TAG, "  [" + child.getKey() + "] score="
                            + String.format("%.3f", sim)
                            + " threshold=" + getMatchThreshold()
                            + (sim >= getMatchThreshold() ? " ✅ MATCH" : " ❌"));

                    if (sim >= getMatchThreshold()) {
                        matched++;
                        String originalReporterId = child.child("reporterId").getValue(String.class);
                        String reportId           = child.getKey();
                        String personName         = child.child("personName").getValue(String.class);
                        int    percent            = (int)(sim * 100);
                        if (personName == null) personName = "مجهول";
                        Log.i(TAG, "🎉 MATCH! " + foundId + " ↔ " + reportId
                                + " = " + percent + "%");

                        if (originalReporterId != null)
                            sendMatchNotif(originalReporterId,
                                "🎉 شخص يشبه المفقود الذي بلّغت عنه تم العثور عليه!",
                                personName, percent, reportId, foundId, "found_matches_report");
                        if (finderUid != null)
                            sendMatchNotif(finderUid,
                                "🔍 الشخص الذي وجدته يطابق بلاغ مفقود!",
                                personName, percent, reportId, foundId, "report_matches_found");
                        sendAdminMatchNotif(personName, percent, reportId, foundId,
                                "found_matches_report");
                        saveMatchRecord(reportId, foundId, sim, originalReporterId, finderUid);
                    }
                }
                Log.d(TAG, "done: compared=" + compared
                        + " skipped=" + skipped + " matched=" + matched);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError e) {
                removeTracked(ref, this);
                Log.e(TAG, "matchFoundPersonWithReports cancelled: " + e.getMessage());
            }
        };
        query.addListenerForSingleValueEvent(listener);
    }

    // ════════════════════════════════════════════════════════
    //  matchReportWithOtherReports — MISSING vs MISSING
    //  يكتشف نفس الشخص المُبلَّغ عنه من أكثر من مستخدم
    // ════════════════════════════════════════════════════════

    public static void matchReportWithOtherReports(String newReportId, String reporterUid,
                                                    String personName, String embedding) {
        Log.d(TAG, "matchReportWithOtherReports: newReportId=" + newReportId);

        if (embedding == null || embedding.isEmpty()) return;

        float[] newVec = FaceEmbeddingManager.stringToEmbedding(embedding);
        if (newVec == null || newVec.length < MIN_EMBEDDING_DIM) {
            Log.w(TAG, "⚠️ embedding غير صالح — لن يحدث match");
            return;
        }

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("reports");

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int compared = 0, matched = 0;

                for (DataSnapshot child : snapshot.getChildren()) {
                    String otherId = child.getKey();
                    // لا تقارن البلاغ مع نفسه
                    if (newReportId.equals(otherId)) continue;

                    String storedEmb = child.child("faceEmbedding").getValue(String.class);
                    if (storedEmb == null || storedEmb.isEmpty()) continue;

                    float[] storedVec = FaceEmbeddingManager.stringToEmbedding(storedEmb);
                    if (storedVec == null || storedVec.length < MIN_EMBEDDING_DIM) continue;

                    float sim = FaceEmbeddingManager.cosineSimilarity(newVec, storedVec);
                    compared++;
                    Log.d(TAG, "  [" + otherId + "] score="
                            + String.format("%.3f", sim)
                            + (sim >= getMatchThreshold() ? " ✅ DUPLICATE" : " ❌"));

                    if (sim >= getMatchThreshold()) {
                        matched++;
                        String otherReporterId = child.child("reporterId").getValue(String.class);
                        String otherPersonName = child.child("personName").getValue(String.class);
                        if (otherPersonName == null || otherPersonName.isEmpty())
                            otherPersonName = personName;
                        int percent = (int)(sim * 100);

                        Log.i(TAG, "🔁 DUPLICATE REPORT! " + newReportId
                                + " ↔ " + otherId + " = " + percent + "%");

                        // إشعار مُقدِّم البلاغ الجديد
                        if (reporterUid != null)
                            sendMatchNotif(reporterUid,
                                "🔁 يوجد بلاغ مشابه لنفس الشخص!",
                                otherPersonName, percent, newReportId, otherId,
                                "duplicate_report");

                        // إشعار مُقدِّم البلاغ القديم
                        if (otherReporterId != null && !otherReporterId.equals(reporterUid))
                            sendMatchNotif(otherReporterId,
                                "🔁 مستخدم آخر أبلغ عن نفس الشخص الذي أبلغت عنه!",
                                otherPersonName, percent, otherId, newReportId,
                                "duplicate_report");

                        sendAdminMatchNotif(otherPersonName, percent, newReportId, otherId,
                                "duplicate_reports");
                        saveDuplicateRecord(newReportId, otherId, sim,
                                reporterUid, otherReporterId);
                    }
                }
                Log.d(TAG, "matchReportWithOtherReports done: compared="
                        + compared + " matched=" + matched);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError e) {
                Log.e(TAG, "matchReportWithOtherReports cancelled: " + e.getMessage());
            }
        });
    }

    // ════════════════════════════════════════════════════════
    //  matchSightingWithReports (V2+V3 + DynamicThreshold)
    // ════════════════════════════════════════════════════════

    public static void matchSightingWithReports(String sightingId, String sighterUid,
                                                 String embedding) {
        Log.d(TAG, "matchSightingWithReports: sightingId=" + sightingId);

        if (embedding == null || embedding.isEmpty()) {
            Log.w(TAG, "⚠️ embedding فارغة");
            return;
        }

        float[] sightingVec = FaceEmbeddingManager.stringToEmbedding(embedding);
        if (sightingVec == null || sightingVec.length < MIN_EMBEDDING_DIM) {
            Log.w(TAG, "⚠️ embedding غير صالح dim="
                + (sightingVec != null ? sightingVec.length : 0));
            return;
        }

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("reports");
        ref.orderByChild("status").equalTo("approved")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    int compared = 0, matched = 0;

                    for (DataSnapshot child : snapshot.getChildren()) {
                        float maxSim = 0f;
                        float storedQuality = 0f;

                        // V3: embeddings array
                        DataSnapshot embArr = child.child("embeddings");
                        if (embArr.exists()) {
                            for (DataSnapshot embSnap : embArr.getChildren()) {
                                String vecStr = embSnap.child("vector").getValue(String.class);
                                if (vecStr == null) continue;
                                float[] vec = FaceEmbeddingManager.stringToEmbedding(vecStr);
                                if (vec == null || vec.length < MIN_EMBEDDING_DIM) continue;
                                float sim = FaceEmbeddingManager.cosineSimilarity(sightingVec, vec);
                                if (sim > maxSim) maxSim = sim;
                                Double q = embSnap.child("qualityScore").getValue(Double.class);
                                if (q != null && q.floatValue() > storedQuality)
                                    storedQuality = q.floatValue();
                            }
                        }

                        // V2 fallback
                        if (maxSim == 0f) {
                            String legacyEmb = child.child("faceEmbedding").getValue(String.class);
                            if (legacyEmb != null && !legacyEmb.isEmpty()) {
                                float[] vec = FaceEmbeddingManager.stringToEmbedding(legacyEmb);
                                if (vec != null && vec.length >= MIN_EMBEDDING_DIM)
                                    maxSim = FaceEmbeddingManager.cosineSimilarity(sightingVec, vec);
                            }
                        }

                        if (maxSim < 0.55f) continue;
                        compared++;

                        float threshold = DynamicThresholdEngine.computeThreshold(0.5f, storedQuality);
                        DynamicThresholdEngine.MatchStatus status =
                            DynamicThresholdEngine.classify(maxSim, threshold, 0.5f);

                        if (status == DynamicThresholdEngine.MatchStatus.INSUFFICIENT_QUALITY
                                || status == DynamicThresholdEngine.MatchStatus.AUTO_NO_MATCH) continue;

                        String reporterUid = child.child("reporterId").getValue(String.class);
                        String reportId    = child.getKey();
                        String personName  = strVal(child, "personName");
                        if (personName.isEmpty()) personName = "مجهول";
                        int percent = (int)(maxSim * 100);
                        matched++;

                        if (status == DynamicThresholdEngine.MatchStatus.AUTO_MATCH) {
                            Log.i(TAG, "🎉 sighting AUTO_MATCH: " + sightingId
                                + " ↔ " + reportId + " = " + percent + "%");
                            if (reporterUid != null)
                                notifySightingMatch(reporterUid, personName, percent, reportId);
                            saveSightingMatchRecord(sightingId, reportId, maxSim,
                                sighterUid, reporterUid, "confirmed");
                        } else {
                            Log.i(TAG, "⚠️ sighting REVIEW_REQUIRED: " + sightingId
                                + " ↔ " + reportId + " = " + percent + "%");
                            saveSightingMatchRecord(sightingId, reportId, maxSim,
                                sighterUid, reporterUid, "pending_verification");
                            saveToReviewQueue(sightingId, reportId, maxSim, personName);
                        }
                    }
                    Log.d(TAG, "matchSightingWithReports done: compared="
                        + compared + " matched=" + matched);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError e) {
                    Log.e(TAG, "matchSightingWithReports cancelled: " + e.getMessage());
                }
            });
    }

    // ════════════════════════════════════════════════════════
    //  notifySightingMatch
    // ════════════════════════════════════════════════════════

    public static void notifySightingMatch(String reporterUid, String personName,
                                            int percent, String reportId) {
        Log.d(TAG, "notifySightingMatch: uid=" + reporterUid
                + " person=" + personName + " percent=" + percent);

        if (reporterUid == null || reporterUid.isEmpty()) return;

        HashMap<String, Object> notif = new HashMap<>();
        notif.put("type",       "sighting_match");
        notif.put("reportId",   reportId);
        notif.put("personName", personName);
        notif.put("similarity", percent);
        notif.put("message",    "👁️ شخص رأى ما قد يكون " + personName
                                + " بنسبة تطابق " + percent + "% — تحقق من التفاصيل");
        notif.put("timestamp",  System.currentTimeMillis());
        notif.put("read",       false);

        FirebaseDatabase.getInstance()
            .getReference("notifications")
            .child(reporterUid).push().setValue(notif)
            .addOnSuccessListener(v -> Log.d(TAG, "✅ sighting notif sent"))
            .addOnFailureListener(e -> Log.w(TAG, "sighting notif failed: " + e.getMessage()));

        HashMap<String, Object> sightingMatch = new HashMap<>();
        sightingMatch.put("type",       "sighting");
        sightingMatch.put("reportId",   reportId);
        sightingMatch.put("similarity", percent / 100f);
        sightingMatch.put("timestamp",  System.currentTimeMillis());
        sightingMatch.put("status",     "pending_verification");

        FirebaseDatabase.getInstance()
            .getReference("matches").push().setValue(sightingMatch);
    }

    // ════════════════════════════════════════════════════════
    //  cancelAll
    // ════════════════════════════════════════════════════════

    public static void cancelAll() {
        Log.d(TAG, "cancelAll: " + activeListeners.size() + " listeners");
        for (int i = 0; i < activeRefs.size() && i < activeListeners.size(); i++) {
            try { activeRefs.get(i).removeEventListener(activeListeners.get(i)); }
            catch (Exception ignored) {}
        }
        activeRefs.clear();
        activeListeners.clear();
    }

    // ════════════════════════════════════════════════════════
    //  Private helpers
    // ════════════════════════════════════════════════════════

    private static void trackAndListen(DatabaseReference ref, ValueEventListener l) {
        activeRefs.add(ref);
        activeListeners.add(l);
        ref.addListenerForSingleValueEvent(l);
    }

    private static void removeTracked(DatabaseReference ref, ValueEventListener l) {
        int idx = activeListeners.indexOf(l);
        if (idx >= 0 && idx < activeRefs.size()) {
            activeRefs.remove(idx);
            activeListeners.remove(idx);
        }
    }

    private static void saveMatchRecord(String reportId, String foundId,
                                         float similarity, String reporterUid,
                                         String finderUid) {
        FirebaseDatabase.getInstance().getReference("reports").child(reportId)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot rSnap) {
                    String reportPhoto  = strVal(rSnap, "photoUrl");
                    String personName   = strVal(rSnap, "personName");
                    String shortReportId = reportId.length() > 4
                        ? "SND-" + reportId.substring(reportId.length() - 4).toUpperCase()
                        : reportId;

                    FirebaseDatabase.getInstance()
                        .getReference("found_persons").child(foundId)
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override public void onDataChange(@NonNull DataSnapshot fSnap) {
                                String foundPhoto  = strVal(fSnap, "photoUrl");
                                String finderName  = strVal(fSnap, "name");
                                String shortFoundId = foundId.length() > 4
                                    ? "SND-" + foundId.substring(foundId.length() - 4).toUpperCase()
                                    : foundId;

                                Map<String, Object> match = new HashMap<>();
                                match.put("reportId",       reportId);
                                match.put("foundId",        foundId);
                                match.put("similarity",     similarity);
                                match.put("reporterUid",    reporterUid);
                                match.put("finderUid",      finderUid);
                                match.put("status",         "pending_review");
                                match.put("timestamp",      System.currentTimeMillis());
                                match.put("reportPhotoUrl", reportPhoto);
                                match.put("foundPhotoUrl",  foundPhoto);
                                match.put("personName",     personName);
                                match.put("finderName",     finderName.isEmpty() ? "معثور" : finderName);
                                match.put("shortReportId",  shortReportId);
                                match.put("shortFoundId",   shortFoundId);

                                String key = FirebaseDatabase.getInstance()
                                    .getReference("matches").push().getKey();
                                if (key != null) {
                                    FirebaseDatabase.getInstance()
                                        .getReference("matches").child(key)
                                        .setValue(match)
                                        .addOnSuccessListener(v -> Log.d(TAG, "✅ match record: " + key))
                                        .addOnFailureListener(e -> Log.e(TAG, "match record failed: " + e.getMessage()));
                                }
                            }
                            @Override public void onCancelled(@NonNull DatabaseError e) {
                                Log.w(TAG, "saveMatchRecord: found_persons read failed");
                            }
                        });
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {
                    Log.w(TAG, "saveMatchRecord: reports read failed");
                }
            });
    }

    private static String strVal(DataSnapshot snap, String key) {
        Object v = snap.child(key).getValue();
        return (v instanceof String) ? (String) v : "";
    }

    private static void sendMatchNotif(String uid, String message, String personName,
                                        int percent, String reportId, String foundId,
                                        String type) {
        HashMap<String, Object> n = new HashMap<>();
        n.put("type",       type);
        n.put("message",    message);
        n.put("personName", personName);
        n.put("percent",    percent);
        n.put("reportId",   reportId);
        n.put("foundId",    foundId);
        n.put("timestamp",  System.currentTimeMillis());
        n.put("read",       false);
        FirebaseDatabase.getInstance()
                .getReference("notifications").child(uid).push().setValue(n);
    }

    private static void saveSightingMatchRecord(String sightingId, String reportId,
                                                 float similarity, String sighterUid,
                                                 String reporterUid, String status) {
        Map<String, Object> match = new HashMap<>();
        match.put("type",        "sighting");
        match.put("sightingId",  sightingId);
        match.put("reportId",    reportId);
        match.put("similarity",  similarity);
        match.put("sighterUid",  sighterUid != null ? sighterUid : "");
        match.put("reporterUid", reporterUid != null ? reporterUid : "");
        match.put("status",      status);
        match.put("timestamp",   System.currentTimeMillis());

        String key = FirebaseDatabase.getInstance()
            .getReference("matches").push().getKey();
        if (key != null) {
            FirebaseDatabase.getInstance()
                .getReference("matches").child(key)
                .setValue(match)
                .addOnSuccessListener(v -> Log.d(TAG, "✅ sighting match record: " + key))
                .addOnFailureListener(e -> Log.e(TAG,
                    "sighting match record failed: " + e.getMessage()));
        }
    }

    private static void saveToReviewQueue(String sightingId, String reportId,
                                           float similarity, String personName) {
        Map<String, Object> review = new HashMap<>();
        review.put("type",       "sighting_review");
        review.put("sightingId", sightingId);
        review.put("reportId",   reportId);
        review.put("similarity", similarity);
        review.put("personName", personName);
        review.put("status",     "pending");
        review.put("timestamp",  System.currentTimeMillis());

        FirebaseDatabase.getInstance()
            .getReference("review_queue").push().setValue(review)
            .addOnSuccessListener(v -> Log.d(TAG, "✅ review queue entry added"))
            .addOnFailureListener(e -> Log.e(TAG,
                "review queue failed: " + e.getMessage()));
    }

    private static void saveDuplicateRecord(String reportId1, String reportId2,
                                              float similarity, String uid1, String uid2) {
        Map<String, Object> dup = new HashMap<>();
        dup.put("type",       "duplicate");
        dup.put("reportId1",  reportId1);
        dup.put("reportId2",  reportId2);
        dup.put("similarity", similarity);
        dup.put("uid1",       uid1 != null ? uid1 : "");
        dup.put("uid2",       uid2 != null ? uid2 : "");
        dup.put("status",     "pending_review");
        dup.put("timestamp",  System.currentTimeMillis());

        String key = FirebaseDatabase.getInstance()
            .getReference("duplicate_reports").push().getKey();
        if (key != null) {
            FirebaseDatabase.getInstance()
                .getReference("duplicate_reports").child(key)
                .setValue(dup)
                .addOnSuccessListener(v -> Log.d(TAG, "✅ duplicate record: " + key))
                .addOnFailureListener(e -> Log.w(TAG,
                    "duplicate record failed: " + e.getMessage()));
        }
    }

    private static void sendAdminMatchNotif(String personName, int percent,
                                             String reportId, String foundId,
                                             String type) {
        HashMap<String, Object> n = new HashMap<>();
        n.put("type",       "admin_match_" + type);
        n.put("personName", personName);
        n.put("percent",    percent);
        n.put("reportId",   reportId);
        n.put("foundId",    foundId);
        n.put("timestamp",  System.currentTimeMillis());
        n.put("read",       false);
        FirebaseDatabase.getInstance().getReference("admin_notifications").push().setValue(n);
    }
}
