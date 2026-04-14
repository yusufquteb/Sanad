package com.missingpersons.app.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import androidx.core.content.FileProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.database.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.*;
import com.missingpersons.app.R;
import com.missingpersons.app.models.ReportModel;
import com.missingpersons.app.utils.CoilImageLoader;
import com.missingpersons.app.utils.FaceMatcher;
import com.missingpersons.app.utils.FaceSelectionDialog;
import com.missingpersons.app.utils.PermissionHelper;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * SearchActivity — البحث بالصورة
 *
 * إصلاحات v2.1:
 * ✅ إصلاح مشكلة كاميرا البحث (FileProvider URI)
 * ✅ اختيار الوجه عند وجود عدة وجوه في صورة البحث
 * ✅ Coil بدلاً من Glide
 * ✅ Material 3 Design
 */
public class SearchActivity extends AppCompatActivity {

    private static final int REQ_CAMERA  = 301;
    private static final int REQ_GALLERY = 302;
    private static final String TAG = "SearchActivity";

    private ImageView     ivSearchPhoto;
    private TextView      tvStatus, tvFaceDetected;
    private MaterialButton btnPickGallery, btnPickCamera, btnStartSearch;
    private CircularProgressIndicator progressIndicator;
    private LinearLayout  layoutResults;

    private Uri    selectedImageUri;
    private Bitmap selectedBitmap;
    private FaceMatcher faceMatcher;
    private DatabaseReference reportsRef;
    private List<ReportModel> matchResults = new ArrayList<>();

    // ─── مهم: حفظ URI الكاميرا ───
    private Uri  tempCameraUri;
    private File tempCameraFile;

    @Override
    protected void attachBaseContext(android.content.Context c) {
        super.attachBaseContext(com.missingpersons.app.utils.LanguageHelper.applyLanguage(c));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
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
            getSupportActionBar().setTitle("البحث عن شخص بالصورة");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        initViews();
        reportsRef = FirebaseDatabase.getInstance().getReference("reports");
        faceMatcher = new FaceMatcher(this);
    }

    private void initViews() {
        ivSearchPhoto    = findViewById(R.id.iv_search_photo);
        tvStatus         = findViewById(R.id.tv_search_status);
        tvFaceDetected   = findViewById(R.id.tv_face_detected);
        btnPickGallery   = findViewById(R.id.btn_pick_gallery);
        btnPickCamera    = findViewById(R.id.btn_pick_camera);
        btnStartSearch   = findViewById(R.id.btn_start_search);
        progressIndicator = findViewById(R.id.progress_search);
        layoutResults    = findViewById(R.id.layout_results);

        if (btnPickGallery  != null) btnPickGallery.setOnClickListener(v -> openGallery());
        if (btnPickCamera   != null) btnPickCamera.setOnClickListener(v -> openCamera());
        if (btnStartSearch  != null) {
            btnStartSearch.setOnClickListener(v -> startFaceDetectionAndSearch());
            setSearchEnabled(false);
        }
    }

