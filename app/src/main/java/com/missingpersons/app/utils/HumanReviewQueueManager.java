package com.missingpersons.app.utils;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HumanReviewQueueManager — إدارة طابور المراجعة البشرية.
 *
 * يُستخدم عندما تكون DynamicThresholdEngine.MatchStatus == REVIEW_REQUIRED:
 *   - تحميل العناصر المعلقة من review_queue
 *   - قبول / رفض كل عنصر
 *   - عند القبول: إرسال إشعار لصاحب البلاغ + تحديث حالة المطابقة
 *   - عند الرفض: حذف العنصر من الطابور
 *
 * Admin/Manager only.
 */
public final class HumanReviewQueueManager {

    private static final String TAG        = "HumanReviewQueueManager";
    private static final String NODE_QUEUE = "review_queue";

    private HumanReviewQueueManager() {}

    // ── Callbacks ─────────────────────────────────────────────────────────

    public interface QueueLoadCallback {
        void onLoaded(List<ReviewItem> items);
        void onError(String reason);
    }

    public interface ActionCallback {
        void onSuccess(String itemId);
        void onError(String reason);
    }

    // ── Load ──────────────────────────────────────────────────────────────

    /**
     * تحميل جميع العناصر المعلقة من review_queue.
     */
    public static void loadPendingItems(QueueLoadCallback callback) {
        FirebaseDatabase.getInstance().getReference(NODE_QUEUE)
            .orderByChild("status").equalTo("pending")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snap) {
                    List<ReviewItem> items = new ArrayList<>();
                    for (DataSnapshot child : snap.getChildren()) {
                        ReviewItem item = parseItem(child);
                        if (item != null) items.add(item);
                    }
                    items.sort((a, b) -> Double.compare(b.similarity, a.similarity));
                    Log.d(TAG, "loadPendingItems: " + items.size() + " items");
                    callback.onLoaded(items);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError e) {
                    callback.onError(e.getMessage());
                }
            });
    }

    // ── Approve ───────────────────────────────────────────────────────────

    /**
     * قبول العنصر: تحديث حالته → "approved"، إشعار صاحب البلاغ.
     */
    public static void approve(ReviewItem item, String reviewerUid, ActionCallback callback) {
        Log.d(TAG, "approve: " + item.itemId);

        Map<String, Object> update = new HashMap<>();
        update.put("status",     "approved");
        update.put("reviewedBy", reviewerUid);
        update.put("reviewedAt", System.currentTimeMillis());

        FirebaseDatabase.getInstance().getReference(NODE_QUEUE)
            .child(item.itemId).updateChildren(update)
            .addOnSuccessListener(v -> {
                Log.i(TAG, "✅ approved: " + item.itemId);
                updateMatchStatus(item, "confirmed");
                if (item.reporterUid != null && !item.reporterUid.isEmpty()) {
                    sendApprovalNotification(item);
                }
                callback.onSuccess(item.itemId);
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ── Reject ────────────────────────────────────────────────────────────

    /**
     * رفض العنصر: تحديث حالته → "rejected".
     */
    public static void reject(ReviewItem item, String reviewerUid, ActionCallback callback) {
        Log.d(TAG, "reject: " + item.itemId);

        Map<String, Object> update = new HashMap<>();
        update.put("status",     "rejected");
        update.put("reviewedBy", reviewerUid);
        update.put("reviewedAt", System.currentTimeMillis());

        FirebaseDatabase.getInstance().getReference(NODE_QUEUE)
            .child(item.itemId).updateChildren(update)
            .addOnSuccessListener(v -> {
                Log.i(TAG, "❌ rejected: " + item.itemId);
                updateMatchStatus(item, "rejected");
                callback.onSuccess(item.itemId);
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static void updateMatchStatus(ReviewItem item, String newStatus) {
        if (item.matchId == null || item.matchId.isEmpty()) return;
        FirebaseDatabase.getInstance().getReference("matches")
            .child(item.matchId).child("status").setValue(newStatus)
            .addOnFailureListener(e ->
                Log.w(TAG, "updateMatchStatus failed: " + e.getMessage()));
    }

    private static void sendApprovalNotification(ReviewItem item) {
        Map<String, Object> notif = new HashMap<>();
        notif.put("type",       "review_approved");
        notif.put("reportId",   item.reportId);
        notif.put("personName", item.personName);
        notif.put("similarity", (int)(item.similarity * 100));
        notif.put("message",    "✅ تمت الموافقة على تطابق محتمل للشخص "
            + item.personName + " — نسبة التشابه "
            + (int)(item.similarity * 100) + "%");
        notif.put("timestamp",  System.currentTimeMillis());
        notif.put("read",       false);

        FirebaseDatabase.getInstance()
            .getReference("notifications").child(item.reporterUid).push()
            .setValue(notif)
            .addOnFailureListener(e ->
                Log.w(TAG, "sendApprovalNotification failed: " + e.getMessage()));
    }

    private static ReviewItem parseItem(DataSnapshot snap) {
        try {
            ReviewItem item = new ReviewItem();
            item.itemId      = snap.getKey();
            item.type        = strVal(snap, "type");
            item.sightingId  = strVal(snap, "sightingId");
            item.reportId    = strVal(snap, "reportId");
            item.matchId     = strVal(snap, "matchId");
            item.personName  = strVal(snap, "personName");
            item.reporterUid = strVal(snap, "reporterUid");
            Double sim = snap.child("similarity").getValue(Double.class);
            item.similarity  = sim != null ? sim.floatValue() : 0f;
            Long ts = snap.child("timestamp").getValue(Long.class);
            item.timestamp   = ts != null ? ts : 0L;
            return item;
        } catch (Exception e) {
            Log.w(TAG, "parseItem failed: " + e.getMessage());
            return null;
        }
    }

    private static String strVal(DataSnapshot snap, String key) {
        Object v = snap.child(key).getValue();
        return (v instanceof String) ? (String) v : "";
    }

    // ── ReviewItem DTO ────────────────────────────────────────────────────

    public static final class ReviewItem {
        public String itemId;
        public String type;
        public String sightingId;
        public String reportId;
        public String matchId;
        public String personName;
        public String reporterUid;
        public float  similarity;
        public long   timestamp;

        public int getPercent() { return (int)(similarity * 100); }
    }
}
