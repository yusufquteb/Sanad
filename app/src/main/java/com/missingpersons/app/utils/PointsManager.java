package com.missingpersons.app.utils;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.util.*;

/**
 * PointsManager — نظام نقاط المجتمع (Gamification)
 *
 * يحفّز المستخدمين على المشاركة من خلال نقاط ومكافآت.
 *
 * ═══════════════════════════════════════════════
 * جدول النقاط:
 *   ACTION_REPORT_SUBMITTED    +10
 *   ACTION_REPORT_SHARED       +5
 *   ACTION_FOUND_PERSON        +20
 *   ACTION_SIGHTING_REPORTED   +30
 *   ACTION_MATCH_CONFIRMED     +50
 *   ACTION_CASE_RESOLVED       +200
 *   ACTION_ABUSE_REPORTED      +5
 *   ACTION_PENALTY_FAKE_REPORT -5
 *
 * Firebase structure:
 *   user_points/{uid}/total: 350
 *   user_points/{uid}/history/{pushId}: {action, points, note, timestamp}
 * ═══════════════════════════════════════════════
 */
public class PointsManager {

    private static final String TAG = "PointsManager";

    // ─── ثوابت الأحداث ────────────────────────────────────────────
    public static final String ACTION_REPORT_SUBMITTED    = "report_submitted";
    public static final String ACTION_REPORT_SHARED       = "report_shared";
    public static final String ACTION_FOUND_PERSON        = "found_person";
    public static final String ACTION_SIGHTING_REPORTED   = "sighting_reported";
    public static final String ACTION_MATCH_CONFIRMED     = "match_confirmed";
    public static final String ACTION_CASE_RESOLVED       = "case_resolved";
    public static final String ACTION_ABUSE_REPORTED      = "abuse_reported";
    public static final String ACTION_PENALTY_FAKE_REPORT = "penalty_fake_report";

    private static final Map<String, Integer> POINTS_MAP;
    static {
        POINTS_MAP = new HashMap<>();
        POINTS_MAP.put(ACTION_REPORT_SUBMITTED,    10);
        POINTS_MAP.put(ACTION_REPORT_SHARED,        5);
        POINTS_MAP.put(ACTION_FOUND_PERSON,        20);
        POINTS_MAP.put(ACTION_SIGHTING_REPORTED,   30);
        POINTS_MAP.put(ACTION_MATCH_CONFIRMED,     50);
        POINTS_MAP.put(ACTION_CASE_RESOLVED,      200);
        POINTS_MAP.put(ACTION_ABUSE_REPORTED,       5);
        POINTS_MAP.put(ACTION_PENALTY_FAKE_REPORT, -5);
    }

    // ─── إضافة نقاط للمستخدم الحالي ─────────────────────────────

    /**
     * إضافة نقاط للمستخدم المسجّل حالياً
     */
    public static void addPoints(String action, String note) {
        String uid = getCurrentUid();
        if (uid == null) return;
        Integer pts = POINTS_MAP.get(action);
        if (pts == null) { Log.w(TAG, "Unknown action: " + action); return; }
        addPointsInternal(uid, action, pts, note);
    }

    /**
     * إضافة نقاط لمستخدم محدد بالـ uid (للأدمن)
     * يُحدد عدد النقاط تلقائياً من POINTS_MAP
     */
    public static void addPointsForUser(String uid, String action, String note) {
        if (uid == null || uid.isEmpty()) return;
        Integer pts = POINTS_MAP.get(action);
        if (pts == null) { Log.w(TAG, "Unknown action: " + action); return; }
        addPointsInternal(uid, action, pts, note);
    }

    // ─── قراءة نقاط المستخدم ─────────────────────────────────────

    public interface PointsCallback {
        void onResult(int totalPoints, String rank);
    }

    public static void getMyPoints(PointsCallback callback) {
        String uid = getCurrentUid();
        if (uid == null) { callback.onResult(0, "مبتدئ"); return; }
        getUserPoints(uid, callback);
    }

