package com.missingpersons.app.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import android.provider.MediaStore;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.google.firebase.storage.*;

import com.missingpersons.app.R;
import com.google.android.material.card.MaterialCardView;
import com.missingpersons.app.utils.*;

import java.io.*;
import java.util.*;

/**
 * FoundSightingActivity — "شُفت شخصاً مجهولاً"
 *
 * المستخدم يُبلّغ عن شخص رآه ويعتقد أنه مفقود.
 * يُقارن الوجه فوراً مع قاعدة البيانات ويرسل إشعاراً
 * لأصحاب البلاغات المطابقة.
 *
 * Firebase node: "sightings/{sightingId}"
 */
public class FoundSightingActivity extends AppCompatActivity {

    private static final int REQ_CAMERA  = 101;
    private static final int REQ_GALLERY = 102;
    private static final int REQ_PERM    = 103;

    // ─── Views ───────────────────────────────────────────────────
    private ImageView        ivPhoto;
    private MaterialCardView cardPhoto;
    private TextInputEditText etDescription, etLocation;
    private Spinner          spGovernorates;
    private MaterialButton   btnCamera, btnGallery, btnSubmit;
    private ProgressBar      progressBar;
    private TextView         tvStatus, tvMatchResult;
    private TextInputEditText etLinkedReportCode;   // كود البلاغ المرتبط
    private MaterialButton    btnLinkReport;
    private TextView          tvLinkedReportInfo;
    private String            linkedReportId = null; // يُملأ بعد التحقق

