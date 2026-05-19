package com.missingpersons.app.activities;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.missingpersons.app.R;
import com.missingpersons.app.utils.AiError;
import com.missingpersons.app.utils.CrossMatchManager;
import com.missingpersons.app.utils.EmbeddingCleanupUtil;
import com.missingpersons.app.utils.FaceEmbeddingManager;
import com.missingpersons.app.utils.ImagePreprocessor;
import com.missingpersons.app.utils.LanguageHelper;
import com.missingpersons.app.utils.StatsCache;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * ReportUpdateActivity — تحديث البلاغ + تحديث الصورة
 *
 * [المرحلة 1 — إصلاح مشاكل الـ Build]
 *
 * ════════════════════════════════════════════════════════
 * الإصلاحات في هذه النسخة:
 *
 * ✅ 1. استخدام ImagePreprocessor.preprocessBitmap() بدلاً من
 *       processForFaceDetection() التي لا توجد
 *
 * ✅ 2. تحويل float[] embedding إلى String قبل إرساله لـ CrossMatchManager
 *       لأن CrossMatchManager.matchReportWithFoundPersons يأخذ:
 *       (String reportId, String reporterUid, String personName, String embedding)
 *
 * ✅ 3. جلب reporterUid و personName من Firebase قبل استدعاء CrossMatch
 *
 * ✅ 4. Views الجديدة تُعالَج بأمان بـ null checks
 *       (إذا لم تُضَف في XML بعد — لن يحدث crash)
 * ════════════════════════════════════════════════════════
 */
public class ReportUpdateActivity extends AppCompatActivity {

    // ── Views الأصلية ──────────────────────────────────────────
    private TextInputEditText etUpdateText;
    private TextInputEditText etEventTime;
    private MaterialButton    btnPickEventTime;
    private Spinner           spUpdateType;
    private MaterialButton    btnSubmitUpdate;
    private ProgressBar       progressUpdate;
    private LinearLayout      layoutPrevUpdates;

    // ── Views الجديدة لتغيير الصورة ───────────────────────────
    private ImageView      ivCurrentPhoto;
    private MaterialButton btnChangePhoto;
    private MaterialButton btnUpdatePhoto;
    private TextView       tvPhotoStatus;

    // ── State ──────────────────────────────────────────────────
    private String  reportId;
    private String  reportNode;
    private Long    selectedEventTime = null;
    private Bitmap  pendingNewPhoto   = null;
    private float[] pendingEmbedding  = null;

    private static final String TAG = "ReportUpdateActivity";

    private static final String[] UPDATE_TYPES = {
        "📍 شُوهد في موقع جديد",
        "👕 تغيّرت ملابسه",
        "📞 تم التواصل معه",
        "ℹ️ معلومة جديدة",
        "✅ تم العثور عليه"
    };

