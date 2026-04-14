package com.missingpersons.app.data.repository;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * UserRepository — طبقة بيانات المستخدمين
 *
 * ══════════════════════════════════════════════════════
 * [إصلاح 2.5] إضافة:
 *   • getMatchedReportsCount(uid, callback) → عدد التطابقات المؤكدة
 *   • getResolvedReportsCount(uid, callback) → عدد الحالات المغلقة
 * ══════════════════════════════════════════════════════
 */
public class UserRepository {

    private static final String TAG = "UserRepository";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ════════════════════════════════════════════════════════
    //  Get User Data
    // ════════════════════════════════════════════════════════

    public void getUserData(String uid, OnUserDataCallback onSuccess, OnErrorCallback onError) {
        if (uid == null || uid.isEmpty()) {
            onError.onError("uid فارغ");
            return;
        }

        FirebaseDatabase.getInstance()
                .getReference("users")
                .child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snap) {
                        UserData data = new UserData();
                        data.uid      = uid;
                        data.name     = safeStr(snap, "name");
                        data.email    = safeStr(snap, "email");
                        data.photoUrl = safeStr(snap, "photoUrl");
                        data.role     = safeStr(snap, "role");
                        if (data.role.isEmpty()) data.role = "member";

                        Long pts = snap.child("points").getValue(Long.class);
                        data.points = pts != null ? pts.intValue() : 0;

                        Long joined = snap.child("createdAt").getValue(Long.class);
                        if (joined == null) joined = snap.child("joinDate").getValue(Long.class);
                        data.joinDate = joined != null ? joined : 0;

                        mainHandler.post(() -> onSuccess.onUserData(data));
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError e) {
                        Log.w(TAG, "getUserData cancelled: " + e.getMessage());
                        mainHandler.post(() -> onError.onError(e.getMessage()));
                    }
                });
    }

    // ════════════════════════════════════════════════════════
    //  Reports Count (total)
    // ════════════════════════════════════════════════════════

    public void getReportsCount(String uid, OnCountCallback callback) {
        if (uid == null || uid.isEmpty()) {
            mainHandler.post(() -> callback.onCount(0));
            return;
        }

        // عدّ من reports + found_persons + sightings
        String[] nodes = {"reports", "found_persons", "sightings"};
        AtomicInteger pending = new AtomicInteger(nodes.length);
        AtomicInteger total   = new AtomicInteger(0);

        for (String node : nodes) {
            FirebaseDatabase.getInstance()
                    .getReference(node)
                    .orderByChild("reporterId")
                    .equalTo(uid)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snap) {
                            total.addAndGet((int) snap.getChildrenCount());
                            if (pending.decrementAndGet() == 0)
                                mainHandler.post(() -> callback.onCount(total.get()));
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError e) {
                            Log.w(TAG, "getReportsCount cancelled: " + e.getMessage());
                            if (pending.decrementAndGet() == 0)
                                mainHandler.post(() -> callback.onCount(total.get()));
                        }
                    });
        }
    }

    // ════════════════════════════════════════════════════════
    //  [إصلاح 2.5] Matched Reports Count
    // ════════════════════════════════════════════════════════

    public void getMatchedReportsCount(String uid, OnCountCallback callback) {
        if (uid == null || uid.isEmpty()) {
            mainHandler.post(() -> callback.onCount(0));
            return;
        }

        // عدّ التطابقات المؤكدة من matches/ حيث reporterUid أو finderUid == uid
        AtomicInteger pending = new AtomicInteger(2);
        AtomicInteger total   = new AtomicInteger(0);

        // كـ صاحب بلاغ المفقود
        FirebaseDatabase.getInstance()
                .getReference("matches")
                .orderByChild("reporterUid")
                .equalTo(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snap) {
                        for (DataSnapshot c : snap.getChildren()) {
                            String status = c.child("status").getValue(String.class);
                            if ("confirmed".equals(status)) total.incrementAndGet();
                        }
                        if (pending.decrementAndGet() == 0)
                            mainHandler.post(() -> callback.onCount(total.get()));
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {
                        if (pending.decrementAndGet() == 0)
                            mainHandler.post(() -> callback.onCount(total.get()));
                    }
                });

        // كـ مُبلِّغ عن العثور
        FirebaseDatabase.getInstance()
                .getReference("matches")
                .orderByChild("finderUid")
                .equalTo(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snap) {
                        for (DataSnapshot c : snap.getChildren()) {
                            String status = c.child("status").getValue(String.class);
                            if ("confirmed".equals(status)) total.incrementAndGet();
                        }
                        if (pending.decrementAndGet() == 0)
                            mainHandler.post(() -> callback.onCount(total.get()));
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {
                        if (pending.decrementAndGet() == 0)
                            mainHandler.post(() -> callback.onCount(total.get()));
                    }
                });
    }

    // ════════════════════════════════════════════════════════
    //  [إصلاح 2.5] Resolved Reports Count
    // ════════════════════════════════════════════════════════

    public void getResolvedReportsCount(String uid, OnCountCallback callback) {
        if (uid == null || uid.isEmpty()) {
            mainHandler.post(() -> callback.onCount(0));
            return;
        }

        FirebaseDatabase.getInstance()
                .getReference("reports")
                .orderByChild("reporterId")
                .equalTo(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snap) {
                        int count = 0;
                        for (DataSnapshot c : snap.getChildren()) {
                            String status = c.child("status").getValue(String.class);
                            if ("resolved".equals(status) || "matched".equals(status))
                                count++;
                        }
                        final int finalCount = count;
                        mainHandler.post(() -> callback.onCount(finalCount));
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {
                        mainHandler.post(() -> callback.onCount(0));
                    }
                });
    }

    // ════════════════════════════════════════════════════════
    //  Update FCM Token
    // ════════════════════════════════════════════════════════

    public void updateFcmToken(String uid, String token) {
        if (uid == null || token == null) return;
        Map<String, Object> update = new HashMap<>();
        update.put("fcmToken", token);
        update.put("tokenUpdatedAt", System.currentTimeMillis());
        FirebaseDatabase.getInstance()
                .getReference("users")
                .child(uid)
                .updateChildren(update)
                .addOnSuccessListener(v -> Log.d(TAG, "FCM token updated"))
                .addOnFailureListener(e -> Log.w(TAG, "FCM token update failed: " + e.getMessage()));
    }

    // ════════════════════════════════════════════════════════
    //  Data Models
    // ════════════════════════════════════════════════════════

    public static class UserData {
        public String uid      = "";
        public String name     = "";
        public String email    = "";
        public String photoUrl = "";
        public String role     = "member";
        public int    points   = 0;
        public long   joinDate = 0;
    }

    // ════════════════════════════════════════════════════════
    //  Callbacks
    // ════════════════════════════════════════════════════════

    public interface OnUserDataCallback {
        void onUserData(UserData data);
    }

    public interface OnErrorCallback {
        void onError(String error);
    }

    public interface OnCountCallback {
        void onCount(int count);
    }

    // ════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════

    private String safeStr(DataSnapshot s, String key) {
        Object v = s.child(key).getValue();
        return v != null ? v.toString() : "";
    }
}
