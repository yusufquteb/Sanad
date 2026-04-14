package com.missingpersons.app.workers;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.*;
import com.google.firebase.database.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * DailyReportWorker — تقرير يومي تلقائي للأدمن
 *
 * يُشغَّل كل 24 ساعة، يجمع الإحصائيات ويكتبها في:
 *   - admin_notifications (يظهر في لوحة التحكم)
 *   - daily_reports/{date} (أرشيف قابل للمراجعة)
 */
public class DailyReportWorker extends Worker {

    private static final String TAG = "DailyReport";

    public DailyReportWorker(@NonNull Context ctx, @NonNull WorkerParameters p) {
        super(ctx, p);
    }

    public static void scheduleDailyReport(Context ctx) {
        PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(
            DailyReportWorker.class, 24, TimeUnit.HOURS)
            .setConstraints(new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build())
            .setInitialDelay(calcDelayToMidnight(), TimeUnit.MILLISECONDS)
            .build();
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "daily_admin_report",
            ExistingPeriodicWorkPolicy.KEEP,
            work);
        Log.d(TAG, "Daily report scheduled");
    }

    /** يحسب التأخير لمنتصف الليل */
    private static long calcDelayToMidnight() {
        Calendar midnight = Calendar.getInstance();
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.add(Calendar.DAY_OF_MONTH, 1);
        return midnight.getTimeInMillis() - System.currentTimeMillis();
    }

    @NonNull
    @Override
    public Result doWork() {
        collectAndSendReport();
        return Result.success();
    }

    private void collectAndSendReport() {
        DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        long dayStart = System.currentTimeMillis() - 24 * 60 * 60 * 1000L;
        String today  = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .format(new Date());

        // عدّادات
        final long[] stats = new long[6];
        // 0=total, 1=newToday, 2=pending, 3=approved, 4=matches, 5=foundPersons

        final int[] tasks = {3}; // عدد الـ async tasks

        // ── إحصائيات reports ─────────────────────────────────────
        db.child("reports").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                for (DataSnapshot c : snap.getChildren()) {
                    stats[0]++;
                    Long ts = c.child("timestamp").getValue(Long.class);
                    if (ts != null && ts >= dayStart) stats[1]++;
                    String status = c.child("status").getValue(String.class);
                    if ("pending".equals(status))  stats[2]++;
                    if ("approved".equals(status)) stats[3]++;
                }
                if (--tasks[0] == 0) saveReport(stats, today);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                if (--tasks[0] == 0) saveReport(stats, today);
            }
        });

        // ── إحصائيات found_persons ───────────────────────────────
        db.child("found_persons").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                stats[5] = snap.getChildrenCount();
                if (--tasks[0] == 0) saveReport(stats, today);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                if (--tasks[0] == 0) saveReport(stats, today);
            }
        });

        // ── إحصائيات matches اليوم ───────────────────────────────
        db.child("matches").orderByChild("timestamp").startAt(dayStart)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    stats[4] = snap.getChildrenCount();
                    if (--tasks[0] == 0) saveReport(stats, today);
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {
                    if (--tasks[0] == 0) saveReport(stats, today);
                }
            });
    }

    private void saveReport(long[] stats, String date) {
        String msg = "📊 التقرير اليومي — " + date + "\n"
            + "═══════════════════\n"
            + "📋 إجمالي البلاغات:    " + stats[0] + "\n"
            + "🆕 جديدة اليوم:        " + stats[1] + "\n"
            + "⏳ قيد المراجعة:       " + stats[2] + "\n"
            + "✅ موافق عليها:        " + stats[3] + "\n"
            + "✋ معثور عليهم:        " + stats[5] + "\n"
            + "🔍 تطابقات اليوم:      " + stats[4];

        HashMap<String, Object> notif = new HashMap<>();
        notif.put("type",       "daily_report");
        notif.put("message",    msg);
        notif.put("date",       date);
        notif.put("stats",      Arrays.asList(stats[0], stats[1], stats[2],
                                              stats[3], stats[4], stats[5]));
        notif.put("timestamp",  System.currentTimeMillis());
        notif.put("read",       false);

        DatabaseReference db = FirebaseDatabase.getInstance().getReference();

        // إشعار في admin_notifications
        db.child("admin_notifications").push().setValue(notif);

        // أرشيف في daily_reports
        db.child("daily_reports").child(date).setValue(notif);

        Log.d(TAG, "Daily report saved: " + date);
    }
}