    private final SimpleDateFormat sdfDisplay  =
        new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("ar"));
    private final SimpleDateFormat sdfTimeline =
        new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("ar"));

    // ── Gallery Launcher ───────────────────────────────────────
    private final ActivityResultLauncher<Intent> galleryLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) processNewPhoto(uri);
                }
            });

    // ════════════════════════════════════════════════════════════
    @Override
    protected void attachBaseContext(android.content.Context c) {
        super.attachBaseContext(LanguageHelper.applyLanguage(c));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_update);

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
        if (reportId   == null) { finish(); return; }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("📝 تحديث البلاغ");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        bindViews();
        setupSpinner();
        setupListeners();
        loadPreviousUpdates();
    }

    // ════════════════════════════════════════════════════════════
    //  Setup
    // ════════════════════════════════════════════════════════════

    private void bindViews() {
        etUpdateText      = findViewById(R.id.et_update_text);
        etEventTime       = findViewById(R.id.et_event_time);
        btnPickEventTime  = findViewById(R.id.btn_pick_event_time);
        spUpdateType      = findViewById(R.id.sp_update_type);
        btnSubmitUpdate   = findViewById(R.id.btn_submit_update);
        progressUpdate    = findViewById(R.id.progress_update);
        layoutPrevUpdates = findViewById(R.id.layout_prev_updates);

        // [جديد] Views تغيير الصورة — nullable (اختيارية في XML)
        ivCurrentPhoto = findViewById(R.id.iv_current_photo);
        btnChangePhoto = findViewById(R.id.btn_change_photo);
        btnUpdatePhoto = findViewById(R.id.btn_update_photo);
        tvPhotoStatus  = findViewById(R.id.tv_photo_status);
    }

    private void setupSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, UPDATE_TYPES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (spUpdateType != null) spUpdateType.setAdapter(adapter);
    }

    private void setupListeners() {
        if (btnPickEventTime != null)
            btnPickEventTime.setOnClickListener(v -> showDateTimePicker());
        if (etEventTime != null)
            etEventTime.setOnClickListener(v -> showDateTimePicker());
        if (btnSubmitUpdate != null)
            btnSubmitUpdate.setOnClickListener(v -> submitUpdate());

        // [جديد] زر اختيار صورة — آمن حتى لو غير موجود في XML
        if (btnChangePhoto != null)
            btnChangePhoto.setOnClickListener(v -> openGallery());

        if (btnUpdatePhoto != null) {
            btnUpdatePhoto.setEnabled(false);
            btnUpdatePhoto.setOnClickListener(v -> fetchReportDataThenRematch());
        }
    }

    // ════════════════════════════════════════════════════════════
    //  [جديد] تغيير الصورة
    // ════════════════════════════════════════════════════════════

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    /**
     * معالجة الصورة الجديدة:
     * preprocessBitmap → extractEmbeddingSync (Alignment + QualityGate + TFLite)
     */
    private void processNewPhoto(Uri uri) {
        setPhotoStatus("⏳ جاري تحليل الصورة...", 0xFF1565C0);
        if (btnUpdatePhoto != null) btnUpdatePhoto.setEnabled(false);
        pendingNewPhoto  = null;
        pendingEmbedding = null;

        new Thread(() -> {
            try {
                // 1. تحميل الصورة
                Bitmap raw;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    raw = android.graphics.ImageDecoder.decodeBitmap(
                        android.graphics.ImageDecoder.createSource(getContentResolver(), uri));
                } else {
                    //noinspection deprecation
                    raw = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                }
                if (raw == null) { postPhotoError(AiError.NULL_BITMAP); return; }

                // 2. Preprocess — الدالة الصحيحة الموجودة في ImagePreprocessor
                Bitmap processed = ImagePreprocessor.preprocessBitmap(raw);
                if (processed == null) processed = raw;

                // 3. استخراج embedding (Alignment + QualityGate + TFLite داخل الدالة)
                float[] emb = FaceEmbeddingManager.extractEmbeddingSync(this, processed);
                if (emb == null) { postPhotoError(AiError.FACE_NOT_FOUND); return; }

                pendingNewPhoto  = processed;
                pendingEmbedding = emb;

                final Bitmap finalBitmap = processed;
                runOnUiThread(() -> {
                    if (ivCurrentPhoto != null) ivCurrentPhoto.setImageBitmap(finalBitmap);
                    setPhotoStatus("✅ وجه اكتُشف — اضغط 'تأكيد' للحفظ", 0xFF2E7D32);
                    if (btnUpdatePhoto != null) btnUpdatePhoto.setEnabled(true);
                });

            } catch (Exception e) {
                android.util.Log.e(TAG, "processNewPhoto: " + e.getMessage());
                postPhotoError(AiError.EMBEDDING_EXTRACTION_FAILED);
            }
        }).start();
    }

    private void postPhotoError(String code) {
        runOnUiThread(() -> {
            setPhotoStatus("❌ " + AiError.toUserMessage(code), 0xFFB71C1C);
            if (btnUpdatePhoto != null) btnUpdatePhoto.setEnabled(false);
        });
    }

    // ════════════════════════════════════════════════════════════
    //  [جديد] رفع الصورة وإعادة المطابقة
    // ════════════════════════════════════════════════════════════

    /**
     * يجلب reporterUid و personName من Firebase أولاً
     * ثم يُنفّذ رفع الصورة + CrossMatch بالتوقيعات الصحيحة
     */
    private void fetchReportDataThenRematch() {
        if (pendingNewPhoto == null || pendingEmbedding == null) {
            Toast.makeText(this, "اختر صورة أولاً", Toast.LENGTH_SHORT).show();
            return;
        }

        if (progressUpdate != null) progressUpdate.setVisibility(View.VISIBLE);
        if (btnUpdatePhoto != null) btnUpdatePhoto.setEnabled(false);
        if (btnChangePhoto != null) btnChangePhoto.setEnabled(false);
        setPhotoStatus("⏳ جاري رفع الصورة...", 0xFF1565C0);

        // جلب بيانات البلاغ الأصلي (reporterUid + personName)
        FirebaseDatabase.getInstance()
            .getReference(reportNode).child(reportId)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String reporterUid = snapshot.child("reporterId").getValue(String.class);
                    String personName  = snapshot.child("personName").getValue(String.class);
                    if (reporterUid == null) reporterUid = "";
                    if (personName  == null) personName  = "مجهول";
                    uploadPhoto(reporterUid, personName);
                }
                @Override
                public void onCancelled(@NonNull DatabaseError e) {
                    onUploadError("فشل قراءة بيانات البلاغ: " + e.getMessage());
                }
            });
    }

    private void uploadPhoto(String reporterUid, String personName) {
        String storagePath = "report_images/" + reportId + "_"
            + System.currentTimeMillis() + ".jpg";
        StorageReference storageRef =
            FirebaseStorage.getInstance().getReference(storagePath);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        pendingNewPhoto.compress(Bitmap.CompressFormat.JPEG, 85, baos);

        storageRef.putBytes(baos.toByteArray())
            .continueWithTask(task -> {
                if (!task.isSuccessful() && task.getException() != null)
                    throw task.getException();
                return storageRef.getDownloadUrl();
            })
            .addOnSuccessListener(uri -> {
                String newUrl = uri.toString();

                // تحويل embedding إلى String للـ CrossMatchManager
                String embString = FaceEmbeddingManager.embeddingToString(pendingEmbedding);

                // تحديث Firebase DB
                Map<String, Object> updates = new HashMap<>();
                updates.put("imageUrl",    newUrl);
                updates.put("faceEmbedding", embString);
                updates.put(EmbeddingCleanupUtil.FIELD_EMBEDDING_VERSION,
                    FaceEmbeddingManager.EMBEDDING_VERSION);
                updates.put(EmbeddingCleanupUtil.FIELD_MODEL_VERSION,
                    FaceEmbeddingManager.MODEL_VERSION);
                updates.put(EmbeddingCleanupUtil.FIELD_PREPROCESSING_VER, 1);
                updates.put("photoUpdatedAt", System.currentTimeMillis());

                FirebaseDatabase.getInstance()
                    .getReference(reportNode).child(reportId)
                    .updateChildren(updates)
                    .addOnSuccessListener(v -> {
                        android.util.Log.i(TAG, "✅ صورة البلاغ محدّثة");
                        setPhotoStatus("⏳ جاري البحث عن تطابقات...", 0xFF1565C0);

                        // إعادة CrossMatch بالتوقيعات الصحيحة
                        runRematch(reporterUid, personName, embString);
                    })
                    .addOnFailureListener(e ->
                        onUploadError("فشل تحديث قاعدة البيانات: " + e.getMessage()));
            })
            .addOnFailureListener(e ->
                onUploadError("فشل رفع الصورة: " + e.getMessage()));
    }

    /**
     * إعادة CrossMatch باستخدام التوقيعات الحقيقية الموجودة في CrossMatchManager:
     *   matchReportWithFoundPersons(reportId, reporterUid, personName, embeddingString)
     *   matchFoundPersonWithReports(foundId, finderUid, embeddingString)
     */
    private void runRematch(String reporterUid, String personName, String embString) {
        boolean isFoundPerson = "found_persons".equals(reportNode);

        if (isFoundPerson) {
            // found_persons: finderUid = reporterUid
            CrossMatchManager.matchFoundPersonWithReports(reportId, reporterUid, embString);
        } else {
            // reports
            CrossMatchManager.matchReportWithFoundPersons(
                reportId, reporterUid, personName, embString);
        }

        // CrossMatch يعمل async — نُخبر المستخدم فوراً
        runOnUiThread(() -> {
            if (progressUpdate != null) progressUpdate.setVisibility(View.GONE);
            if (btnChangePhoto != null) btnChangePhoto.setEnabled(true);

            String msg = "✅ تم تحديث الصورة وبدء البحث عن تطابقات";
            setPhotoStatus(msg, 0xFF2E7D32);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();

            pendingNewPhoto  = null;
            pendingEmbedding = null;
        });
    }

    private void onUploadError(String message) {
        runOnUiThread(() -> {
            if (progressUpdate != null) progressUpdate.setVisibility(View.GONE);
            if (btnUpdatePhoto != null) btnUpdatePhoto.setEnabled(true);
            if (btnChangePhoto != null) btnChangePhoto.setEnabled(true);
            setPhotoStatus("❌ " + message, 0xFFB71C1C);
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }

    private void setPhotoStatus(String text, int color) {
        runOnUiThread(() -> {
            if (tvPhotoStatus != null) {
                tvPhotoStatus.setText(text);
                tvPhotoStatus.setTextColor(color);
                tvPhotoStatus.setVisibility(View.VISIBLE);
            }
        });
    }

    // ════════════════════════════════════════════════════════════
    //  الكود الأصلي (بدون تغيير)
    // ════════════════════════════════════════════════════════════

    private void showDateTimePicker() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (dp, year, month, day) ->
            new TimePickerDialog(this, (tp, hour, minute) -> {
                cal.set(year, month, day, hour, minute, 0);
                selectedEventTime = cal.getTimeInMillis();
                if (etEventTime != null)
                    etEventTime.setText(sdfDisplay.format(cal.getTime()));
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show(),
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)).show();
    }

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
        update.put("text",       text);
        update.put("type",       updateType);
        update.put("reporterId", uid);
        update.put("timestamp",  publishTime);
        update.put("status",     "published");

        if (selectedEventTime != null) {
            update.put("eventTime",      selectedEventTime);
            update.put("eventTimeLabel", sdfTimeline.format(new Date(selectedEventTime)));
        } else {
            update.put("eventTime",      publishTime);
            update.put("eventTimeLabel", "");
        }

        FirebaseDatabase.getInstance()
            .getReference("report_updates").child(reportId)
            .push().setValue(update)
            .addOnSuccessListener(v -> {
                FirebaseDatabase.getInstance().getReference(reportNode)
                    .child(reportId).child("lastUpdated").setValue(publishTime);
                if (progressUpdate != null) progressUpdate.setVisibility(View.GONE);
                Toast.makeText(this, "✅ تم نشر التحديث", Toast.LENGTH_SHORT).show();
                if (etUpdateText != null) etUpdateText.setText("");
                if (etEventTime  != null) etEventTime.setText("");
                selectedEventTime = null;
                loadPreviousUpdates();
                if (updateType.contains("تم العثور")) {
                    FirebaseDatabase.getInstance().getReference(reportNode)
                        .child(reportId).child("status").setValue("resolved");
                    FirebaseDatabase.getInstance().getReference(reportNode)
                        .child(reportId).child("resolvedAt")
                        .setValue(System.currentTimeMillis());
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

    private void loadPreviousUpdates() {
        if (layoutPrevUpdates == null) return;
        layoutPrevUpdates.removeAllViews();

        FirebaseDatabase.getInstance()
            .getReference("report_updates").child(reportId)
            .orderByChild("eventTime").limitToLast(10)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    List<DataSnapshot> list = new ArrayList<>();
                    for (DataSnapshot c : snap.getChildren()) list.add(c);
                    Collections.reverse(list);

                    for (DataSnapshot c : list) {
                        String text     = c.child("text").getValue(String.class);
                        String type     = c.child("type").getValue(String.class);
                        Long   pubTs    = c.child("timestamp").getValue(Long.class);
                        String evtLabel = c.child("eventTimeLabel").getValue(String.class);
                        if (text == null) continue;

                        LinearLayout row = new LinearLayout(ReportUpdateActivity.this);
                        row.setOrientation(LinearLayout.VERTICAL);
                        row.setPadding(0, 8, 0, 8);

                        TextView tvType = new TextView(ReportUpdateActivity.this);
                        tvType.setText(type != null ? type : "ℹ️");
                        tvType.setTextSize(12f);
                        tvType.setTextColor(0xFF1565C0);

                        TextView tvText = new TextView(ReportUpdateActivity.this);
                        tvText.setText(text);
                        tvText.setTextSize(14f);

                        TextView tvPubTime = new TextView(ReportUpdateActivity.this);
                        tvPubTime.setText(pubTs != null
                            ? "⬆️ " + sdfDisplay.format(new Date(pubTs)) : "");
                        tvPubTime.setTextSize(11f);
                        tvPubTime.setTextColor(0xFF888888);

                        row.addView(tvType);
                        row.addView(tvText);
                        if (evtLabel != null && !evtLabel.isEmpty()) {
                            TextView tvEvt = new TextView(ReportUpdateActivity.this);
                            tvEvt.setText("🕐 " + evtLabel);
                            tvEvt.setTextSize(11f);
                            tvEvt.setTextColor(0xFF2E7D32);
                            row.addView(tvEvt);
                        }
                        row.addView(tvPubTime);

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