    // ─── فتح الكاميرا بـ FileProvider (إصلاح) ───
    private void openCamera() {
        if (!PermissionHelper.hasCameraPermission(this)) {
            PermissionHelper.requestCameraPermission(this);
            return;
        }
        try {
            File dir = new File(getFilesDir(), "search");
            if (!dir.exists()) dir.mkdirs();
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            tempCameraFile = new File(dir, "search_" + ts + ".jpg");

            tempCameraUri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".provider",
                tempCameraFile
            );

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, tempCameraUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(intent, REQ_CAMERA);
            } else {
                setStatus("❌ لا يوجد تطبيق كاميرا");
            }
        } catch (Exception e) {
            setStatus("❌ خطأ في الكاميرا: " + e.getMessage());
        }
    }

    private void openGallery() {
        if (!PermissionHelper.hasStoragePermission(this)) {
            PermissionHelper.requestStoragePermission(this);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, REQ_GALLERY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;

        if (requestCode == REQ_GALLERY && data != null && data.getData() != null) {
            selectedImageUri = data.getData();
            ivSearchPhoto.setImageURI(selectedImageUri);
            try {
                InputStream is = getContentResolver().openInputStream(selectedImageUri);
                selectedBitmap = BitmapFactory.decodeStream(is);
                setSearchEnabled(selectedBitmap != null);
                setStatus("تم اختيار الصورة — اضغط 'ابدأ البحث'");
            } catch (IOException e) {
                setStatus("❌ خطأ في قراءة الصورة");
            }

        } else if (requestCode == REQ_CAMERA) {
            // ─── استخدام الملف المحفوظ لا data.getExtras() ───
            if (tempCameraFile != null && tempCameraFile.exists() && tempCameraFile.length() > 0) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 2;
                selectedBitmap = BitmapFactory.decodeFile(tempCameraFile.getAbsolutePath(), opts);
                selectedImageUri = tempCameraUri;
                ivSearchPhoto.setImageURI(tempCameraUri);
                setSearchEnabled(selectedBitmap != null);
                setStatus("تم التقاط الصورة — اضغط 'ابدأ البحث'");
            } else {
                setStatus("❌ فشل التقاط الصورة");
            }
        }
    }

    private void startFaceDetectionAndSearch() {
        if (selectedBitmap == null) {
            setStatus("اختر صورة أولاً");
            return;
        }

        setLoadingState(true);
        setStatus("🔍 جارٍ تحليل الوجه...");
        tvFaceDetected.setVisibility(View.GONE);
        layoutResults.removeAllViews();
        matchResults.clear();

        InputImage image = InputImage.fromBitmap(selectedBitmap, 0);

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.10f)
                .enableTracking()
                .build();

        FaceDetection.getClient(options).process(image)
            .addOnSuccessListener(faces -> {
                if (faces.isEmpty()) {
                    setLoadingState(false);
                    setStatus("❌ لم يتم اكتشاف وجه في الصورة\nحاول بصورة أوضح");
                } else if (faces.size() == 1) {
                    tvFaceDetected.setVisibility(View.VISIBLE);
                    setFaceInfo("✅ تم اكتشاف وجه — جارٍ البحث...");
                    searchInDatabase(faces.get(0));
                } else {
                    // عدة وجوه — اعرض حوار الاختيار
                    setLoadingState(false);
                    tvFaceDetected.setVisibility(View.VISIBLE);
                    setFaceInfo("👥 تم اكتشاف " + faces.size() + " وجوه — اختر الشخص");
                    showFaceSelectionDialog(faces);
                }
            })
            .addOnFailureListener(e -> {
                setLoadingState(false);
                setStatus("❌ خطأ في تحليل الصورة: " + e.getMessage());
            });
    }

    private void showFaceSelectionDialog(List<Face> faces) {
        FaceSelectionDialog dialog = new FaceSelectionDialog(this, selectedBitmap, faces);
        dialog.show(selectedFace -> {
            setLoadingState(true);
            setStatus("🔍 جارٍ البحث في قاعدة البيانات...");
            searchInDatabase(selectedFace);
        });
    }

    private void searchInDatabase(Face detectedFace) {
        setStatus("🔍 جارٍ البحث في قاعدة البيانات...");

        // البحث في الحالات الموافق عليها فقط
        reportsRef.orderByChild("approved").equalTo(true)
                .limitToLast(100)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists() || snapshot.getChildrenCount() == 0) {
                        setLoadingState(false);
                        setStatus("لا توجد بلاغات في قاعدة البيانات حتى الآن");
                        return;
                    }

                    List<ReportModel> reports = new ArrayList<>();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        ReportModel report = child.getValue(ReportModel.class);
                        if (report != null) reports.add(report);
                    }

                    setStatus("🔍 جارٍ مقارنة " + reports.size() + " بلاغ...");
                    compareWithReports(reports, detectedFace);
                })
                .addOnFailureListener(e -> {
                    setLoadingState(false);
                    setStatus("❌ خطأ في الاتصال بقاعدة البيانات");
                });
    }

    private void compareWithReports(List<ReportModel> reports, Face detectedFace) {
        // استخرج embedding للوجه المحدد
        new Thread(() -> {
            android.graphics.Rect box = detectedFace.getBoundingBox();
            int l = Math.max(0, box.left),   t = Math.max(0, box.top);
            int r = Math.min(selectedBitmap.getWidth(),  box.right);
            int b = Math.min(selectedBitmap.getHeight(), box.bottom);
            android.graphics.Bitmap faceCrop = (r > l && b > t)
                ? android.graphics.Bitmap.createBitmap(selectedBitmap, l, t, r - l, b - t)
                : selectedBitmap;

            float[] queryVec = com.missingpersons.app.utils.FaceEmbeddingManager
                .extractEmbeddingSync(this, faceCrop);

            // رتّب النتائج حسب التشابه
            List<android.util.Pair<ReportModel, Float>> ranked = new ArrayList<>();
            for (ReportModel rep : reports) {
                String storedEmb = rep.getFaceEmbedding();
                float sim = 0f;
                if (queryVec != null && storedEmb != null && !storedEmb.isEmpty()) {
                    float[] storedVec = com.missingpersons.app.utils.FaceEmbeddingManager
                        .stringToEmbedding(storedEmb);
                    if (storedVec != null)
                        sim = com.missingpersons.app.utils.FaceEmbeddingManager
                            .cosineSimilarity(queryVec, storedVec);
                }
                ranked.add(new android.util.Pair<>(rep, sim));
            }
            // الأعلى تشابهاً أولاً
            ranked.sort((a, b2) -> Float.compare(b2.second, a.second));

            // أضف النتائج فوق 30% فقط (أو كل النتائج لو queryVec فاشل)
            matchResults.clear();
            float minSim = (queryVec != null) ? 0.30f : 0f;
            for (android.util.Pair<ReportModel, Float> p : ranked) {
                if (p.second >= minSim) matchResults.add(p.first);
                if (matchResults.size() >= 20) break; // أقصى 20 نتيجة
            }

            runOnUiThread(() -> {
                setLoadingState(false);
                if (matchResults.isEmpty()) {
                    setStatus("لم يتم العثور على تطابق قريب");
                } else {
                    float best = ranked.isEmpty() ? 0f : ranked.get(0).second;
                    String pct = queryVec != null ? " (أعلى تشابه: " + (int)(best*100) + "%)" : "";
                    setStatus("تم العثور على " + matchResults.size() + " نتيجة" + pct);
                    showResults();
                }
            });
        }).start();
    }

    private void showResults() {
        layoutResults.removeAllViews();
        for (ReportModel report : matchResults) {
            View resultCard = getLayoutInflater().inflate(R.layout.item_result, layoutResults, false);
            ImageView ivResult   = resultCard.findViewById(R.id.iv_result_image);
            TextView  tvLocation = resultCard.findViewById(R.id.tv_result_location);
            TextView  tvTime     = resultCard.findViewById(R.id.tv_result_time);
            MaterialButton btnContact = resultCard.findViewById(R.id.btn_contact);

            // تحميل بـ Coil
            CoilImageLoader.loadRounded(this, report.getImageUrl(), ivResult,
                R.drawable.ic_face_placeholder, 12f);

            tvLocation.setText("📍 " + (report.getLocationText() != null
                    ? report.getLocationText() : "موقع غير محدد"));

            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("ar"));
            tvTime.setText("🕐 " + sdf.format(new Date(report.getTimestamp())));

            btnContact.setOnClickListener(v -> {
                Intent intent = new Intent(this, ChatActivity.class);
                intent.putExtra("reportId",    report.getReportId());
                intent.putExtra("reportOwner", report.getReporterId());
                startActivity(intent);
            });

            layoutResults.addView(resultCard);
        }
    }

    private void setLoadingState(boolean loading) {
        progressIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        setSearchEnabled(!loading);
        btnPickGallery.setEnabled(!loading);
        btnPickCamera.setEnabled(!loading);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (faceMatcher != null) faceMatcher.release();
    }

    @Override public boolean onSupportNavigateUp() { onBackPressed(); return true; }

    /** null-safe wrapper لتحديث حالة البحث */
    private void setStatus(String msg) {
        if (tvStatus != null) setStatus(msg);
    }
    private void setFaceInfo(String msg) {
        if (tvFaceDetected != null) {
            tvFaceDetected.setVisibility(msg == null ? View.GONE : View.VISIBLE);
            if (msg != null) setFaceInfo(msg);
        }
    }
    private void setSearchEnabled(boolean enabled) {
        if (btnStartSearch != null) setSearchEnabled(enabled);
    }
    private void showProgress(boolean show) {
        if (progressIndicator != null)
            progressIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] perms,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, perms, grantResults);
        if (requestCode == PermissionHelper.REQUEST_CAMERA
                && PermissionHelper.areAllGranted(grantResults)) {
            openCamera();
        } else if (requestCode == PermissionHelper.REQUEST_STORAGE
                && PermissionHelper.areAllGranted(grantResults)) {
            openGallery();
        }
    }
}
