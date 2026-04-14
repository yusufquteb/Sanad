package com.missingpersons.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * RateLimiter — حدود البلاغات والرسائل
 *
 * - Admin: بلاغات غير محدودة
 * - Members/Managers: حد يومي قابل للتعديل من الأدمن (افتراضي 5)
 * - الحد يُجلب من Firebase: settings/daily_report_limit
 * - الأدمن يضبطه عبر: setGlobalDailyLimit(newLimit)
 */
public class RateLimiter {

    private static final String TAG       = "RateLimiter";
    private static final String PREF_NAME = "rate_limiter";

    public static final int DEFAULT_MAX_REPORTS    = 5;
    public static final int MAX_MESSAGES_PER_HOUR  = 60;
    public static final int MAX_ABUSE_PER_DAY      = 3;

    private static final long ONE_DAY_MS  = 24 * 60 * 60 * 1000L;
    private static final long ONE_HOUR_MS = 60 * 60 * 1000L;

    // ════════════════════════════════════════════════════════════════════
    //  Role management
    // ════════════════════════════════════════════════════════════════════

    /** يُستدعى عند تحميل صلاحية المستخدم (HomeActivity أو SplashActivity) */
    public static void setUserRole(Context ctx, String role) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString("user_role", role).apply();
    }

    public static boolean isAdmin(Context ctx) {
        String role = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString("user_role", "member");
        return "admin".equals(role);
    }

    // ════════════════════════════════════════════════════════════════════
    //  Daily limit — يُجلب من Firebase ويُخزَّن محلياً كـ cache
    // ════════════════════════════════════════════════════════════════════

    /** يُستدعى عند بدء التطبيق — يحدّث الـ cache من Firebase */
    public static void fetchAndCacheDailyLimit(Context ctx) {
        FirebaseDatabase.getInstance().getReference("settings/daily_report_limit")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(DataSnapshot snap) {
                    Integer limit = snap.getValue(Integer.class);
                    if (limit != null && limit > 0) {
                        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                            .edit().putInt("cached_daily_limit", limit).apply();
                        Log.d(TAG, "Daily limit updated from Firebase: " + limit);
                    }
                }
                @Override public void onCancelled(DatabaseError e) {
                    Log.w(TAG, "Could not fetch daily limit: " + e.getMessage());
                }
            });
    }

    /** يُستدعى من AdminDashboard لتغيير الحد لكل الأعضاء */
    public static void setGlobalDailyLimit(int newLimit) {
        FirebaseDatabase.getInstance().getReference("settings/daily_report_limit")
            .setValue(newLimit);
        Log.d(TAG, "Admin set global daily limit to: " + newLimit);
    }

    private static int getDailyLimit(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt("cached_daily_limit", DEFAULT_MAX_REPORTS);
    }

    // ════════════════════════════════════════════════════════════════════
    //  Public API
    // ════════════════════════════════════════════════════════════════════

    /** يتحقق ويزيد العداد — يرجع true إذا مسموح */
    public static boolean canSubmitReport(Context ctx) {
        if (isAdmin(ctx)) return true; // Admin: غير محدود

        // الحد الفردي المخصص يتجاوز الحد العام
        int limit = getPersonalLimit(ctx);
        if (limit == 0) return true; // صفر = غير محدود
        return checkAndIncrement(ctx, "report_count", "report_reset", limit, ONE_DAY_MS);
    }

    /** يقرأ الحد المخصص للمستخدم الحالي (مُخزَّن محلياً بعد الجلب من Firebase) */
    private static int getPersonalLimit(Context ctx) {
        int personal = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt("personal_daily_limit", -1);
        // -1 يعني لم يُضبط بعد → استخدم الحد العام
        return personal >= 0 ? personal : getDailyLimit(ctx);
    }

    /** يُستدعى من HomeActivity بعد تحميل بيانات المستخدم من Firebase */
    public static void setPersonalDailyLimit(Context ctx, int limit) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putInt("personal_daily_limit", limit).apply();
    }

    /** جلب الحد الفردي من Firebase وتخزينه محلياً */
    public static void fetchPersonalLimit(Context ctx, String uid) {
        FirebaseDatabase.getInstance().getReference("users").child(uid)
            .child("dailyReportLimit")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(DataSnapshot snap) {
                    Integer limit = snap.getValue(Integer.class);
                    if (limit != null) {
                        setPersonalDailyLimit(ctx, limit);
                        Log.d(TAG, "Personal daily limit for " + uid + ": " + limit);
                    }
                }
                @Override public void onCancelled(DatabaseError e) {
                    Log.w(TAG, "Could not fetch personal limit: " + e.getMessage());
                }
            });
    }

    public static boolean canSendMessage(Context ctx) {
        return checkAndIncrement(ctx, "msg_count", "msg_reset",
            MAX_MESSAGES_PER_HOUR, ONE_HOUR_MS);
    }

    public static boolean canSubmitAbuseReport(Context ctx) {
        return checkAndIncrement(ctx, "abuse_count", "abuse_reset",
            MAX_ABUSE_PER_DAY, ONE_DAY_MS);
    }

    /** عدد البلاغات المتبقية اليوم */
    public static int remainingReports(Context ctx) {
        if (isAdmin(ctx)) return Integer.MAX_VALUE;
        int limit = getDailyLimit(ctx);
        return remaining(ctx, "report_count", "report_reset", limit, ONE_DAY_MS);
    }

    public static int remainingMessages(Context ctx) {
        return remaining(ctx, "msg_count", "msg_reset", MAX_MESSAGES_PER_HOUR, ONE_HOUR_MS);
    }

    // ════════════════════════════════════════════════════════════════════
    //  Internal
    // ════════════════════════════════════════════════════════════════════

    private static boolean checkAndIncrement(Context ctx, String countKey,
                                              String resetKey, int max, long period) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long resetTime = prefs.getLong(resetKey, 0);
        int  count     = prefs.getInt(countKey, 0);

        if (System.currentTimeMillis() - resetTime > period) {
            prefs.edit()
                .putInt(countKey, 1)
                .putLong(resetKey, System.currentTimeMillis())
                .apply();
            return true;
        }
        if (count >= max) return false;
        prefs.edit().putInt(countKey, count + 1).apply();
        return true;
    }

    private static int remaining(Context ctx, String countKey,
                                  String resetKey, int max, long period) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long resetTime = prefs.getLong(resetKey, 0);
        if (System.currentTimeMillis() - resetTime > period) return max;
        return Math.max(0, max - prefs.getInt(countKey, 0));
    }
}
