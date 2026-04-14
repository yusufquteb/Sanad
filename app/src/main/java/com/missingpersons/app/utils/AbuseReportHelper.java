package com.missingpersons.app.utils;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import com.missingpersons.app.R;
import com.missingpersons.app.activities.AdminActivity;
import java.util.HashMap;

/**
 * AbuseReportHelper — مساعد الإبلاغ عن سوء الاستخدام
 *
 * يُرسل البلاغ إلى:
 * ✅ قاعدة بيانات Firebase (abuse_reports)
 * ✅ إشعار داخلي للأدمن
 * ✅ إشعار داخلي لجميع المديرين (الذين لديهم canSendNotifications أو أي صلاحية)
 */
public class AbuseReportHelper {

    public enum ReportTarget {
        REPORT,   // بلاغ اختفاء (منشور)
        MEMBER,   // عضو في التطبيق
        APP       // سلوك عام في التطبيق
    }

    public interface ReportCallback {
        void onResult(boolean success);
    }

    /**
     * يُظهر Dialog لإدخال سبب الإبلاغ ثم يرسله
     *
     * @param activity     النشاط الحالي
     * @param targetType   نوع الإبلاغ
     * @param targetId     ID المنشور أو العضو (null لو APP)
     * @param targetName   اسم المنشور / العضو للعرض
     */
    public static void showReportDialog(Activity activity, ReportTarget targetType,
                                        String targetId, String targetName) {
        View dialogView = LayoutInflater.from(activity)
            .inflate(R.layout.dialog_abuse_report, null);

        RadioGroup rgReasons = dialogView.findViewById(R.id.rg_abuse_reasons);
        EditText etDetails   = dialogView.findViewById(R.id.et_abuse_details);

        new AlertDialog.Builder(activity)
            .setTitle("🚨 إبلاغ عن سوء استخدام")
            .setMessage("سيصل تقريرك مباشرةً للإدارة للمراجعة والاتخاذ اللازم.")
            .setView(dialogView)
            .setPositiveButton("إرسال البلاغ", (d, w) -> {
                // تحديد السبب المختار
                int selectedId = rgReasons.getCheckedRadioButtonId();
                String reason = "سوء استخدام";
                if (selectedId != -1) {
                    RadioButton rb = dialogView.findViewById(selectedId);
                    if (rb != null) reason = rb.getText().toString();
                }
                String details = etDetails.getText().toString().trim();
                if (details.isEmpty()) details = "لم يُذكر تفاصيل";

                submitReport(activity, targetType, targetId, targetName, reason, details, null);
            })
            .setNegativeButton("إلغاء", null)
            .show();
    }

    public static void submitReport(Context context, ReportTarget targetType,
                                     String targetId, String targetName,
                                     String reason, String details,
                                     ReportCallback callback) {

        FirebaseUser reporter = FirebaseAuth.getInstance().getCurrentUser();
        String reporterUid   = reporter != null ? reporter.getUid()   : "guest";
        String reporterEmail = reporter != null ? reporter.getEmail()  : "زائر";
        if (reporterEmail == null) reporterEmail = "مجهول";

        DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        String reportKey = db.child("abuse_reports").push().getKey();
        if (reportKey == null) {
            if (callback != null) callback.onResult(false);
            return;
        }

        // بناء كائن البلاغ
        HashMap<String, Object> report = new HashMap<>();
        report.put("reportKey",    reportKey);
        report.put("type",         targetType.name());
        report.put("targetId",     targetId   != null ? targetId   : "N/A");
        report.put("targetName",   targetName != null ? targetName : "غير محدد");
        report.put("reason",       reason);
        report.put("details",      details);
        report.put("reporterUid",  reporterUid);
        report.put("reporterEmail", reporterEmail);
        report.put("timestamp",    System.currentTimeMillis());
        report.put("status",       "pending");

        // حفظ البلاغ
        db.child("abuse_reports").child(reportKey).setValue(report)
            .addOnSuccessListener(aVoid -> {
                Toast.makeText(context, "✅ تم إرسال البلاغ للإدارة. شكراً لمساعدتك!", Toast.LENGTH_LONG).show();
                // إرسال إشعار داخلي للإدارة
                notifyAdminsAndManagers(db, reportKey, targetName, reason);
                if (callback != null) callback.onResult(true);
            })
            .addOnFailureListener(e -> {
                Toast.makeText(context, "❌ فشل إرسال البلاغ، تحقق من الإنترنت", Toast.LENGTH_SHORT).show();
                if (callback != null) callback.onResult(false);
            });
    }

    /**
     * يكتب إشعار في نود notifications لكل أدمن ومدير
     * التطبيق يقرأ الإشعارات عند الفتح ويُظهرها
     */
    private static void notifyAdminsAndManagers(DatabaseReference db, String reportKey,
                                                 String targetName, String reason) {
        String notifTitle = "🚨 بلاغ سوء استخدام جديد";
        String notifBody  = "الهدف: " + targetName + "\nالسبب: " + reason;

        // إشعار للأدمن — نبحث عنه بالإيميل
        db.child("users").orderByChild("role")
            .equalTo("admin") // role-based
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(DataSnapshot snapshot) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        writeNotification(db, child.getKey(), notifTitle, notifBody, reportKey);
                    }
                }
                @Override public void onCancelled(DatabaseError error) {}
            });

        // إشعار لجميع المديرين
        db.child("users").orderByChild("role").equalTo("manager")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(DataSnapshot snapshot) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        Boolean banned = child.child("banned").getValue(Boolean.class);
                        if (Boolean.TRUE.equals(banned)) continue;
                        writeNotification(db, child.getKey(), notifTitle, notifBody, reportKey);
                    }
                }
                @Override public void onCancelled(DatabaseError error) {}
            });
    }

    private static void writeNotification(DatabaseReference db, String uid,
                                          String title, String body, String reportKey) {
        if (uid == null) return;
        String key = db.child("notifications").child(uid).push().getKey();
        if (key == null) return;

        HashMap<String, Object> notif = new HashMap<>();
        notif.put("title",     title);
        notif.put("body",      body);
        notif.put("type",      "abuse_report");
        notif.put("reportKey", reportKey);
        notif.put("timestamp", System.currentTimeMillis());
        notif.put("read",      false);

        db.child("notifications").child(uid).child(key).setValue(notif);
    }
}
