package com.missingpersons.app.utils;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

/**
 * NotificationListenerHelper — يستمع لإشعارات Firebase في الوقت الحقيقي
 * ويعرض إشعارات محلية عند وصول تطابق أو رسالة
 *
 * يُفعَّل في HomeActivity.onCreate()
 */
public class NotificationListenerHelper {

    private static final String TAG = "NotifListener";
    private static boolean isListening = false;
    private static ChildEventListener listener;

    public static void startListening(Context context) {
        if (isListening) return;

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
            ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;

        DatabaseReference ref = FirebaseDatabase.getInstance()
            .getReference("notifications").child(uid);

        listener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snap, String prev) {
                Boolean read = snap.child("read").getValue(Boolean.class);
                if (Boolean.TRUE.equals(read)) return;

                String type = snap.child("type").getValue(String.class);
                String message = snap.child("message").getValue(String.class);
                String personName = snap.child("personName").getValue(String.class);
                Long similarity = snap.child("similarity").getValue(Long.class);
                String reportId = snap.child("reportId").getValue(String.class);

                if (type == null || message == null) return;

                switch (type) {
                    case "face_match":
                    case "found_matches_report":
                    case "report_matches_found":
                        int percent = similarity != null ? similarity.intValue() : 0;
                        NotificationHelper.showMatchNotification(context,
                            personName != null ? personName : "شخص",
                            percent, reportId);
                        break;

                    case "chat_message":
                        String fromName = snap.child("fromName").getValue(String.class);
                        NotificationHelper.showChatNotification(context,
                            fromName != null ? fromName : "مستخدم", message);
                        break;

                    case "abuse_report":
                        NotificationHelper.showAdminNotification(context,
                            "🚨 بلاغ سوء استخدام جديد", message);
                        break;
                }
            }

            @Override public void onChildChanged(@NonNull DataSnapshot s, String p) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot s) {}
            @Override public void onChildMoved(@NonNull DataSnapshot s, String p) {}
            @Override public void onCancelled(@NonNull DatabaseError e) {
                Log.e(TAG, "Listener cancelled: " + e.getMessage());
            }
        };

        ref.orderByChild("read").equalTo(false)
            .addChildEventListener(listener);
        isListening = true;
        Log.d(TAG, "Started listening for notifications");
    }

    public static void stopListening() {
        if (!isListening || listener == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
            ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid != null) {
            FirebaseDatabase.getInstance()
                .getReference("notifications").child(uid)
                .removeEventListener(listener);
        }
        isListening = false;
        listener = null;
    }
}
