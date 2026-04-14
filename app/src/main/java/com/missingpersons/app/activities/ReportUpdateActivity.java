package com.missingpersons.app.activities;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.missingpersons.app.R;
import com.missingpersons.app.utils.LanguageHelper;
import com.missingpersons.app.utils.StatsCache;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * ReportUpdateActivity — إضافة تحديث على بلاغ منشور
 *
 * يسمح لصاحب البلاغ بإضافة معلومات جديدة (رُئي في مكان آخر،
 * تغيّرت ملابسه، آخر موقع...) بدون إعادة موافقة كاملة.
 *
 * التحديثات تظهر كـ timeline في CaseDetailActivity.
 *
 * [محدّث] إضافة حقل "توقيت الحدث" منفصل عن وقت النشر
 *         لضمان دقة التسلسل الزمني للواقعة.
 */
public class ReportUpdateActivity extends AppCompatActivity {

    private TextInputEditText etUpdateText;
    private TextInputEditText etEventTime;
    private MaterialButton    btnPickEventTime;
    private Spinner           spUpdateType;
    private MaterialButton    btnSubmitUpdate;
    private ProgressBar       progressUpdate;
    private LinearLayout      layoutPrevUpdates;

    private String reportId;
    private String reportNode;

    // وقت الحدث الفعلي (يُختار من قِبَل المستخدم، اختياري)
    private Long   selectedEventTime = null;

    private static final String[] UPDATE_TYPES = {
        "📍 شُوهد في موقع جديد",
        "👕 تغيّرت ملابسه",
        "📞 تم التواصل معه",
        "ℹ️ معلومة جديدة",
        "✅ تم العثور عليه"
    };

