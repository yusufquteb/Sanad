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
     * عتبة المطابقة الموحدة — مرتبطة بـ TFLiteFaceRecognizer
     * لا تُغيَّر هنا، غيِّرها في TFLiteFaceRecognizer.MATCH_THRESHOLD فقط.
     */
    private static float getMatchThreshold() {
        return TFLiteFaceRecognizer.MATCH_THRESHOLD; // 0.82f
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
