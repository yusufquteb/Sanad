package com.missingpersons.app.utils;

import androidx.annotation.NonNull;
import com.google.firebase.database.*;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * StatsCache — المرحلة 3: توحيد معمارية البيانات
 *
 * يكتب node مخصص `stats/` في Firebase بعد كل موافقة Admin أو تغيير حالة،
 * بحيث لا تحتاج الـ NewHomeActivity و StatisticsActivity لقراءة كل البيانات
 * في كل مرة — بل تقرأ من `stats/` مباشرة (fast single-read).
 *
 * البنية:
 *   stats/
 *     total          : عدد كل البلاغات
 *     approved       : عدد المعتمدة
 *     resolved       : عدد المحلولة
 *     pending        : عدد قيد الانتظار
 *     resolvedMonth  : محلولة هذا الشهر
 *     male           : ذكور
 *     female         : إناث
 *     byGov/         : { "القاهرة": 12, "الجيزة": 7, ... }
 *     lastUpdated    : timestamp آخر تحديث
 *
 * الاستخدام:
 *   StatsCache.rebuild();  // بعد كل approve/resolve في AdminActivity
 */
public class StatsCache {

    private static final String STATS_NODE = "stats";
    private static final String[] SOURCE_NODES = {"reports", "found_persons", "sightings"};