    private final SimpleDateFormat sdfDisplay =
        new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("ar"));
    private final SimpleDateFormat sdfTimeline =
        new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("ar"));

    @Override
    protected void attachBaseContext(android.content.Context c) {
        super.attachBaseContext(LanguageHelper.applyLanguage(c));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_update);
        // [إصلاح 6 — Edge-to-Edge]
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bot = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), bot);
            return insets;
        });

        reportId   = getIntent().getStringExtra("reportId");
        reportNode = getIntent().getStringExtra("reportNode");
        if (reportNode == null) reportNode = "reports";

        if (reportId == null) { finish(); return; }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("📝 إضافة تحديث");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        etUpdateText     = findViewById(R.id.et_update_text);
        etEventTime      = findViewById(R.id.et_event_time);
        btnPickEventTime = findViewById(R.id.btn_pick_event_time);
        spUpdateType     = findViewById(R.id.sp_update_type);
        btnSubmitUpdate  = findViewById(R.id.btn_submit_update);
        progressUpdate   = findViewById(R.id.progress_update);
        layoutPrevUpdates = findViewById(R.id.layout_prev_updates);

        // تهيئة Spinner نوع التحديث
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, UPDATE_TYPES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (spUpdateType != null) spUpdateType.setAdapter(adapter);

        // اختيار توقيت الحدث
        if (btnPickEventTime != null)
            btnPickEventTime.setOnClickListener(v -> showDateTimePicker());
        if (etEventTime != null)
            etEventTime.setOnClickListener(v -> showDateTimePicker());

        if (btnSubmitUpdate != null)
            btnSubmitUpdate.setOnClickListener(v -> submitUpdate());

        loadPreviousUpdates();
    }

    // ─── اختيار التاريخ والوقت ─────────────────────────────────
    private void showDateTimePicker() {
        Calendar cal = Calendar.getInstance();
        // اختر التاريخ أولاً
        new DatePickerDialog(this, (dp, year, month, day) -> {
            // ثم اختر الوقت
            new TimePickerDialog(this, (tp, hour, minute) -> {
                cal.set(year, month, day, hour, minute, 0);
                selectedEventTime = cal.getTimeInMillis();
                if (etEventTime != null)
                    etEventTime.setText(sdfDisplay.format(cal.getTime()));
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    // ─── إرسال التحديث ────────────────────────────────────────
    private void submitUpdate() {
        String text = etUpdateText != null && etUpdateText.getText() != null
            ? etUpdateText.getText().toString().trim() : "";
        if (text.isEmpty()) {
            Toast.makeText(this, "أدخل نص التحديث", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
            ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anon";
        String updateType = spUpdateType != null
            ? UPDATE_TYPES[spUpdateType.getSelectedItemPosition()] : "ℹ️ معلومة جديدة";

        if (progressUpdate != null) progressUpdate.setVisibility(View.VISIBLE);
        if (btnSubmitUpdate != null) btnSubmitUpdate.setEnabled(false);

        long publishTime = System.currentTimeMillis();

        HashMap<String, Object> update = new HashMap<>();
        update.put("text",        text);
        update.put("type",        updateType);
        update.put("reporterId",  uid);
        update.put("timestamp",   publishTime);          // وقت النشر (تلقائي)
        update.put("status",      "published");

        // وقت الحدث الفعلي — اختياري، يُستخدم لترتيب التايم لاين
        if (selectedEventTime != null) {
            update.put("eventTime",      selectedEventTime);
            update.put("eventTimeLabel", sdfTimeline.format(new Date(selectedEventTime)));
        } else {
            // إذا لم يختر المستخدم وقتاً، نستخدم وقت النشر كقيمة افتراضية
            update.put("eventTime",      publishTime);
            update.put("eventTimeLabel", "");
        }

        // حفظ التحديث في report_updates/{reportId}
        FirebaseDatabase.getInstance()
            .getReference("report_updates").child(reportId)
            .push().setValue(update)
            .addOnSuccessListener(v -> {
                // تحديث lastUpdated على البلاغ الأصلي
                FirebaseDatabase.getInstance().getReference(reportNode)
                    .child(reportId).child("lastUpdated")
                    .setValue(publishTime);

                if (progressUpdate != null) progressUpdate.setVisibility(View.GONE);
                Toast.makeText(this, "✅ تم نشر التحديث", Toast.LENGTH_SHORT).show();
                if (etUpdateText  != null) etUpdateText.setText("");
                if (etEventTime   != null) etEventTime.setText("");
                selectedEventTime = null;
                loadPreviousUpdates();

                // لو التحديث "تم العثور عليه" → حدّث الحالة
                if (updateType.contains("تم العثور")) {
                    FirebaseDatabase.getInstance().getReference(reportNode)
                        .child(reportId).child("status").setValue("resolved");
                    // المرحلة 1: حفظ resolvedAt لـ pagination في SuccessStoriesActivity
                    FirebaseDatabase.getInstance().getReference(reportNode)
                        .child(reportId).child("resolvedAt")
                        .setValue(System.currentTimeMillis());
                    // المرحلة 3: تحديث stats cache
                    StatsCache.incrementResolved();
                    Toast.makeText(this,
                        "🎉 تم تحديث حالة البلاغ إلى: تم العثور عليه",
                        Toast.LENGTH_LONG).show();
                }

                if (btnSubmitUpdate != null) btnSubmitUpdate.setEnabled(true);
            })
            .addOnFailureListener(e -> {
                if (progressUpdate != null) progressUpdate.setVisibility(View.GONE);
                if (btnSubmitUpdate != null) btnSubmitUpdate.setEnabled(true);
                Toast.makeText(this, "❌ فشل النشر: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            });
    }

    // ─── تحميل التحديثات السابقة ─────────────────────────────
    private void loadPreviousUpdates() {
        if (layoutPrevUpdates == null) return;
        layoutPrevUpdates.removeAllViews();

        FirebaseDatabase.getInstance()
            .getReference("report_updates").child(reportId)
            .orderByChild("eventTime").limitToLast(10)  // ترتيب حسب eventTime
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    List<DataSnapshot> list = new ArrayList<>();
                    for (DataSnapshot c : snap.getChildren()) list.add(c);
                    // عكس الترتيب — الأحدث (حسب وقت الحدث) أولاً
                    Collections.reverse(list);

                    for (DataSnapshot c : list) {
                        String text      = c.child("text").getValue(String.class);
                        String type      = c.child("type").getValue(String.class);
                        Long   pubTs     = c.child("timestamp").getValue(Long.class);
                        Long   evtTs     = c.child("eventTime").getValue(Long.class);
                        String evtLabel  = c.child("eventTimeLabel").getValue(String.class);
                        if (text == null) continue;

                        LinearLayout row = new LinearLayout(ReportUpdateActivity.this);
                        row.setOrientation(LinearLayout.VERTICAL);
                        row.setPadding(0, 8, 0, 8);

                        // نوع التحديث
                        TextView tvType = new TextView(ReportUpdateActivity.this);
                        tvType.setText(type != null ? type : "ℹ️");
                        tvType.setTextSize(12f);
                        tvType.setTextColor(0xFF1565C0);

                        // نص التحديث
                        TextView tvText = new TextView(ReportUpdateActivity.this);
                        tvText.setText(text);
                        tvText.setTextSize(14f);

                        // وقت الحدث الفعلي (إن وُجد)
                        TextView tvEventTime = new TextView(ReportUpdateActivity.this);
                        if (evtLabel != null && !evtLabel.isEmpty()) {
                            tvEventTime.setText("🕐 وقت الحدث: " + evtLabel);
                            tvEventTime.setTextSize(11f);
                            tvEventTime.setTextColor(0xFF2E7D32);
                        }

                        // وقت النشر
                        TextView tvPubTime = new TextView(ReportUpdateActivity.this);
                        tvPubTime.setText(pubTs != null
                            ? "⬆️ نُشر: " + sdfDisplay.format(new Date(pubTs)) : "");
                        tvPubTime.setTextSize(11f);
                        tvPubTime.setTextColor(0xFF888888);

                        row.addView(tvType);
                        row.addView(tvText);
                        if (evtLabel != null && !evtLabel.isEmpty())
                            row.addView(tvEventTime);
                        row.addView(tvPubTime);

                        // فاصل
                        View sep = new View(ReportUpdateActivity.this);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1);
                        lp.setMargins(0, 8, 0, 0);
                        sep.setLayoutParams(lp);
                        sep.setBackgroundColor(0xFFE0E0E0);
                        row.addView(sep);

                        layoutPrevUpdates.addView(row);
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
    }

    @Override public boolean onSupportNavigateUp() { onBackPressed(); return true; }
}
