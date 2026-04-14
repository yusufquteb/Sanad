package com.missingpersons.app.workers;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * ChatCleanupWorker — يحذف رسائل الشات الأقدم من عدد الأيام المحدد.
 *
 * مدة الاحتفاظ قابلة للتحكم من لوحة الإدارة عبر Firebase:
 *   settings/chat_ttl_days  → عدد صحيح (افتراضي: 30 يوماً)
 *
 * كيفية التغيير من لوحة التحكم:
 *   AdminDashboardActivity يكتب في: settings/chat_ttl_days = <عدد الأيام>
 *
 * يُشغَّل يومياً من MyApplication
 */
public class ChatCleanupWorker extends Worker {

    private static final String TAG              = "ChatCleanup";
    private static final long   DEFAULT_TTL_DAYS = 30L; // افتراضي: 30 يوماً
    private static final String SETTINGS_PATH    = "settings/chat_ttl_days";

    public ChatCleanupWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "ChatCleanupWorker started — قراءة مدة الاحتفاظ من Firebase...");

        // اقرأ عدد الأيام من Firebase ثم نفّذ الحذف
        FirebaseDatabase.getInstance().getReference(SETTINGS_PATH)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snap) {
                    Long ttlDays = snap.getValue(Long.class);
                    // إذا لم تُضبط القيمة بعد → استخدم الافتراضي
                    long days = (ttlDays != null && ttlDays > 0) ? ttlDays : DEFAULT_TTL_DAYS;
                    long cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L;
                    Log.d(TAG, "مدة الاحتفاظ = " + days + " يوم — حذف ما قبل: " + cutoff);
                    performCleanup(cutoff);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError e) {
                    // فشل القراءة → استخدم الافتراضي
                    Log.w(TAG, "فشل قراءة الإعدادات — استخدام الافتراضي " + DEFAULT_TTL_DAYS + " يوم");
                    long cutoff = System.currentTimeMillis() - DEFAULT_TTL_DAYS * 24L * 60L * 60L * 1000L;
                    performCleanup(cutoff);
                }
            });

        return Result.success();
    }

    private void performCleanup(long cutoff) {
        FirebaseDatabase.getInstance().getReference("chats")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot chatsSnap) {
                    int deleted = 0;
                    for (DataSnapshot chatRoom : chatsSnap.getChildren()) {
                        for (DataSnapshot msg : chatRoom.getChildren()) {
                            Long ts = msg.child("timestamp").getValue(Long.class);
                            if (ts != null && ts < cutoff) {
                                msg.getRef().removeValue();
                                deleted++;
                            }
                        }
                    }
                    Log.d(TAG, "Chat cleanup done — " + deleted + " رسالة محذوفة");
                }

                @Override
                public void onCancelled(@NonNull DatabaseError e) {
                    Log.e(TAG, "Chat cleanup cancelled: " + e.getMessage());
                }
            });
    }
}