    /**
     * يُعيد بناء كل الـ stats من الـ source nodes ويكتبها في `stats/`.
     * استدعِها بعد كل موافقة أو تغيير حالة.
     */
    public static void rebuild() {
        DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        int[] remaining = {SOURCE_NODES.length};

        long[] total = {0}, approved = {0}, resolved = {0}, pending = {0};
        long[] resolvedMonth = {0}, male = {0}, female = {0};
        Map<String, Integer> govMap = new HashMap<>();
        long monthStart = getThisMonthStart();

        for (String node : SOURCE_NODES) {
            db.child(node).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snap) {
                    for (DataSnapshot c : snap.getChildren()) {
                        total[0]++;
                        String status = str(c, "status");
                        String gender = str(c, "personGender");
                        if (gender.isEmpty()) gender = str(c, "gender");
                        String gov    = str(c, "governorate");
                        Long   ts     = c.child("timestamp").getValue(Long.class);

                        if ("approved".equals(status))  approved[0]++;
                        if ("resolved".equals(status)) {
                            resolved[0]++;
                            Long rTs = c.child("resolvedAt").getValue(Long.class);
                            if (rTs == null) rTs = ts;
                            if (rTs != null && rTs >= monthStart) resolvedMonth[0]++;
                        }
                        if ("pending".equals(status) || "pending_edit".equals(status)) pending[0]++;

                        boolean isMale   = "ذكر".equals(gender)  || "male".equals(gender);
                        boolean isFemale = "أنثى".equals(gender) || "female".equals(gender);
                        if (isMale)   male[0]++;
                        if (isFemale) female[0]++;

                        if (!gov.isEmpty())
                            govMap.put(gov, govMap.getOrDefault(gov, 0) + 1);
                    }
                    remaining[0]--;
                    if (remaining[0] == 0) writeCache(db, total[0], approved[0],
                        resolved[0], pending[0], resolvedMonth[0],
                        male[0], female[0], govMap);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError e) {
                    remaining[0]--;
                }

                private String str(DataSnapshot c, String key) {
                    String v = c.child(key).getValue(String.class);
                    return v != null ? v : "";
                }
            });
        }
    }

    /**
     * قراءة سريعة للإحصائيات المحفوظة (single read من stats/).
     * استخدمها في NewHomeActivity بدل قراءة كل الـ nodes.
     */
    public static void readCached(OnStatsCacheLoaded callback) {
        FirebaseDatabase.getInstance().getReference(STATS_NODE)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snap) {
                    if (!snap.exists()) {
                        // لا يوجد cache — أعِد البناء ثم اتصل مجدداً
                        rebuild();
                        callback.onLoaded(0, 0, 0, 0);
                        return;
                    }
                    long total    = longVal(snap, "total");
                    long res      = longVal(snap, "resolved");
                    long approved = longVal(snap, "approved");
                    long today    = longVal(snap, "resolvedMonth");
                    callback.onLoaded(total, approved, res, today);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError e) {
                    callback.onLoaded(0, 0, 0, 0);
                }

                private long longVal(DataSnapshot s, String key) {
                    Object v = s.child(key).getValue();
                    if (v instanceof Long)    return (Long) v;
                    if (v instanceof Integer) return ((Integer) v).longValue();
                    return 0L;
                }
            });
    }

    /** يُحدِّث حقل واحد فقط بدل إعادة البناء الكاملة (أسرع بعد approve واحد) */
    public static void incrementApproved() {
        DatabaseReference statsRef = FirebaseDatabase.getInstance()
            .getReference(STATS_NODE);
        statsRef.child("approved").runTransaction(new Transaction.Handler() {
            @NonNull @Override
            public Transaction.Result doTransaction(@NonNull MutableData d) {
                Long val = d.getValue(Long.class);
                d.setValue(val == null ? 1L : val + 1);
                return Transaction.success(d);
            }
            @Override public void onComplete(DatabaseError e, boolean b, DataSnapshot s) {}
        });
        statsRef.child("total").runTransaction(new Transaction.Handler() {
            @NonNull @Override
            public Transaction.Result doTransaction(@NonNull MutableData d) {
                Long val = d.getValue(Long.class);
                d.setValue(val == null ? 1L : val + 1);
                return Transaction.success(d);
            }
            @Override public void onComplete(DatabaseError e, boolean b, DataSnapshot s) {}
        });
        statsRef.child("lastUpdated").setValue(System.currentTimeMillis());
    }

    /** يُحدِّث حقل resolved + resolvedMonth */
    public static void incrementResolved() {
        DatabaseReference statsRef = FirebaseDatabase.getInstance()
            .getReference(STATS_NODE);
        statsRef.child("resolved").runTransaction(new Transaction.Handler() {
            @NonNull @Override
            public Transaction.Result doTransaction(@NonNull MutableData d) {
                Long val = d.getValue(Long.class);
                d.setValue(val == null ? 1L : val + 1);
                return Transaction.success(d);
            }
            @Override public void onComplete(DatabaseError e, boolean b, DataSnapshot s) {}
        });
        statsRef.child("resolvedMonth").runTransaction(new Transaction.Handler() {
            @NonNull @Override
            public Transaction.Result doTransaction(@NonNull MutableData d) {
                Long val = d.getValue(Long.class);
                d.setValue(val == null ? 1L : val + 1);
                return Transaction.success(d);
            }
            @Override public void onComplete(DatabaseError e, boolean b, DataSnapshot s) {}
        });
        statsRef.child("lastUpdated").setValue(System.currentTimeMillis());
    }

    // ── Private helpers ──────────────────────────────────────────

    private static void writeCache(DatabaseReference db,
            long total, long approved, long resolved, long pending,
            long resolvedMonth, long male, long female,
            Map<String, Integer> govMap) {

        Map<String, Object> stats = new HashMap<>();
        stats.put("total",         total);
        stats.put("approved",      approved);
        stats.put("resolved",      resolved);
        stats.put("pending",       pending);
        stats.put("resolvedMonth", resolvedMonth);
        stats.put("male",          male);
        stats.put("female",        female);
        stats.put("byGov",         govMap);
        stats.put("lastUpdated",   System.currentTimeMillis());

        db.child(STATS_NODE).setValue(stats);
    }

    private static long getThisMonthStart() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    // ── Callback interface ───────────────────────────────────────

    public interface OnStatsCacheLoaded {
        /** @param active   البلاغات المعتمدة
         *  @param resolved البلاغات المحلولة
         *  @param today    المحلولة هذا الشهر
         */
        void onLoaded(long total, long active, long resolved, long today);
    }
}
