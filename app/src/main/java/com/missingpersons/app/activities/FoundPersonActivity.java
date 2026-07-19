package com.missingpersons.app.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.google.firebase.storage.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.*;
import com.missingpersons.app.R;
import com.missingpersons.app.utils.CoilImageLoader;
import com.missingpersons.app.utils.FaceAnalyzer;
import com.missingpersons.app.utils.FaceEmbeddingManager;
import com.missingpersons.app.utils.FaceSelectionDialog;
import com.missingpersons.app.utils.AdsManager;
import com.missingpersons.app.utils.PermissionHelper;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * FoundPersonActivity v3.0 — وجدت شخصاً مجهولاً
 *
 * ✅ ينشئ بوست (found_persons) ويعرض على الإدارة للموافقة
 * ✅ يقارن الوجه تلقائياً مع قاعدة بيانات المفقودين (reports)
 * ✅ يرسل إشعار لصاحب البلاغ المطابق
 * ✅ يعرض الوجوه لاختيار الشخص الصحيح عند تعدد الوجوه
 * ✅ يحفظ صورة الوجه المقصوص فقط (وليس الصورة كاملة)
 * ✅ مدمج مع بحث بالوجه
 */
public class FoundPersonActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA  = 101;
    private static final int REQUEST_GALLERY = 102;
    private static final int REQUEST_MAP     = 103;

    private ImageView          ivFoundPhoto;
    private TextInputEditText  etFoundLocation, etFoundNote, etFoundDesc;
    private TextInputEditText  etFoundCalledName;                          // الاسم المنادى به
    private com.google.android.material.chip.ChipGroup chipGroupHealth;   // الحالة الصحية
    private com.google.android.material.card.MaterialCardView cardHealthAlert; // تحذير أولوية
    private MaterialButton     btnPickPhoto, btnSubmit, btnPickMap;
    private LinearProgressIndicator progressBar;
    private TextView           tvResult, tvStatus;
    private LinearLayout       layoutResult, layoutMatches;

    private Uri    tempCameraUri;
    private File   tempCameraFile;

    private Bitmap originalBitmap;   // الصورة الأصلية
    private Bitmap croppedFaceBitmap; // الوجه المقصوص فقط
    private float[] faceEmbedding;

    private double selectedLat = 0, selectedLng = 0;
    private String selectedAddress = "";

    @Override
    protected void attachBaseContext(android.content.Context c) {
        super.attachBaseContext(com.missingpersons.app.utils.LanguageHelper.applyLanguage(c));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_found_person);
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
            getSupportActionBar().setTitle("📸 وجدت شخصاً مجهولاً");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        initViews();
    }

    private void initViews() {
        ivFoundPhoto    = findViewById(R.id.iv_found_photo);
        etFoundLocation   = findViewById(R.id.et_found_location);
        etFoundCalledName = findViewById(R.id.et_found_called_name);
        chipGroupHealth   = findViewById(R.id.chip_group_health);
        cardHealthAlert   = findViewById(R.id.card_health_alert);

        // إظهار/إخفاء تحذير الأولوية عند تغيير الحالة الصحية
        if (chipGroupHealth != null) {
            chipGroupHealth.setOnCheckedStateChangeListener((group, checkedIds) -> {
                boolean urgent = checkedIds.contains(R.id.chip_health_critical)
                    || checkedIds.contains(R.id.chip_health_unconscious);
                if (cardHealthAlert != null)
                    cardHealthAlert.setVisibility(urgent
                        ? android.view.View.VISIBLE : android.view.View.GONE);
            });
        }
        etFoundNote     = findViewById(R.id.et_found_note);
        etFoundDesc     = findViewById(R.id.et_found_desc);
        btnPickPhoto    = findViewById(R.id.btn_pick_found_photo);
        btnSubmit       = findViewById(R.id.btn_submit_found);
        btnPickMap      = findViewById(R.id.btn_pick_map_found);
        progressBar     = findViewById(R.id.progress_found);
        tvResult        = findViewById(R.id.tv_found_result);
        tvStatus        = findViewById(R.id.tv_found_status);
        layoutResult    = findViewById(R.id.layout_found_result);
        layoutMatches   = findViewById(R.id.layout_matches);

        btnPickPhoto.setOnClickListener(v -> showPhotoOptions());

        if (btnSubmit != null) {
            if (btnSubmit != null) btnSubmit.setEnabled(false);
            if (btnSubmit != null) btnSubmit.setOnClickListener(v -> submitFoundReport());
        }

        if (btnPickMap != null)
            btnPickMap.setOnClickListener(v -> openMapPicker());
    }

    // ─────────────────────────────────────────
    //  PHOTO SELECTION
    // ─────────────────────────────────────────
    private void showPhotoOptions() {
        String[] options = {"📷 كاميرا", "🖼️ المعرض"};
        new AlertDialog.Builder(this)
            .setTitle("التقط أو اختر صورة الشخص")
            .setItems(options, (d, which) -> {
                if (which == 0) openCamera();
                else openGallery();
            }).show();
    }

    private void openCamera() {
        PermissionHelper.ensureBiometricConsent(this, accepted -> {
            if (!accepted) return;
            try {
                File dir = new File(getFilesDir(), "found");
                if (!dir.exists()) dir.mkdirs();
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                tempCameraFile = new File(dir, "found_" + ts + ".jpg");
                tempCameraUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", tempCameraFile);
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, tempCameraUri);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (intent.resolveActivity(getPackageManager()) != null)
                    startActivityForResult(intent, REQUEST_CAMERA);
                else
                    showStatus("❌ لا يوجد تطبيق كاميرا");
            } catch (Exception e) {
                showStatus("❌ خطأ في الكاميرا: " + e.getMessage());
            }
        });
    }

    private void openGallery() {
        PermissionHelper.ensureBiometricConsent(this, accepted -> {
            if (!accepted) return;
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_GALLERY);
        });
    }

    private void openMapPicker() {
        Intent intent = new Intent(this, MapActivity.class);
        intent.putExtra(MapActivity.EXTRA_PICK_MODE, true);
        startActivityForResult(intent, REQUEST_MAP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_MAP && resultCode == RESULT_OK && data != null) {
            selectedLat = data.getDoubleExtra(MapActivity.EXTRA_LAT, 0);
            selectedLng = data.getDoubleExtra(MapActivity.EXTRA_LNG, 0);
            selectedAddress = data.getStringExtra(MapActivity.EXTRA_ADDRESS);
            if (selectedAddress == null) selectedAddress = "";
            if (etFoundLocation != null) etFoundLocation.setText(selectedAddress);
            return;
        }

        if (resultCode != RESULT_OK) {
            if (requestCode == REQUEST_CAMERA && tempCameraFile != null) tempCameraFile.delete();
            return;
        }

        if (requestCode == REQUEST_CAMERA) {
            if (tempCameraFile != null && tempCameraFile.exists() && tempCameraFile.length() > 0) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 2;
                originalBitmap = BitmapFactory.decodeFile(tempCameraFile.getAbsolutePath(), opts);
                if (originalBitmap != null) processSelectedPhoto(originalBitmap);
            } else {
                showStatus("❌ فشل التقاط الصورة");
            }
        } else if (requestCode == REQUEST_GALLERY && data != null && data.getData() != null) {
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 2;
                originalBitmap = BitmapFactory.decodeStream(
                    getContentResolver().openInputStream(data.getData()), null, opts);
                if (originalBitmap != null) processSelectedPhoto(originalBitmap);
            } catch (IOException e) {
                showStatus("❌ خطأ في تحميل الصورة");
            }
        }
    }

    // ─────────────────────────────────────────
    //  FACE DETECTION + SELECTION
    // ─────────────────────────────────────────
    private void processSelectedPhoto(Bitmap bmp) {
        showStatus("🔍 جارٍ تحليل الوجه...");
        setLoading(true);

        InputImage image = InputImage.fromBitmap(bmp, 0);
        FaceDetectorOptions opts = new FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.08f)
            .build();

        FaceDetection.getClient(opts).process(image)
            .addOnSuccessListener(faces -> {
                if (faces.isEmpty()) {
                    setLoading(false);
                    new AlertDialog.Builder(this)
                        .setTitle("⚠️ لم يُعثَر على وجه")
                        .setMessage("لم يتم اكتشاف وجه واضح. حاول بصورة أوضح.")
                        .setPositiveButton("حسناً", null).show();
                } else if (faces.size() == 1) {
                    onFaceSelected(faces.get(0), bmp);
                } else {
                    setLoading(false);
                    showStatus("👥 تم اكتشاف " + faces.size() + " وجوه — اختر الشخص");
                    FaceSelectionDialog dialog = new FaceSelectionDialog(this, bmp, faces);
                    dialog.show(selectedFace -> {
                        setLoading(true);
                        onFaceSelected(selectedFace, bmp);
                    });
                }
            })
            .addOnFailureListener(e -> {
                setLoading(false);
                showStatus("❌ خطأ في تحليل الصورة: " + e.getMessage());
            });
    }

    private String detectedGender = "غير محدد";
    private String detectedAgeRange = "غير محدد";
    private int detectedAge = 25;

    /** يُستدعى بعد اختيار وجه واحد */
    private void onFaceSelected(Face face, Bitmap original) {
        // ─── قص الوجه فقط ───
        Rect box = face.getBoundingBox();
        int x = Math.max(0, box.left);
        int y = Math.max(0, box.top);
        int w = Math.min(box.width(), original.getWidth() - x);
        int h = Math.min(box.height(), original.getHeight() - y);

        int padX = (int)(w * 0.15);
        int padY = (int)(h * 0.15);
        x = Math.max(0, x - padX);
        y = Math.max(0, y - padY);
        w = Math.min(w + padX * 2, original.getWidth() - x);
        h = Math.min(h + padY * 2, original.getHeight() - y);

        try {
            croppedFaceBitmap = Bitmap.createBitmap(original, x, y, w, h);
        } catch (Exception e) {
            croppedFaceBitmap = original;
        }

        ivFoundPhoto.setImageBitmap(croppedFaceBitmap);
        showStatus("✅ تم تحديد الوجه — جارٍ التحليل...");

        // ─── تحليل النوع والعمر ───
        FaceAnalyzer.analyze(original, new FaceAnalyzer.AnalysisCallback() {
            @Override
            public void onAnalysisComplete(FaceAnalyzer.FaceAnalysisResult result,
                                            Face f, Bitmap cropped) {
                runOnUiThread(() -> {
                    if (result.hasFace) {
                        detectedGender = result.estimatedGender;
                        detectedAge = result.estimatedAge;
                        detectedAgeRange = result.estimatedAgeRange;

                        int gPct = (int)(result.genderConfidence * 100);
                        showStatus("✅ تحليل: " + detectedGender + " (" + gPct + "%) — "
                            + result.estimatedAgeRange);

                        // ملء وصف تلقائي
                        if (etFoundDesc != null && (etFoundDesc.getText() == null
                                || etFoundDesc.getText().toString().isEmpty())) {
                            etFoundDesc.setText(detectedGender + " — " + detectedAgeRange);
                        }
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> showStatus("⚠️ تعذر تحليل الملامح"));
            }
        });

        // ─── استخراج embedding ───
        FaceEmbeddingManager.extractEmbedding(croppedFaceBitmap, new FaceEmbeddingManager.EmbeddingCallback() {
            @Override
            public void onEmbeddingReady(float[] embedding) {
                faceEmbedding = embedding;
                runOnUiThread(() -> {
                    showStatus("✅ الوجه جاهز — ابحث عن تطابق أو أرسل البلاغ");
                    if (btnSubmit != null) btnSubmit.setEnabled(true);
                    setLoading(false);
                    // بدء البحث التلقائي
                    searchForMatches();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    showStatus("⚠️ تم حفظ الصورة بدون بصمة: " + error);
                    if (btnSubmit != null) btnSubmit.setEnabled(true);
                    setLoading(false);
                });
            }
        });
    }

    // ─────────────────────────────────────────
    //  FACE MATCHING (auto-search)
    // ─────────────────────────────────────────
    private void searchForMatches() {
        if (faceEmbedding == null) return;

        showStatus("🔍 جارٍ البحث في قاعدة بيانات المفقودين...");

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("reports");
        ref.orderByChild("status").equalTo("approved")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    List<MatchResult> matches = new ArrayList<>();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        String storedEmb = child.child("faceEmbedding").getValue(String.class);
                        if (storedEmb == null || storedEmb.isEmpty()) continue;
                        float[] storedVec = FaceEmbeddingManager.stringToEmbedding(storedEmb);
                        if (storedVec == null) continue;
                        float similarity = FaceEmbeddingManager.cosineSimilarity(faceEmbedding, storedVec);
                        if (similarity >= FaceEmbeddingManager.MATCH_THRESHOLD) {
                            matches.add(new MatchResult(
                                child.getKey(),
                                child.child("personName").getValue(String.class),
                                child.child("reporterId").getValue(String.class),
                                child.child("reporterName").getValue(String.class),
                                getFirstImageUrl(child),
                                similarity
                            ));
                        }
                    }
                    runOnUiThread(() -> showMatchResults(matches));
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    runOnUiThread(() -> showStatus("⚠️ تعذر البحث: " + error.getMessage()));
                }
            });
    }

    private String getFirstImageUrl(DataSnapshot snap) {
        Object urlsObj = snap.child("imageUrls").getValue();
        if (urlsObj instanceof List && !((List<?>) urlsObj).isEmpty())
            return ((List<?>) urlsObj).get(0).toString();
        String single = snap.child("imageUrl").getValue(String.class);
        return single != null ? single : "";
    }

    private void showMatchResults(List<MatchResult> matches) {
        if (layoutMatches == null) return;
        layoutMatches.removeAllViews();

        if (matches.isEmpty()) {
            showStatus("😔 لم يُعثَر على تطابق حالياً — أرسل البلاغ وسنبحث لاحقاً");
            layoutResult.setVisibility(View.VISIBLE);
            return;
        }

        matches.sort((a, b) -> Float.compare(b.similarity, a.similarity));
        layoutResult.setVisibility(View.VISIBLE);

        int count = matches.size();
        showStatus("🎉 تم العثور على " + count + " تطابق محتمل!");

        for (MatchResult match : matches) {
            View card = getLayoutInflater().inflate(R.layout.item_result, layoutMatches, false);
            ImageView ivResult   = card.findViewById(R.id.iv_result_image);
            TextView  tvLocation = card.findViewById(R.id.tv_result_location);
            TextView  tvTime     = card.findViewById(R.id.tv_result_time);
            MaterialButton btnContact = card.findViewById(R.id.btn_contact);

            String name = match.personName != null ? match.personName : "مجهول";
            int percent = (int)(match.similarity * 100);

            tvLocation.setText("👤 " + name + " — تطابق " + percent + "%");
            tvTime.setText("📋 بلاغ مفقود مسجل");

            if (!match.imageUrl.isEmpty()) {
                CoilImageLoader.loadRounded(this, match.imageUrl, ivResult,
                    R.drawable.ic_face_placeholder, 12f);
            }

            btnContact.setText("💬 تواصل");
            btnContact.setOnClickListener(v -> {
                if (match.reporterId != null && !match.reporterId.isEmpty()) {
                    Intent intent = new Intent(this, ChatActivity.class);
                    intent.putExtra("otherUid", match.reporterId);
                    intent.putExtra("otherName", match.reporterName != null ? match.reporterName : "صاحب البلاغ");
                    startActivity(intent);
                }
            });

            layoutMatches.addView(card);
        }
    }

    // ─────────────────────────────────────────
    //  SUBMIT FOUND REPORT
    // ─────────────────────────────────────────
    private void submitFoundReport() {
        if (croppedFaceBitmap == null && originalBitmap == null) {
            showStatus("❌ أضف صورة أولاً");
            return;
        }

        // ─── إعلان Interstitial قبل الرفع ───
        AdsManager.getInstance(this).showInterstitialOnSubmit(this, this::doSubmitFound);
    }

    private void doSubmitFound() {
        setLoading(true);
        if (btnSubmit != null) btnSubmit.setEnabled(false);
        showStatus("⬆️ جارٍ رفع البلاغ...");

        String reportId = UUID.randomUUID().toString();
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
            ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anonymous";
        String email = FirebaseAuth.getInstance().getCurrentUser() != null
            ? FirebaseAuth.getInstance().getCurrentUser().getEmail() : "";
        String userName = FirebaseAuth.getInstance().getCurrentUser() != null
            ? FirebaseAuth.getInstance().getCurrentUser().getDisplayName() : "";

        // ─── رفع صورة الوجه المقصوص ───
        Bitmap toUpload = croppedFaceBitmap != null ? croppedFaceBitmap : originalBitmap;
        uploadFaceImage(reportId, toUpload, imageUrl -> {
            // ─── حفظ البيانات ───
            HashMap<String, Object> report = new HashMap<>();
            report.put("reportId",       reportId);
            report.put("type",           "found_person");
            report.put("status",         "pending");
            report.put("approved",       false);
            report.put("imageUrl",       imageUrl);
            report.put("imageUrls",      Collections.singletonList(imageUrl));
            report.put("latitude",       selectedLat);
            report.put("longitude",      selectedLng);
            report.put("locationText",   selectedAddress);
            report.put("manualAddress",  etFoundLocation.getText() != null ? etFoundLocation.getText().toString() : selectedAddress);
            report.put("personName",     etFoundDesc != null && etFoundDesc.getText() != null ? etFoundDesc.getText().toString().trim() : "شخص مجهول");
            // الاسم المنادى به (اختياري)
            String calledName = etFoundCalledName != null && etFoundCalledName.getText() != null
                ? etFoundCalledName.getText().toString().trim() : "";
            if (!calledName.isEmpty()) report.put("calledName", calledName);
            // الحالة الصحية
            String healthStatus = "stable";
            if (chipGroupHealth != null) {
                java.util.List<Integer> checked = chipGroupHealth.getCheckedChipIds();
                if (!checked.isEmpty()) {
                    int cid = checked.get(0);
                    if (cid == R.id.chip_health_critical)    healthStatus = "critical";
                    else if (cid == R.id.chip_health_unconscious) healthStatus = "unconscious";
                }
            }
            report.put("healthStatus", healthStatus);
            // بلاغات حرجة تُعلَّم كـ priority للإدارة
            if (!"stable".equals(healthStatus)) report.put("isPriority", true);
            report.put("personGender",  detectedGender);
            report.put("personAge",     detectedAge);
            report.put("ageRange",      detectedAgeRange);
            report.put("note",           etFoundNote.getText() != null ? etFoundNote.getText().toString() : "");
            report.put("reporterId",     uid);
            report.put("reporterEmail",  email);
            report.put("reporterName",   userName);
            report.put("timestamp",      System.currentTimeMillis());
            if (faceEmbedding != null)
                report.put("faceEmbedding", FaceEmbeddingManager.embeddingToString(faceEmbedding));

            // حفظ في found_persons
            FirebaseDatabase.getInstance().getReference("found_persons")
                .child(reportId).setValue(report)
                .addOnSuccessListener(x -> {
                    // ─── إرسال إشعارات للمطابقات ───
                    if (faceEmbedding != null) {
                        sendMatchNotifications(reportId, imageUrl);
                    }

                    runOnUiThread(() -> {
                        setLoading(false);
                        showStatus("✅ تم إرسال البلاغ للمراجعة — شكراً لمساعدتك!");
                        Toast.makeText(this, "✅ تم الإرسال بنجاح", Toast.LENGTH_LONG).show();
                    });
                })
                .addOnFailureListener(e -> runOnUiThread(() -> {
                    setLoading(false);
                    if (btnSubmit != null) btnSubmit.setEnabled(true);
                    showStatus("❌ فشل الحفظ: " + e.getMessage());
                }));
        });
    }

    private void uploadFaceImage(String reportId, Bitmap bmp, OnImageUploaded callback) {
        try {
            // حفظ مؤقت ثم ضغط بـ ImageCompressor
            File tmp = new File(getFilesDir(), "found_raw_" + reportId + ".jpg");
            FileOutputStream fos = new FileOutputStream(tmp);
            bmp.compress(Bitmap.CompressFormat.JPEG, 95, fos);
            fos.close();

            File file = com.missingpersons.app.utils.ImageCompressor.compressSync(this, tmp);
            tmp.delete();

            String path = "found_persons/" + reportId + "/face.jpg";
            StorageReference ref = FirebaseStorage.getInstance().getReference().child(path);

            ref.putFile(Uri.fromFile(file))
                .addOnSuccessListener(snap ->
                    ref.getDownloadUrl().addOnSuccessListener(uri ->
                        callback.onUploaded(uri.toString())))
                .addOnFailureListener(e -> {
                    showStatus("❌ فشل رفع الصورة: " + e.getMessage());
                    setLoading(false);
                    if (btnSubmit != null) btnSubmit.setEnabled(true);
                });
        } catch (IOException e) {
            showStatus("❌ خطأ: " + e.getMessage());
            setLoading(false);
        }
    }

    interface OnImageUploaded { void onUploaded(String url); }

    // ─────────────────────────────────────────
    //  MATCH NOTIFICATIONS
    // ─────────────────────────────────────────
    private void sendMatchNotifications(String foundReportId, String imageUrl) {
        if (faceEmbedding == null) return;

        FirebaseDatabase.getInstance().getReference("reports")
            .orderByChild("status").equalTo("approved")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        String storedEmb = child.child("faceEmbedding").getValue(String.class);
                        if (storedEmb == null || storedEmb.isEmpty()) continue;
                        float[] storedVec = FaceEmbeddingManager.stringToEmbedding(storedEmb);
                        if (storedVec == null) continue;
                        float similarity = FaceEmbeddingManager.cosineSimilarity(faceEmbedding, storedVec);

                        if (similarity >= FaceEmbeddingManager.MATCH_THRESHOLD) {
                            String reporterId = child.child("reporterId").getValue(String.class);
                            String personName = child.child("personName").getValue(String.class);
                            if (reporterId != null) {
                                int percent = (int)(similarity * 100);
                                HashMap<String, Object> notif = new HashMap<>();
                                notif.put("type", "face_match");
                                notif.put("foundReportId", foundReportId);
                                notif.put("matchedReportId", child.getKey());
                                notif.put("personName", personName);
                                notif.put("similarity", percent);
                                notif.put("imageUrl", imageUrl);
                                notif.put("message", "🎉 تم العثور على تطابق محتمل لـ " + personName + " بنسبة " + percent + "%");
                                notif.put("timestamp", System.currentTimeMillis());
                                notif.put("read", false);

                                FirebaseDatabase.getInstance().getReference("notifications")
                                    .child(reporterId).push().setValue(notif);
                            }
                        }
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
    }

    // ─────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────
    private void showStatus(String msg) {
        if (tvStatus != null) tvStatus.setText(msg);
        if (tvResult != null) tvResult.setText(msg);
    }

    private void setLoading(boolean loading) {
        if (progressBar != null) progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    static class MatchResult {
        String reportId, personName, reporterId, reporterName, imageUrl;
        float similarity;
        MatchResult(String rid, String name, String uid, String rName, String img, float sim) {
            reportId = rid; personName = name; reporterId = uid;
            reporterName = rName; imageUrl = img; similarity = sim;
        }
    }

    @Override public boolean onSupportNavigateUp() { onBackPressed(); return true; }
}