    public static void getUserPoints(String uid, PointsCallback callback) {
        FirebaseDatabase.getInstance()
            .getReference("user_points").child(uid).child("total")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snap) {
                    Integer pts = snap.getValue(Integer.class);
                    int total = pts != null ? pts : 0;
                    callback.onResult(total, getRank(total));
                }
                @Override
                public void onCancelled(@NonNull DatabaseError e) {
                    callback.onResult(0, "مبتدئ");
                }
            });
    }

    // ─── لوحة الأوائل ────────────────────────────────────────────

    public interface LeaderboardCallback {
        void onResult(List<LeaderboardEntry> entries);
    }

    public static void getLeaderboard(LeaderboardCallback callback) {
        FirebaseDatabase.getInstance()
            .getReference("user_points")
            .orderByChild("total")
            .limitToLast(10)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snap) {
                    List<LeaderboardEntry> list = new ArrayList<>();
                    for (DataSnapshot c : snap.getChildren()) {
                        Integer pts  = c.child("total").getValue(Integer.class);
                        String  name = c.child("displayName").getValue(String.class);
                        // نعكس الترتيب (Firebase يرجع تصاعدي)
                        list.add(0, new LeaderboardEntry(
                            c.getKey(),
                            name != null ? name : "مستخدم",
                            pts  != null ? pts  : 0));
                    }
                    callback.onResult(list);
                }
                @Override
                public void onCancelled(@NonNull DatabaseError e) {
                    callback.onResult(new ArrayList<>());
                }
            });
    }

    // ─── الرتبة ───────────────────────────────────────────────────

    public static String getRank(int points) {
        if (points >= 1000) return "🥇 بطل الأبطال";
        if (points >= 500)  return "🥈 محقق بارز";
        if (points >= 200)  return "🥉 متطوع نشيط";
        if (points >= 100)  return "⭐ مساهم";
        if (points >= 50)   return "🔰 مبادر";
        return "🌱 مبتدئ";
    }

    public static String getRankEmoji(int points) {
        if (points >= 1000) return "🥇";
        if (points >= 500)  return "🥈";
        if (points >= 200)  return "🥉";
        if (points >= 100)  return "⭐";
        if (points >= 50)   return "🔰";
        return "🌱";
    }

    // ─── Model ────────────────────────────────────────────────────

    public static class LeaderboardEntry {
        public final String uid, displayName;
        public final int    points;

        public LeaderboardEntry(String uid, String name, int pts) {
            this.uid = uid; this.displayName = name; this.points = pts;
        }
    }

    // ─── Internal Helpers ────────────────────────────────────────

    private static void addPointsInternal(String uid, String action, int points, String note) {
        DatabaseReference userPointsRef = FirebaseDatabase.getInstance()
            .getReference("user_points").child(uid);
        DatabaseReference totalRef = userPointsRef.child("total");

        totalRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData data) {
                Integer current = data.getValue(Integer.class);
                int now = current != null ? current : 0;
                data.setValue(Math.max(0, now + points));
                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed,
                                   DataSnapshot snap) {
                if (committed) {
                    saveHistory(uid, action, points, note);
                    saveDisplayName(uid, userPointsRef);
                    // مزامنة النقاط إلى users/{uid}/points حتى تظهر في صفحة الملف الشخصي
                    Integer newTotal = snap.getValue(Integer.class);
                    if (newTotal != null) {
                        FirebaseDatabase.getInstance()
                            .getReference("users").child(uid).child("points")
                            .setValue(newTotal);
                    }
                    Log.i(TAG, points + " نقطة → " + uid + " (" + action + ") total=" + newTotal);
                } else if (error != null) {
                    Log.e(TAG, "Transaction failed: " + error.getMessage());
                }
            }
        });
    }

    /**
     * يحفظ displayName للمستخدم في user_points/{uid}/displayName
     * حتى تعمل getLeaderboard() بشكل صحيح.
     * يستخدم setValue مع priority لتجنب الكتابة إذا كان الاسم فارغاً.
     */
    private static void saveDisplayName(String uid, DatabaseReference userPointsRef) {
        com.google.firebase.auth.FirebaseUser user =
            FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String displayName = user.getDisplayName();
        if (displayName == null || displayName.isEmpty()) {
            // fallback: استخدم جزء من البريد الإلكتروني إن وُجد
            String email = user.getEmail();
            if (email != null && !email.isEmpty()) {
                displayName = email.split("@")[0];
            } else {
                displayName = "مستخدم " + uid.substring(0, 4);
            }
        }
        userPointsRef.child("displayName").setValue(displayName);
    }

    private static void saveHistory(String uid, String action, int points, String note) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("action",    action);
        entry.put("points",    points);
        entry.put("note",      note != null ? note : "");
        entry.put("timestamp", System.currentTimeMillis());

        FirebaseDatabase.getInstance()
            .getReference("user_points").child(uid).child("history")
            .push().setValue(entry);
    }

    private static String getCurrentUid() {
        com.google.firebase.auth.FirebaseUser user =
            FirebaseAuth.getInstance().getCurrentUser();
        return (user != null && !user.isAnonymous()) ? user.getUid() : null;
    }
}