    // ─── State ───────────────────────────────────────────────────
    private Uri     photoUri;
    private Bitmap  photoBitmap;
    private double  currentLat = 0, currentLng = 0;
    private boolean isUploading = false;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LanguageHelper.applyLanguage(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_found_sighting);
        // [إصلاح 6 — Edge-to-Edge]
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bot = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), bot);
            return insets;
        });

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("👁️ شُفت شخصاً مجهولاً");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        bindViews();
        setupGovernorates();
        requestLocation();
    }

    // ─── Views Setup ──────────────────────────────────────────────

    private void bindViews() {
        ivPhoto        = findViewById(R.id.iv_sighting_photo);
        cardPhoto      = findViewById(R.id.card_sighting_photo);
        etDescription  = findViewById(R.id.et_sighting_description);
        etLocation     = findViewById(R.id.et_sighting_location);
        spGovernorates = findViewById(R.id.sp_sighting_governorate);
        btnCamera      = findViewById(R.id.btn_sighting_camera);
        btnGallery     = findViewById(R.id.btn_sighting_gallery);
        btnSubmit      = findViewById(R.id.btn_sighting_submit);
        progressBar    = findViewById(R.id.progress_sighting);
        tvStatus       = findViewById(R.id.tv_sighting_status);
        tvMatchResult  = findViewById(R.id.tv_sighting_match_result);

        etLinkedReportCode = findViewById(R.id.et_linked_report_code);
        btnLinkReport      = findViewById(R.id.btn_link_report);
        tvLinkedReportInfo = findViewById(R.id.tv_linked_report_info);

        if (btnLinkReport != null) btnLinkReport.setOnClickListener(v -> lookupLinkedReport());
        if (btnCamera != null) btnCamera.setOnClickListener(v -> openCamera());
        if (btnGallery != null) btnGallery.setOnClickListener(v -> openGallery());
        if (btnSubmit != null) btnSubmit.setOnClickListener(v -> submit());
    }

    // ─── البحث عن البلاغ بكوده ──────────────────────────────
    private void lookupLinkedReport() {
        if (etLinkedReportCode == null || etLinkedReportCode.getText() == null) return;
        String code = etLinkedReportCode.getText().toString().trim();
        if (code.isEmpty()) {
            Toast.makeText(this, "أدخل كود البلاغ", Toast.LENGTH_SHORT).show();
            return;
        }
        if (tvLinkedReportInfo != null) {
            tvLinkedReportInfo.setText("🔍 جارٍ البحث...");
            tvLinkedReportInfo.setVisibility(android.view.View.VISIBLE);
        }
        // البحث في reports node بكود reportId أو personName
        com.google.firebase.database.FirebaseDatabase.getInstance()
            .getReference("reports").child(code)
            .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot snap) {
                    if (snap.exists()) {
                        linkedReportId = code;
                        String name = snap.child("personName").getValue(String.class);
                        if (tvLinkedReportInfo != null) {
                            tvLinkedReportInfo.setText("✅ تم الربط بـ: "
                                + (name != null ? name : code));
                            tvLinkedReportInfo.setTextColor(0xFF2E7D32);
                        }
                    } else {
                        linkedReportId = null;
                        if (tvLinkedReportInfo != null) {
                            tvLinkedReportInfo.setText("❌ لم يُعثر على بلاغ بهذا الكود");
                            tvLinkedReportInfo.setTextColor(0xFFC62828);
                        }
                    }
                }
                @Override
                public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError e) {
                    if (tvLinkedReportInfo != null) tvLinkedReportInfo.setText("خطأ: " + e.getMessage());
                }
            });
    }

    private void setupGovernorates() {
List<String> govsList = EgyptAddressHelper.getGovernorates();
String[] govs = govsList.toArray(new String[0]);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, govs);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spGovernorates.setAdapter(adapter);
    }

    // ─── Camera / Gallery ────────────────────────────────────────

    private void openCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA}, REQ_PERM);
            return;
        }
        File photoFile;
        try {
            photoFile = File.createTempFile("sighting_", ".jpg",
                getExternalFilesDir("sightings"));
        } catch (IOException e) {
            Toast.makeText(this, "خطأ في إنشاء ملف الصورة", Toast.LENGTH_SHORT).show();
            return;
        }
        photoUri = FileProvider.getUriForFile(this,
            getPackageName() + ".provider", photoFile);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        startActivityForResult(intent, REQ_CAMERA);
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, REQ_GALLERY);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK) return;
        try {
            if (req == REQ_CAMERA && photoUri != null) {
                photoBitmap = MediaStore.Images.Media.getBitmap(
                    getContentResolver(), photoUri);
            } else if (req == REQ_GALLERY && data != null && data.getData() != null) {
                photoUri    = data.getData();
                photoBitmap = MediaStore.Images.Media.getBitmap(
                    getContentResolver(), photoUri);
            }
            if (photoBitmap != null) {
                ivPhoto.setImageBitmap(photoBitmap);
                cardPhoto.setVisibility(View.VISIBLE);
                compareSightingWithDatabase(photoBitmap);
            }
        } catch (IOException e) {
            Toast.makeText(this, "خطأ في تحميل الصورة", Toast.LENGTH_SHORT).show();
        }
    }

    // ─── Face Comparison ────────────────────────────────────────

    private void compareSightingWithDatabase(Bitmap bitmap) {
        if (tvMatchResult == null) return;
        tvMatchResult.setVisibility(View.VISIBLE);
        tvMatchResult.setText("🔍 جارٍ البحث في قاعدة البيانات...");

        FaceEmbeddingManager.extractEmbedding(bitmap,
            new FaceEmbeddingManager.EmbeddingCallback() {
                @Override public void onEmbeddingReady(float[] emb) {
                    findMatchingReports(emb);
                }
                @Override public void onError(String error) {
                    if (tvMatchResult != null)
                        tvMatchResult.setText(
                            "⚠️ لم يُكتشف وجه واضح.\nحاول التقاط صورة أوضح للوجه.");
                }
            });
    }

    private void findMatchingReports(float[] sightingEmb) {
        FirebaseDatabase.getInstance().getReference("reports")
            .orderByChild("status").equalTo("approved")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    int matchCount = 0;
                    StringBuilder matchInfo = new StringBuilder();

                    for (DataSnapshot c : snap.getChildren()) {
                        float maxSim = 0f;
                        float storedQuality = 0f;

                        // V3: embeddings array
                        DataSnapshot embArr = c.child("embeddings");
                        if (embArr.exists()) {
                            for (DataSnapshot embSnap : embArr.getChildren()) {
                                String vecStr = embSnap.child("vector").getValue(String.class);
                                if (vecStr == null) continue;
                                float[] vec = FaceEmbeddingManager.stringToEmbedding(vecStr);
                                if (vec == null) continue;
                                float sim = FaceEmbeddingManager.cosineSimilarity(sightingEmb, vec);
                                if (sim > maxSim) maxSim = sim;
                                Double q = embSnap.child("qualityScore").getValue(Double.class);
                                if (q != null && q.floatValue() > storedQuality)
                                    storedQuality = q.floatValue();
                            }
                        }

                        // V2 fallback
                        if (maxSim == 0f) {
                            String legacyEmb = c.child("faceEmbedding").getValue(String.class);
                            if (legacyEmb != null && !legacyEmb.isEmpty()) {
                                float[] vec = FaceEmbeddingManager.stringToEmbedding(legacyEmb);
                                if (vec != null)
                                    maxSim = FaceEmbeddingManager.cosineSimilarity(sightingEmb, vec);
                            }
                        }

                        if (maxSim < 0.55f) continue;

                        float threshold = DynamicThresholdEngine.computeThreshold(0.5f, storedQuality);
                        DynamicThresholdEngine.MatchStatus status =
                            DynamicThresholdEngine.classify(maxSim, threshold, 0.5f);

                        if (status == DynamicThresholdEngine.MatchStatus.AUTO_MATCH
                                || status == DynamicThresholdEngine.MatchStatus.REVIEW_REQUIRED) {
                            matchCount++;
                            String personName = c.child("personName").getValue(String.class);
                            int percent = (int)(maxSim * 100);
                            matchInfo.append(
                                status == DynamicThresholdEngine.MatchStatus.AUTO_MATCH
                                    ? "✅ " : "⚠️ ")
                                .append(personName != null ? personName : "مجهول")
                                .append(" — تطابق ").append(percent).append("%\n");
                        }
                    }

                    final int finalCount = matchCount;
                    final String finalInfo = matchInfo.toString();
                    runOnUiThread(() -> {
                        if (tvMatchResult == null) return;
                        if (finalCount > 0) {
                            tvMatchResult.setText(
                                "🎉 وُجد " + finalCount + " تطابق محتمل!\n\n" + finalInfo);
                            tvMatchResult.setTextColor(0xFF2E7D32);
                        } else {
                            tvMatchResult.setText(
                                "ℹ️ لا يوجد تطابق حالياً.\n"
                                + "سيُحفظ البلاغ للمقارنة المستقبلية.");
                        }
                    });
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {
                    if (tvMatchResult != null)
                        tvMatchResult.setText("خطأ في البحث: " + e.getMessage());
                }
            });
    }

    // ─── Submit ──────────────────────────────────────────────────

    private void submit() {
        if (isUploading) return;

        String description = etDescription != null && etDescription.getText() != null
            ? etDescription.getText().toString().trim() : "";
        String location = etLocation != null && etLocation.getText() != null
            ? etLocation.getText().toString().trim() : "";
        String governorate = spGovernorates.getSelectedItem() != null
            ? spGovernorates.getSelectedItem().toString() : "";

        if (description.isEmpty()) {
            Toast.makeText(this, "أضف وصفاً للشخص الذي رأيته", Toast.LENGTH_SHORT).show();
            return;
        }
        if (photoBitmap == null) {
            Toast.makeText(this, "يجب إضافة صورة للشخص", Toast.LENGTH_SHORT).show();
            return;
        }

        isUploading = true;
        if (btnSubmit != null) btnSubmit.setEnabled(false);
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (tvStatus != null) {
            if (tvStatus != null) tvStatus.setText("جارٍ رفع البلاغ...");
            if (tvStatus != null) tvStatus.setVisibility(View.VISIBLE);
        }

        // تحويل الصورة إلى bytes مباشرةً (بدون ImageCompressor لأن signature مختلف)
        uploadSighting(photoBitmap, description, location, governorate);
    }

    private void uploadSighting(Bitmap bmp, String description,
                                 String location, String governorate) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
            ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anonymous";
        String sightingId = "sighting_" + System.currentTimeMillis();

        // ضغط إلى JPEG bytes
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 75, baos);
        byte[] imageData = baos.toByteArray();

        StorageReference storageRef = FirebaseStorage.getInstance()
            .getReference("sightings").child(sightingId + ".jpg");

        storageRef.putBytes(imageData)
            .addOnSuccessListener(snap ->
                storageRef.getDownloadUrl().addOnSuccessListener(uri ->
                    saveSightingToFirebase(sightingId, uid, description,
                        location, governorate, uri.toString())))
            .addOnFailureListener(e ->
                onUploadFinished(false, "فشل رفع الصورة: " + e.getMessage()));
    }

    private void saveSightingToFirebase(String sightingId, String uid, String description,
                                         String location, String governorate, String imageUrl) {
        FaceEmbeddingManager.extractEmbedding(photoBitmap,
            new FaceEmbeddingManager.EmbeddingCallback() {
                @Override public void onEmbeddingReady(float[] emb) {
                    doSave(sightingId, uid, description, location, governorate,
                        imageUrl, FaceEmbeddingManager.embeddingToString(emb));
                }
                @Override public void onError(String err) {
                    doSave(sightingId, uid, description, location, governorate, imageUrl, "");
                }
            });
    }

    private void doSave(String sightingId, String uid, String description,
                         String location, String governorate,
                         String imageUrl, String embedding) {
        Map<String, Object> data = new HashMap<>();
        data.put("sightingId",    sightingId);
        data.put("reportedBy",    uid);
        data.put("description",   description);
        data.put("location",      location);
        data.put("governorate",   governorate);
        data.put("lat",           currentLat);
        data.put("lng",           currentLng);
        data.put("imageUrl",      imageUrl);
        data.put("faceEmbedding", embedding);
        data.put("timestamp",     System.currentTimeMillis());
        data.put("status",        "pending");
        // ربط البلاغ بحالة مفقودة إن وُجدت
        if (linkedReportId != null && !linkedReportId.isEmpty()) {
            data.put("linkedReportId", linkedReportId);
            // تحديث حالة البلاغ الأصلي بإضافة مشاهدة مرتبطة
            com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("reports").child(linkedReportId)
                .child("sightingsCount")
                .runTransaction(new com.google.firebase.database.Transaction.Handler() {
                    @androidx.annotation.NonNull
                    @Override
                    public com.google.firebase.database.Transaction.Result doTransaction(
                            @androidx.annotation.NonNull com.google.firebase.database.MutableData d) {
                        Integer count = d.getValue(Integer.class);
                        d.setValue(count == null ? 1 : count + 1);
                        return com.google.firebase.database.Transaction.success(d);
                    }
                    @Override
                    public void onComplete(com.google.firebase.database.DatabaseError e,
                                           boolean committed,
                                           com.google.firebase.database.DataSnapshot s) {}
                });
        }

        FirebaseDatabase.getInstance().getReference("sightings")
            .child(sightingId).setValue(data)
            .addOnSuccessListener(v -> {
                PointsManager.addPoints(PointsManager.ACTION_SIGHTING_REPORTED, "رؤية جديدة");
                if (!embedding.isEmpty()) {
                    CrossMatchManager.matchSightingWithReports(sightingId, uid, embedding);
                }
                onUploadFinished(true, "✅ تم رفع البلاغ بنجاح! شكراً على مساعدتك.");
            })
            .addOnFailureListener(e ->
                onUploadFinished(false, "خطأ: " + e.getMessage()));
    }

    private void onUploadFinished(boolean success, String message) {
        runOnUiThread(() -> {
            isUploading = false;
            if (btnSubmit != null) btnSubmit.setEnabled(true);
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            if (tvStatus != null) {
                if (tvStatus != null) tvStatus.setText(message);
                tvStatus.setTextColor(success ? 0xFF2E7D32 : 0xFFC62828);
                if (tvStatus != null) tvStatus.setVisibility(View.VISIBLE);
            }
            if (success) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    // ─── Location ────────────────────────────────────────────────

    private void requestLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            com.google.android.gms.location.FusedLocationProviderClient fused =
                com.google.android.gms.location.LocationServices
                    .getFusedLocationProviderClient(this);
            fused.getLastLocation().addOnSuccessListener(loc -> {
                if (loc != null) {
                    currentLat = loc.getLatitude();
                    currentLng = loc.getLongitude();
                }
            });
        }
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }
}
