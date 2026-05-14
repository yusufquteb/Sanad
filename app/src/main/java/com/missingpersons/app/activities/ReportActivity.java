package com.missingpersons.app.activities;

import android.Manifest;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.location.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.google.android.gms.location.*;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.google.firebase.storage.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.*;
import com.missingpersons.app.R;
import com.missingpersons.app.utils.*;
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import com.missingpersons.app.services.ImageModerationService;

public class ReportActivity extends AppCompatActivity {

    public static final String TYPE_MISSING   = "missing";
    public static final String TYPE_FOUND     = "found";
    public static final String TYPE_SIGHTING  = "sighting";
    public static final String TYPE_EMERGENCY = "emergency";
    public static final String TYPE_HOMELESS  = "homeless";

    private static final int REQ_CAMERA  = 101;
    private static final int REQ_GALLERY = 102;
    private static final int REQ_MAP     = 103;
    private static final int REQ_PERM    = 200;
    private static final int MAX_PHOTOS  = 5;
    private static final int MAX_IMAGE_DIM = 1080;

    // ── Containers ──────────────────────────────────────
    private LinearLayout wizardContainer, layoutSuccess;
    private LinearLayout layoutStep1, layoutStep2, layoutStep3, layoutStep4;
    private ScrollView   scrollReport;
    private int          currentStep = 1;

    // ── Step indicator circles ───────────────────────────
    private TextView stepC1, stepC2, stepC3, stepC4;
    private View     stepLine1, stepLine2, stepLine3;
    private TextView tvStepTitle, tvStepBadge, tvBtnBack, tvBtnCancel;

    // ── Age category (auto-derived from age field) ─────────────────────────
    private static final String AGE_CAT_CHILD = "child";
    private static final String AGE_CAT_ADULT = "adult";
    private static final String AGE_CAT_ELDER = "elder";
    private String selectedAgeCategory = AGE_CAT_CHILD; // derived from etAge

    // ── Navigation ───────────────────────────────────────
    private MaterialButton btnPrev, btnNext, btnSubmit;

    // ── Step 2: Report type ──────────────────────────────
    private MaterialCardView cardTypeMissing, cardTypeFound, cardTypeSighting,
                             cardTypeEmergency, cardTypeHomeless;
    private TextView         icCheckMissing, icCheckFound, icCheckSighting,
                             icCheckEmergency, icCheckHomeless;
    private TextView         tvTypeDescription;
    private String           currentType = TYPE_MISSING;
    private String           editReportId = null;
    private String           editReportNode = "reports";

    // ── Step 3: Personal data + photos ──────────────────
    private TextInputEditText  etName, etAge, etPhysicalCondition;
    private TextInputLayout    tilAge;
    private MaterialCardView   cardAgeAiHint;
    private TextView           tvAgeAiHint, tvStep3Subtitle;
    private TextView           tvAgeAtDisappearance, tvCurrentAge;
    private LinearLayout       layoutCurrentAge, layoutEducationWrapper;
    private ChipGroup          chipGroupGender;
    private Chip               chipMale, chipFemale;
    private MaterialButton     btnIncidentDate;
    private AutoCompleteTextView spEducation;
    private long               selectedIncidentDate = 0;
    private int                manualCurrentAge = -1;
    private String             selectedGender = "male";
    private LinearLayout       layoutPhotos;
    private TextView           tvMatchResult;
    private MaterialButton     btnCamera, btnGallery;
    private MaterialCardView   cardPhotoPlaceholder;

    // ── Step 4: Location ─────────────────────────────────
    private AutoCompleteTextView spGov, spCity, spArea, spMentalState;
    private TextInputEditText    etLandmark, etTransportLine, etMarkersNote, etClothes;
    private LinearLayout         layoutTransport;
    private TextInputLayout      tilFoundName;
    private TextInputEditText    etFoundName;
    private ChipGroup            chipGroupMarkers;
    private TextView             tvGpsStatus;
    private MaterialButton       btnMap, btnFoundDate;
    private double               lat = 0, lng = 0;
    private String               manualAddress = "";
    private long                 selectedFoundDate = 0;

    // ── Step 5: Review ───────────────────────────────────
    // New simple review card views
    private ImageView          ivReviewPhoto;
    private TextView           tvReviewPersonName, tvReviewTagType, tvReviewTagCategory;
    private TextView           tvReviewAge, tvReviewLocation, tvReviewIncidentDate, tvReviewClothes;
    private LinearLayout       layoutReviewPhotos;
    private MaterialCheckBox   cbConsent;
    // Hidden compat views
    private TextView           tvReviewType, tvReviewName, tvReviewGender, tvReviewEducation;
    private TextView           tvReviewMental, tvReviewMarkers, tvReviewFoundDate;
    private LinearLayout       tabContentPerson, tabContentLocation, tabContentContact, layoutChatSwitch;
    private MaterialButton     btnEditPerson, btnEditLocation;
    private TextInputEditText  etPhone, etRelatives, etDescription;
    private SwitchMaterial     switchPhonePublic, switchChat;
    private AutoCompleteTextView spRelation;
    private TabLayout          tabsReview;
    // Shared
    private LinearProgressIndicator progressBar;
    private TextView           tvStatus;

    // ── Success screen ───────────────────────────────────
    private TextView       tvReportId;
    private MaterialButton btnShareReport, btnGoHome, btnTrackReport;
    private String         savedReportId = "";

    // ── State ────────────────────────────────────────────
    private final List<File>   photoFiles   = new ArrayList<>();
    private final List<String> existingImageUrls = new ArrayList<>();
    private final List<Bitmap> photoBitmaps = new ArrayList<>();
    private Uri    cameraUri;
    private java.io.File pendingCameraFile; // [إصلاح] ملف الكاميرا المعلق
    private boolean isUploading = false;
    private AtomicReference<float[]> firstFaceEmbedding = new AtomicReference<>();
    private android.widget.TextView tvPhotoCount; // عداد الصور

    private FusedLocationProviderClient fusedClient;
    private AdsManager adsManager;

    private static final String[] EDUCATION_LEVELS = {
        "حضانة / روضة","ابتدائي","إعدادي","ثانوي","جامعي","خريج / متخرج","غير معروف"
    };
    private static final String[] MENTAL_STATES = {
        "طبيعي","ضعف ذهني","زهايمر","اضطراب نفسي","فقدان ذاكرة","أبكم","غير معروف"
    };
    private static final String[] RELATIONS = {
        "والد","والدة","أخ","أخت","زوج","زوجة","ابن / ابنة","قريب","جار / صديق",
        "فاعل خير","طاقم مستشفى","شرطة / جهة رسمية","أخرى"
    };

    private static final String[] STEP_TITLES = {
        "الخطوة 1 — الصورة ونوع البلاغ",
        "الخطوة 2 — البيانات الشخصية",
        "الخطوة 3 — الموقع والوصف",
        "الخطوة 4 — مراجعة وإرسال"
    };
    private static final String[] STEP_SUBTITLES = {
        "أضف صورة ثم اختر نوع البلاغ",    // [إصلاح] الصورة أولاً ثم النوع
        "الاسم والسن وبقية البيانات",
        "آخر مكان شُوهد فيه",
        "راجع البيانات قبل الإرسال"
    };
    private static final String[] NEXT_LABELS = {
        "التالي — البيانات الشخصية ►",
        "التالي — الموقع ►",
        "التالي — مراجعة ►",
        "التالي ►"
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LanguageHelper.applyLanguage(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_wizard);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        adsManager  = AdsManager.getInstance(this);

        bindViews();
        setupTypeCards();
        setupGenderChips();
        setupDropdowns();
        setupAddressCascade();
        requestLocation();
        updateStep(1);

        // [إصلاح 4.3] استرجاع المسودة إن وجدت (بعد بناء الـ UI)
        new android.os.Handler(android.os.Looper.getMainLooper())
            .postDelayed(this::restoreDraftIfExists, 300);
    }

    // ════════════════════════════════════════════════════
    //  Bind Views
    // ════════════════════════════════════════════════════

    private void bindViews() {
        wizardContainer = findViewById(R.id.wizard_container);
        layoutSuccess   = findViewById(R.id.layout_success);
        scrollReport    = findViewById(R.id.scroll_report);

        tvBtnBack   = findViewById(R.id.tv_btn_back);
        tvBtnCancel = findViewById(R.id.tv_btn_cancel);
        tvStepTitle = findViewById(R.id.tv_step_title);
        tvStepBadge = findViewById(R.id.tv_step_badge);

        stepC1    = findViewById(R.id.step_c1);
        stepC2    = findViewById(R.id.step_c2);
        stepC3    = findViewById(R.id.step_c3);
        stepC4    = findViewById(R.id.step_c4);
        stepLine1 = findViewById(R.id.step_line1);
        stepLine2 = findViewById(R.id.step_line2);
        stepLine3 = findViewById(R.id.step_line3);

        layoutStep1 = findViewById(R.id.layout_step1);
        layoutStep2 = findViewById(R.id.layout_step2);
        layoutStep3 = findViewById(R.id.layout_step3);
        layoutStep4 = findViewById(R.id.layout_step4);

        // Navigation
        btnPrev   = findViewById(R.id.btn_prev);
        btnNext   = findViewById(R.id.btn_next);
        btnSubmit = findViewById(R.id.btn_submit);

        // Step 2
        cardTypeMissing   = findViewById(R.id.card_type_missing);
        cardTypeFound     = findViewById(R.id.card_type_found);
        cardTypeSighting  = findViewById(R.id.card_type_sighting);
        cardTypeEmergency = findViewById(R.id.card_type_emergency);
        cardTypeHomeless  = findViewById(R.id.card_type_homeless);
        icCheckMissing    = findViewById(R.id.ic_check_missing);
        icCheckFound      = findViewById(R.id.ic_check_found);
        icCheckSighting   = findViewById(R.id.ic_check_sighting);
        icCheckEmergency  = findViewById(R.id.ic_check_emergency);
        icCheckHomeless   = findViewById(R.id.ic_check_homeless);
        tvTypeDescription = findViewById(R.id.tv_type_description);

        // Step 3
        etName              = findViewById(R.id.et_name);
        etAge               = findViewById(R.id.et_age);
        etPhysicalCondition = findViewById(R.id.et_physical_condition);
        tilAge              = findViewById(R.id.til_age);
        cardAgeAiHint       = findViewById(R.id.card_age_ai_hint);
        tvAgeAiHint         = findViewById(R.id.tv_age_ai_hint);
        tvStep3Subtitle     = findViewById(R.id.tv_step3_subtitle);
        tvAgeAtDisappearance = findViewById(R.id.tv_age_at_disappearance);
        tvCurrentAge        = findViewById(R.id.tv_current_age);
        layoutCurrentAge    = findViewById(R.id.layout_current_age);
        layoutEducationWrapper = findViewById(R.id.layout_education_wrapper);

        View btnEditAge = findViewById(R.id.btn_edit_current_age);
        if (btnEditAge != null) btnEditAge.setOnClickListener(v -> showEditCurrentAgeDialog());
        if (tvAgeAtDisappearance != null) {
            tvAgeAtDisappearance.setClickable(true);
            tvAgeAtDisappearance.setOnClickListener(v -> showEditAgeAtDisappearanceDialog());
        }

        chipGroupGender = findViewById(R.id.chip_group_gender);
        chipMale        = findViewById(R.id.chip_male);
        chipFemale      = findViewById(R.id.chip_female);
        btnIncidentDate = findViewById(R.id.btn_incident_date);
        spEducation     = findViewById(R.id.sp_education);

        layoutPhotos         = findViewById(R.id.layout_photos);
        tvMatchResult        = findViewById(R.id.tv_match_result);
        btnCamera            = findViewById(R.id.btn_camera);
        btnGallery           = findViewById(R.id.btn_gallery);
        cardPhotoPlaceholder = findViewById(R.id.card_photo_placeholder);
        tvPhotoCount         = findViewById(R.id.tv_photo_count);

        // Step 4
        spGov           = findViewById(R.id.sp_governorate);
        spCity          = findViewById(R.id.sp_city);
        spArea          = findViewById(R.id.sp_area);
        etClothes       = findViewById(R.id.et_clothes);
        tvGpsStatus     = findViewById(R.id.tv_gps_status);
        btnMap          = findViewById(R.id.btn_map);
        chipGroupMarkers= findViewById(R.id.chip_group_markers);
        btnFoundDate    = findViewById(R.id.btn_found_date);
        tilFoundName    = findViewById(R.id.til_found_name);
        etFoundName     = findViewById(R.id.et_found_name);
        etLandmark      = findViewById(R.id.et_landmark);
        layoutTransport = findViewById(R.id.layout_transport);
        etTransportLine = findViewById(R.id.et_transport_line);
        spMentalState   = findViewById(R.id.sp_mental_state);
        etMarkersNote   = findViewById(R.id.et_markers_note);

        // Step 5 - new review card views
        ivReviewPhoto       = findViewById(R.id.iv_review_photo);
        tvReviewPersonName  = findViewById(R.id.tv_review_person_name);
        tvReviewTagType     = findViewById(R.id.tv_review_tag_type);
        tvReviewTagCategory = findViewById(R.id.tv_review_tag_category);
        tvReviewAge         = findViewById(R.id.tv_review_age);
        tvReviewLocation    = findViewById(R.id.tv_review_location);
        tvReviewIncidentDate = findViewById(R.id.tv_review_incident_date);
        tvReviewClothes     = findViewById(R.id.tv_review_clothes);
        layoutReviewPhotos  = findViewById(R.id.layout_review_photos);
        cbConsent           = findViewById(R.id.cb_consent);

        // Hidden compat views
        tvReviewType         = findViewById(R.id.tv_review_type);
        tvReviewName         = findViewById(R.id.tv_review_name);
        tvReviewGender       = findViewById(R.id.tv_review_gender);
        tvReviewEducation    = findViewById(R.id.tv_review_education);
        tvReviewMental       = findViewById(R.id.tv_review_mental);
        tvReviewMarkers      = findViewById(R.id.tv_review_markers);
        tvReviewFoundDate    = findViewById(R.id.tv_review_found_date);
        tabContentPerson     = findViewById(R.id.tab_content_person);
        tabContentLocation   = findViewById(R.id.tab_content_location);
        tabContentContact    = findViewById(R.id.tab_content_contact);
        layoutChatSwitch     = findViewById(R.id.layout_chat_switch);
        btnEditPerson        = findViewById(R.id.btn_edit_person);
        btnEditLocation      = findViewById(R.id.btn_edit_location);
        etPhone              = findViewById(R.id.et_phone);
        etRelatives          = findViewById(R.id.et_relatives);
        etDescription        = findViewById(R.id.et_description);
        switchPhonePublic    = findViewById(R.id.switch_phone_public);
        switchChat           = findViewById(R.id.switch_chat);
        spRelation           = findViewById(R.id.sp_relation);
        tabsReview           = findViewById(R.id.tabs_review);
        progressBar          = findViewById(R.id.progress_bar);
        tvStatus             = findViewById(R.id.tv_status);

        // Success screen
        tvReportId     = findViewById(R.id.tv_report_id);
        btnShareReport = findViewById(R.id.btn_share_report);
        btnGoHome      = findViewById(R.id.btn_go_home);
        btnTrackReport = findViewById(R.id.btn_track_report);

        // Events
        btnPrev.setOnClickListener(v   -> goToPrevStep());
        btnNext.setOnClickListener(v   -> goToNextStep());
        btnSubmit.setOnClickListener(v -> submit());

        if (tvBtnBack != null)
            tvBtnBack.setOnClickListener(v -> { if (currentStep > 1) goToPrevStep(); else finish(); });
        if (tvBtnCancel != null)
            tvBtnCancel.setOnClickListener(v -> showCancelDialog());

        btnCamera.setOnClickListener(v  -> openCamera());
        btnGallery.setOnClickListener(v -> openGallery());
        if (cardPhotoPlaceholder != null)
            cardPhotoPlaceholder.setOnClickListener(v -> openCamera());

        btnMap.setOnClickListener(v      -> openMapPicker());
        if (btnFoundDate != null)
            btnFoundDate.setOnClickListener(v -> showDatePicker(false));

        selectedIncidentDate = System.currentTimeMillis();
        updateIncidentDateButton();

        if (etAge != null) etAge.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            @Override public void onTextChanged(CharSequence s,int a,int b,int c){}
            @Override public void afterTextChanged(android.text.Editable s) {
                recalcCurrentAge();
                selectedAgeCategory = deriveAgeCategory(s.toString());
            }
        });
        if (btnIncidentDate != null)
            btnIncidentDate.setOnClickListener(v -> showDatePicker(true));

        if (btnEditPerson  != null) btnEditPerson.setOnClickListener(v  -> updateStep(2));
        if (btnEditLocation!= null) btnEditLocation.setOnClickListener(v-> updateStep(3));
        if (btnGoHome      != null) btnGoHome.setOnClickListener(v      -> finish());
        if (btnShareReport != null) btnShareReport.setOnClickListener(v -> shareReport());
        if (btnTrackReport != null) btnTrackReport.setOnClickListener(v -> {
            // [Phase 4.2] توجيه المستخدم لبلاغاته مباشرة
            Intent intent = new Intent(this,
                com.missingpersons.app.activities.MyReportsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }

    // ════════════════════════════════════════════════════
    //  Step Navigation
    // ════════════════════════════════════════════════════

    private void updateStep(int step) {
        currentStep = step;

        if (layoutStep1 != null) layoutStep1.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        if (layoutStep2 != null) layoutStep2.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
        if (layoutStep3 != null) layoutStep3.setVisibility(step == 3 ? View.VISIBLE : View.GONE);
        if (layoutStep4 != null) layoutStep4.setVisibility(step == 4 ? View.VISIBLE : View.GONE);

        if (tvStepTitle != null) tvStepTitle.setText(STEP_TITLES[step - 1]);
        if (tvStepBadge != null) tvStepBadge.setText(STEP_SUBTITLES[step - 1]);

        btnPrev.setVisibility(step > 1 ? View.VISIBLE : View.GONE);
        btnNext.setVisibility(step < 4 ? View.VISIBLE : View.GONE);
        btnSubmit.setVisibility(step == 4 ? View.VISIBLE : View.GONE);

        // Update next button text per step
        if (step < 4 && btnNext != null)
            btnNext.setText(NEXT_LABELS[step - 1]);

        updateStepIndicator(step);
        scrollReport.post(() -> scrollReport.smoothScrollTo(0, 0));

        if (step == 3 && lat == 0 && lng == 0) requestLocation();
        if (step == 4) populateReview();
    }

    private void updateStepIndicator(int step) {
        TextView[] circles = { stepC1, stepC2, stepC3, stepC4 };
        View[]     lines   = { stepLine1, stepLine2, stepLine3 };
        int colorDone    = 0xFF2E7D32;
        int colorPending = ContextCompat.getColor(this, R.color.md_primary_container);

        for (int i = 0; i < circles.length; i++) {
            if (circles[i] == null) continue;
            int s = i + 1;
            if (s < step) {
                circles[i].setBackground(ContextCompat.getDrawable(this, R.drawable.bg_step_done));
                circles[i].setTextColor(Color.WHITE);
                circles[i].setText("✓");
            } else if (s == step) {
                circles[i].setBackground(ContextCompat.getDrawable(this, R.drawable.bg_step_active));
                circles[i].setTextColor(Color.WHITE);
                circles[i].setText(String.valueOf(s));
            } else {
                circles[i].setBackground(ContextCompat.getDrawable(this, R.drawable.bg_step_pending));
                circles[i].setTextColor(getColorFromAttr(com.google.android.material.R.attr.colorPrimary));
                circles[i].setText(String.valueOf(s));
            }
        }
        for (int i = 0; i < lines.length; i++) {
            if (lines[i] == null) continue;
            lines[i].setBackgroundColor((i + 1) < step ? colorDone : colorPending);
        }
    }

    private void goToNextStep() {
        if (!validateCurrentStep()) return;
        if (currentStep < 4) updateStep(currentStep + 1);
    }

    private void goToPrevStep() {
        if (currentStep > 1) updateStep(currentStep - 1);
    }

    // ════════════════════════════════════════════════════
    //  Validation
    // ════════════════════════════════════════════════════

    private boolean validateCurrentStep() {
        switch (currentStep) {
            case 1:
                // [Phase 5] الصورة الآن في Step1 — تحقق هنا
                if (photoFiles.isEmpty()) {
                    showError("⚠️ أضف صورة واضحة أولاً قبل المتابعة");
                    android.view.View photoArea = cardPhotoPlaceholder != null
                        ? cardPhotoPlaceholder : (layoutPhotos != null ? layoutPhotos : null);
                    if (photoArea != null) {
                        photoArea.animate().translationX(-14f).setDuration(70).withEndAction(() ->
                        photoArea.animate().translationX(14f).setDuration(70).withEndAction(() ->
                        photoArea.animate().translationX(-10f).setDuration(60).withEndAction(() ->
                        photoArea.animate().translationX(10f).setDuration(60).withEndAction(() ->
                        photoArea.animate().translationX(0f).setDuration(50).start()
                        ).start()).start()).start()).start();
                    }
                    return false;
                }
                return true; // type always selected
            case 2:
                if (!TYPE_HOMELESS.equals(currentType)) {
                    String age = etAge.getText() != null ? etAge.getText().toString().trim() : "";
                    if (age.isEmpty()) { showError("أدخل السن التقريبي للشخص"); etAge.requestFocus(); return false; }
                }
                return true;
            case 3:
                if (spGov.getText().toString().trim().isEmpty()) {
                    showError("اختر المحافظة أولاً"); return false;
                }
                return true;
            case 4:
                // تأكيد أخير قبل الإرسال
                if (photoFiles.isEmpty()) {
                    showError("⚠️ لا يمكن الإرسال بدون صورة — ارجع وأضف صورة"); return false;
                }
                if (cbConsent != null && !cbConsent.isChecked()) {
                    showError("يجب الموافقة على الإقرار قبل الإرسال"); return false;
                }
                return true;
        }
        return true;
    }

    private void showError(String msg) {
        Snackbar.make(scrollReport, "⚠️ " + msg, Snackbar.LENGTH_SHORT).show();
    }
    //  Type Cards
    // ════════════════════════════════════════════════════

    private void setupTypeCards() {
        if (cardTypeMissing == null) return;
        cardTypeMissing.setOnClickListener(v   -> setType(TYPE_MISSING));
        cardTypeFound.setOnClickListener(v     -> setType(TYPE_FOUND));
        cardTypeSighting.setOnClickListener(v  -> setType(TYPE_SIGHTING));
        cardTypeEmergency.setOnClickListener(v -> setType(TYPE_EMERGENCY));
        if (cardTypeHomeless != null)
            cardTypeHomeless.setOnClickListener(v -> setType(TYPE_HOMELESS));
        setType(TYPE_MISSING);

        editReportId   = getIntent().getStringExtra("editReportId");
        editReportNode = getIntent().getStringExtra("editReportNode");
        if (editReportNode == null) editReportNode = "reports";
        String presetType = getIntent().getStringExtra("reportType");
        if (presetType != null) setType(presetType);
        if (editReportId != null) loadExistingReport();
    }

    private void setType(String type) {
        currentType = type;
        Map<String,String> desc = new HashMap<>();
        desc.put(TYPE_MISSING,   "شخص غاب عن منزله — ستركّز الاستمارة على تفاصيل خروجه الأخير وخط سيره.");
        desc.put(TYPE_FOUND,     "شخص تائه موجود معك الآن — ستركّز على مكان العثور وطريقة التواصل مع ذويه.");
        desc.put(TYPE_SIGHTING,  "رأيت شخصاً يشبه مفقوداً — ستركّز على مكان وزمان الرؤية.");
        desc.put(TYPE_EMERGENCY, "شخص في مستشفى أو موقع حادث — ستركّز على الحالة الطبية.");
        desc.put(TYPE_HOMELESS,  "شخص مشرد في موقع ثابت — ستسألك عن تاريخي أول مشاهدة وإيجاد الهوية.");

        if (tvTypeDescription != null) tvTypeDescription.setText(desc.get(type));

        updateTypeCardVisual(cardTypeMissing,   icCheckMissing,   type.equals(TYPE_MISSING));
        updateTypeCardVisual(cardTypeFound,     icCheckFound,     type.equals(TYPE_FOUND));
        updateTypeCardVisual(cardTypeSighting,  icCheckSighting,  type.equals(TYPE_SIGHTING));
        updateTypeCardVisual(cardTypeEmergency, icCheckEmergency, type.equals(TYPE_EMERGENCY));
        updateTypeCardVisual(cardTypeHomeless,  icCheckHomeless,  type.equals(TYPE_HOMELESS));

        applyStep3Smart(type);
        applyStep4Smart(type);
    }

    private void updateTypeCardVisual(MaterialCardView card, TextView check, boolean sel) {
        if (card == null) return;
        if (sel) {
            card.setCardBackgroundColor(getColorFromAttr(com.google.android.material.R.attr.colorPrimaryContainer));
            card.setStrokeWidth(dpToPx(2));
            card.setStrokeColor(getColorFromAttr(com.google.android.material.R.attr.colorPrimary));
        } else {
            card.setCardBackgroundColor(getColorFromAttr(com.google.android.material.R.attr.colorSurface));
            card.setStrokeWidth(dpToPx(1));
            card.setStrokeColor(getColorFromAttr(com.google.android.material.R.attr.colorOutline));
        }
        if (check != null) check.setVisibility(sel ? View.VISIBLE : View.GONE);
    }

    private void applyStep3Smart(String type) {
        if (tvStep3Subtitle != null) {
            switch (type) {
                case TYPE_MISSING:   tvStep3Subtitle.setText("البيانات الأساسية — السن والنوع إلزاميان للبحث الدقيق"); break;
                case TYPE_FOUND:     tvStep3Subtitle.setText("بيانات الشخص التائه — السن والنوع يساعدان في إيجاد ذويه"); break;
                case TYPE_SIGHTING:  tvStep3Subtitle.setText("بيانات من رأيته — كن دقيقاً قدر الإمكان"); break;
                case TYPE_EMERGENCY: tvStep3Subtitle.setText("بيانات مريض الطوارئ — أي معلومة قد تحدد هويته"); break;
                case TYPE_HOMELESS:  tvStep3Subtitle.setText("بيانات المشرد — السن تقريبي ومشتق من الصورة"); break;
            }
        }
        if (tilAge != null) {
            tilAge.setVisibility(View.VISIBLE);
            switch (type) {
                case TYPE_MISSING:
                    tilAge.setHint("السن وقت الاختفاء *");
                    break;
                case TYPE_FOUND:
                    tilAge.setHint("السن التقريبي للشخص التائه");
                    break;
                case TYPE_SIGHTING:
                    tilAge.setHint("التقدير العمري للشخص المرئي");
                    break;
                case TYPE_EMERGENCY:
                    tilAge.setHint("العمر التقريبي (مشتق من الصورة)");
                    break;
                case TYPE_HOMELESS:
                    tilAge.setHint("العمر التقريبي (اختياري)");
                    break;
                default:
                    tilAge.setHint("السن *");
            }
        }
        if (btnIncidentDate != null)
            btnIncidentDate.setVisibility(TYPE_FOUND.equals(type) ? View.GONE : View.VISIBLE);
        updateIncidentDateButton();
        if (layoutCurrentAge != null)
            layoutCurrentAge.setVisibility(TYPE_MISSING.equals(type) ? View.VISIBLE : View.GONE);
        recalcCurrentAge();
        if (layoutEducationWrapper != null) {
            boolean showEdu = !TYPE_EMERGENCY.equals(type) && !TYPE_HOMELESS.equals(type);
            layoutEducationWrapper.setVisibility(showEdu ? View.VISIBLE : View.GONE);
        }
    }

    private void applyStep4Smart(String type) {
        if (tilFoundName != null)
            tilFoundName.setVisibility(TYPE_FOUND.equals(type) ? View.VISIBLE : View.GONE);
        if (layoutTransport != null)
            layoutTransport.setVisibility(TYPE_MISSING.equals(type) ? View.VISIBLE : View.GONE);
        if (btnFoundDate != null) {
            boolean show = TYPE_FOUND.equals(type) || TYPE_EMERGENCY.equals(type) || TYPE_HOMELESS.equals(type);
            btnFoundDate.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) {
                switch (type) {
                    case TYPE_FOUND:     btnFoundDate.setText("📅 تاريخ العثور"); break;
                    case TYPE_EMERGENCY: btnFoundDate.setText("📅 تاريخ الخروج من المستشفى"); break;
                    case TYPE_HOMELESS:  btnFoundDate.setText("📅 تاريخ التعرف على هويته"); break;
                }
            }
        }
    }

    // ════════════════════════════════════════════════════
    //  Gender Chips
    // ════════════════════════════════════════════════════

    private void setupGenderChips() {
        if (chipGroupGender == null) return;
        chipGroupGender.setOnCheckedStateChangeListener((group, checked) -> {
            if (checked.isEmpty()) return;
            selectedGender = (checked.get(0) == R.id.chip_female) ? "female" : "male";
        });
    }

    // ════════════════════════════════════════════════════
    //  Dropdowns
    // ════════════════════════════════════════════════════

    private void setupDropdowns() {
        if (spEducation != null) {
            spEducation.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, EDUCATION_LEVELS));
            spEducation.setOnClickListener(v -> spEducation.showDropDown());
        }
        if (spMentalState != null) {
            spMentalState.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, MENTAL_STATES));
            spMentalState.setOnClickListener(v -> spMentalState.showDropDown());
        }
        if (spRelation != null) {
            spRelation.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, RELATIONS));
            spRelation.setOnClickListener(v -> spRelation.showDropDown());
        }
    }

    // ════════════════════════════════════════════════════
    //  Address Cascade
    // ════════════════════════════════════════════════════

    private void setupAddressCascade() {
        if (spGov == null) return;
        spGov.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line,
            EgyptAddressHelper.getGovernorates()));
        spGov.setThreshold(1);
        // [Phase 5] تعبئة مسبقة من تفضيل المستخدم
        String savedGov = GovernorateManager.getPrimaryGov(this);
        if (!savedGov.isEmpty() && spGov.getText().toString().trim().isEmpty()) {
            spGov.setText(savedGov, false);
        }
        spGov.setOnItemClickListener((p, v, pos, id) -> {
            String gov = spGov.getText().toString();
            spCity.setText("", false);
            if (spArea != null) spArea.setText("", false);
            spCity.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line,
                EgyptAddressHelper.getCities(gov)));
        });
        if (spCity != null) spCity.setOnItemClickListener((p, v, pos, id) -> {
            String gov  = spGov.getText().toString();
            String city = spCity.getText().toString();
            if (spArea != null) {
                spArea.setText("", false);
                spArea.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line,
                    EgyptAddressHelper.getDistricts(gov, city)));
            }
        });
    }

    // ════════════════════════════════════════════════════
    //  Populate Review (Step 5)
    // ════════════════════════════════════════════════════

    private void populateReview() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMMM yyyy", new Locale("ar"));

        // Photo — show first photo if available
        if (ivReviewPhoto != null && !photoBitmaps.isEmpty()) {
            ivReviewPhoto.setImageBitmap(photoBitmaps.get(0));
        }

        // Name
        String name = getText(etName);
        if (tvReviewPersonName != null)
            tvReviewPersonName.setText(name.isEmpty() ? "غير محدد" : name);

        // Type tag
        if (tvReviewTagType != null) {
            switch (currentType) {
                case TYPE_FOUND:     tvReviewTagType.setText("✋ معثور عليه"); break;
                case TYPE_SIGHTING:  tvReviewTagType.setText("👁️ رؤية");       break;
                case TYPE_EMERGENCY: tvReviewTagType.setText("🚨 طوارئ");       break;
                case TYPE_HOMELESS:  tvReviewTagType.setText("🏠 مشرد");        break;
                default:             tvReviewTagType.setText("🔍 مفقود");
            }
        }

        // Category tag
        if (tvReviewTagCategory != null) {
            switch (selectedAgeCategory) {
                case AGE_CAT_ADULT: tvReviewTagCategory.setText("بالغ");  break;
                case AGE_CAT_ELDER: tvReviewTagCategory.setText("مسن");   break;
                default:            tvReviewTagCategory.setText("طفل");
            }
        }

        // Age row
        String age = etAge.getText() != null ? etAge.getText().toString().trim() : "";
        if (tvReviewAge != null)
            tvReviewAge.setText((age.isEmpty() ? "غير محدد" : age + " سنة") + " · " +
                ("female".equals(selectedGender) ? "أنثى" : "ذكر"));

        // Location row
        String gov  = spGov != null  ? spGov.getText().toString().trim()  : "";
        String city = spCity != null ? spCity.getText().toString().trim() : "";
        String area = spArea != null ? spArea.getText().toString().trim() : "";
        StringBuilder loc = new StringBuilder();
        if (!area.isEmpty()) loc.append(area);
        if (!city.isEmpty()) { if (loc.length() > 0) loc.append("، "); loc.append(city); }
        if (!gov.isEmpty())  { if (loc.length() > 0) loc.append("، "); loc.append(gov); }
        if (tvReviewLocation != null)
            tvReviewLocation.setText(loc.length() > 0 ? loc.toString() : "غير محدد");

        // Date row
        if (tvReviewIncidentDate != null) {
            String label;
            switch (currentType) {
                case TYPE_FOUND:     label = "تاريخ العثور:  "; break;
                case TYPE_SIGHTING:  label = "تاريخ الرؤية:  "; break;
                case TYPE_EMERGENCY: label = "تاريخ الحادث:  "; break;
                case TYPE_HOMELESS:  label = "أول مشاهدة:  ";   break;
                default:             label = "تاريخ الاختفاء:  ";
            }
            tvReviewIncidentDate.setText(label +
                (selectedIncidentDate > 0 ? sdf.format(new java.util.Date(selectedIncidentDate)) : "غير محدد"));
        }

        // Description row (clothes)
        String clothes = getText(etClothes);
        String markers = getMarkersText();
        String desc = "";
        if (!markers.isEmpty()) desc = markers;
        if (!clothes.isEmpty()) desc = (desc.isEmpty() ? "" : desc + " · ") + clothes;
        if (tvReviewClothes != null)
            tvReviewClothes.setText(desc.isEmpty() ? "غير محدد" : desc);

        // Photos row
        if (layoutReviewPhotos != null) {
            layoutReviewPhotos.removeAllViews();
            for (int i = 0; i < Math.min(photoBitmaps.size(), 3); i++) {
                ImageView iv = new ImageView(this);
                int size = dpToPx(38);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                lp.setMarginEnd(dpToPx(6));
                iv.setLayoutParams(lp);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setImageBitmap(photoBitmaps.get(i));
                // round corners via clip
                iv.setClipToOutline(true);
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setCornerRadius(dpToPx(8));
                bg.setColor(0xFFE8F0FE);
                iv.setBackground(bg);
                layoutReviewPhotos.addView(iv);
            }
            if (photoBitmaps.isEmpty()) {
                TextView placeholder = new TextView(this);
                placeholder.setText("📷");
                placeholder.setTextSize(20);
                layoutReviewPhotos.addView(placeholder);
            }
        }
    }

    private String getMarkersText() {
        if (chipGroupMarkers == null) return "";
        List<String> sel = new ArrayList<>();
        for (int i = 0; i < chipGroupMarkers.getChildCount(); i++) {
            View child = chipGroupMarkers.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).isChecked())
                sel.add(((Chip) child).getText().toString());
        }
        return sel.isEmpty() ? "" : String.join(" · ", sel);
    }

    // ════════════════════════════════════════════════════
    //  Edit Mode
    // ════════════════════════════════════════════════════

    private void loadExistingReport() {
        FirebaseDatabase.getInstance().getReference(editReportNode).child(editReportId)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    if (!snap.exists()) return;
                    String type = snap.child("reportType").getValue(String.class);
                    if (type != null) setType(type);
                    String ageCat = snap.child("ageCategory").getValue(String.class);
                    if (ageCat != null) selectedAgeCategory = ageCat; // restored from DB
                    String n = snap.child("personName").getValue(String.class);
                    if (etName != null && n != null && !"مجهول".equals(n)) etName.setText(n);
                    String a = snap.child("personAge").getValue(String.class);
                    if (etAge != null && a != null) etAge.setText(a);
                    String g = snap.child("gender").getValue(String.class);
                    if ("female".equals(g) && chipFemale != null) chipFemale.setChecked(true);
                    String g2 = snap.child("governorate").getValue(String.class);
                    if (spGov != null && g2 != null) spGov.setText(g2, false);
                    Long d = snap.child("incidentDate").getValue(Long.class);
                    if (d != null) { selectedIncidentDate = d; updateIncidentDateButton(); recalcCurrentAge(); }
                    Object urls = snap.child("imageUrls").getValue();
                    if (urls instanceof List) for (Object u : (List<?>) urls)
                        if (u instanceof String && !existingImageUrls.contains((String) u))
                            existingImageUrls.add((String) u);
                    updateStep(2);
                    Toast.makeText(ReportActivity.this, "📝 تعديل البلاغ — راجع البيانات وأرسل",
                        Toast.LENGTH_SHORT).show();
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
    }

    // ════════════════════════════════════════════════════
    //  AI Age Hint
    // ════════════════════════════════════════════════════

    public void onAgeEstimated(int est) {
        runOnUiThread(() -> {
            if (cardAgeAiHint == null || tvAgeAiHint == null || etAge == null) return;
            tvAgeAiHint.setText("🤖 التقدير من الصورة: ~" + est + " سنة");
            cardAgeAiHint.setVisibility(View.VISIBLE);
            if (etAge.getText() != null && etAge.getText().toString().trim().isEmpty())
                etAge.setText(String.valueOf(est));
        });
    }

    /**
     * [Phase 5] اقتراح الجنس من الوجه مع إمكانية التعديل
     * maleConf + femaleConf يجب أن يساويا 1.0
     */
    public void onGenderEstimated(float maleConf, float femaleConf) {
        runOnUiThread(() -> {
            if (chipMale == null || chipFemale == null) return;
            // اقترح فقط إذا كانت الثقة > 60%
            if (maleConf > 0.60f) {
                chipMale.setChecked(true);
                chipFemale.setChecked(false);
                selectedGender = "male";
                // عرض نسبة الثقة في الـ hint
                if (tvAgeAiHint != null && cardAgeAiHint != null) {
                    String current = tvAgeAiHint.getText().toString();
                    int pct = Math.round(maleConf * 100);
                    if (!current.contains("جنس"))
                        tvAgeAiHint.setText(current + "  |  جنس مقترح: ذكر (" + pct + "%) — يمكن التعديل");
                    cardAgeAiHint.setVisibility(View.VISIBLE);
                }
            } else if (femaleConf > 0.60f) {
                chipFemale.setChecked(true);
                chipMale.setChecked(false);
                selectedGender = "female";
                if (tvAgeAiHint != null && cardAgeAiHint != null) {
                    String current = tvAgeAiHint.getText().toString();
                    int pct = Math.round(femaleConf * 100);
                    if (!current.contains("جنس"))
                        tvAgeAiHint.setText(current + "  |  جنس مقترح: أنثى (" + pct + "%) — يمكن التعديل");
                    cardAgeAiHint.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    // ════════════════════════════════════════════════════
    //  Date Picker
    // ════════════════════════════════════════════════════

    private void showDatePicker(boolean isIncident) {
        Calendar cal = Calendar.getInstance();
        if (isIncident && selectedIncidentDate > 0) cal.setTimeInMillis(selectedIncidentDate);
        new android.app.DatePickerDialog(this, (v, y, m, d) -> {
            Calendar c = Calendar.getInstance();
            c.set(y, m, d, 0, 0, 0);
            long ts = c.getTimeInMillis();
            if (isIncident) {
                selectedIncidentDate = ts;
                updateIncidentDateButton();
                recalcCurrentAge();
            } else {
                selectedFoundDate = ts;
                String fmt = new SimpleDateFormat("dd/MM/yyyy", new Locale("ar")).format(new java.util.Date(ts));
                if (btnFoundDate != null) btnFoundDate.setText("📅 " + fmt);
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        {{ getDatePicker().setMaxDate(System.currentTimeMillis()); }}.show();
    }

    private void updateIncidentDateButton() {
        if (btnIncidentDate == null) return;
        String label;
        switch (currentType) {
            case TYPE_SIGHTING:  label = "📅 تاريخ الرؤية: "; break;
            case TYPE_EMERGENCY: label = "📅 تاريخ الحادث: "; break;
            case TYPE_HOMELESS:  label = "📅 أول مشاهدة: ";   break;
            default:             label = "📅 يوم الاختفاء: ";
        }
        String fmt = new SimpleDateFormat("dd/MM/yyyy", new Locale("ar"))
            .format(new java.util.Date(selectedIncidentDate));
        btnIncidentDate.setText(label + fmt);
    }

    private void recalcCurrentAge() {
        if (layoutCurrentAge == null || !TYPE_MISSING.equals(currentType)) {
            if (layoutCurrentAge != null) layoutCurrentAge.setVisibility(View.GONE);
            return;
        }
        String s = etAge != null && etAge.getText() != null ? etAge.getText().toString().trim() : "";
        if (s.isEmpty()) { layoutCurrentAge.setVisibility(View.GONE); return; }
        try {
            int atDisapp = Integer.parseInt(s);
            int diffYears = (int)((System.currentTimeMillis() - selectedIncidentDate) / (1000L*60*60*24*365));
            int cur = manualCurrentAge >= 0 ? manualCurrentAge : atDisapp + diffYears;
            layoutCurrentAge.setVisibility(View.VISIBLE);
            if (tvAgeAtDisappearance != null) tvAgeAtDisappearance.setText(atDisapp + " عام");
            if (tvCurrentAge != null)
                tvCurrentAge.setText(cur + " عام" + (diffYears > 0 ? " (+" + diffYears + ")" : "") + " ✏️");
        } catch (Exception e) { layoutCurrentAge.setVisibility(View.GONE); }
    }

    private void showEditAgeAtDisappearanceDialog() {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setText(etAge != null && etAge.getText() != null ? etAge.getText().toString() : "");
        et.setPadding(40,20,40,20);
        new AlertDialog.Builder(this).setTitle("✏️ تعديل العمر وقت الاختفاء").setView(et)
            .setPositiveButton("حفظ", (d,w) -> {
                String v = et.getText().toString().trim();
                if (!v.isEmpty()) { if (etAge != null) etAge.setText(v); recalcCurrentAge(); }
            }).setNegativeButton("إلغاء", null).show();
    }

    private void showEditCurrentAgeDialog() {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setText(tvCurrentAge != null ? tvCurrentAge.getText().toString().replaceAll("[^0-9]","") : "");
        et.setPadding(40,20,40,20);
        new AlertDialog.Builder(this).setTitle("✏️ تعديل السن الحالي").setView(et)
            .setPositiveButton("حفظ", (d,w) -> {
                String v = et.getText().toString().trim();
                if (!v.isEmpty()) { manualCurrentAge = Integer.parseInt(v); recalcCurrentAge(); }
            }).setNegativeButton("إعادة الحساب", (d,w) -> { manualCurrentAge=-1; recalcCurrentAge(); }).show();
    }

    // ════════════════════════════════════════════════════
    //  Cancel Dialog
    // ════════════════════════════════════════════════════

    private void showCancelDialog() {
        new AlertDialog.Builder(this).setTitle("إلغاء البلاغ")
            .setMessage("هل تريد الإلغاء؟ ستُفقد البيانات المدخلة.")
            .setPositiveButton("نعم، إلغاء", (d,w) -> finish())
            .setNegativeButton("متابعة", null).show();
    }

    // ════════════════════════════════════════════════════
    //  Camera / Gallery
    // ════════════════════════════════════════════════════

    private void openCamera() {
        if (photoFiles.size() >= MAX_PHOTOS) { showError("وصلت للحد الأقصى (" + MAX_PHOTOS + " صور)"); return; }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_PERM);
            return;
        }
        try {
            File f = createTempImageFile();
            cameraUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", f);
            // [إصلاح] لا نضيف الملف هنا — نضيفه فقط عند نجاح الالتقاط
            pendingCameraFile = f;
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            startActivityForResult(i, REQ_CAMERA);
        } catch (IOException e) { showError("تعذّر فتح الكاميرا"); }
    }

    private void openGallery() {
        if (photoFiles.size() >= MAX_PHOTOS) { showError("وصلت للحد الأقصى (" + MAX_PHOTOS + " صور)"); return; }
        startActivityForResult(new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI), REQ_GALLERY);
    }

    @Override
    protected void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK) {
            // [إصلاح] تنظيف ملف الكاميرا المعلق إذا ألغى المستخدم
            if (pendingCameraFile != null) {
                pendingCameraFile.delete();
                pendingCameraFile = null;
            }
            return;
        }
        Uri uri = null;
        if (req == REQ_CAMERA) {
            uri = cameraUri;
            // [إصلاح] أضف الملف للقائمة فقط عند نجاح الالتقاط
            if (pendingCameraFile != null) {
                photoFiles.add(pendingCameraFile);
                pendingCameraFile = null;
            }
        } else if (req == REQ_GALLERY && data != null) {
            uri = data.getData();
            try {
                File dest = createTempImageFile();
                copyUriToFile(uri, dest);
                photoFiles.add(dest);
                uri = Uri.fromFile(dest);
            } catch (IOException e) { showError("خطأ في فتح الصورة"); return; }
        } else if (req == REQ_MAP && data != null) {
            lat = data.getDoubleExtra("lat", 0);
            lng = data.getDoubleExtra("lng", 0);
            String addr = data.getStringExtra("address");
            if (addr != null) manualAddress = addr;
            if (tvGpsStatus != null) {
                tvGpsStatus.setText("📍 " + (manualAddress.isEmpty() ? lat + ", " + lng : manualAddress));
                tvGpsStatus.setVisibility(View.VISIBLE);
            }
            return;
        }
        if (uri != null) processImage(uri);
    }

    private void processImage(Uri uri) {
        try {
            Bitmap bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            // ── ExifInterface rotation fix ──────────────────────────────
            bmp = fixExifRotation(bmp, uri);
            // ───────────────────────────────────────────────────────────
            bmp = scaleBitmap(bmp, MAX_IMAGE_DIM);
            final Bitmap fb = bmp;
            if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
            FaceDetector det = FaceDetection.getClient(
                new FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setMinFaceSize(0.10f).build());
            det.process(InputImage.fromBitmap(fb, 0))
                .addOnSuccessListener(faces -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    if (faces.isEmpty()) { acceptPhoto(fb, null); return; }
                    if (faces.size() == 1) { acceptPhoto(fb, cropFace(fb, faces.get(0))); }
                    else { showFacePickerDialog(fb, faces); }
                })
                .addOnFailureListener(e -> { if (progressBar != null) progressBar.setVisibility(View.GONE); acceptPhoto(fb, null); });
        } catch (IOException e) { showError("تعذّر معالجة الصورة"); }
    }

    private void showFacePickerDialog(Bitmap full, List<Face> faces) {
        List<Bitmap> crops = new ArrayList<>();
        for (Face f : faces) crops.add(cropFace(full, f));
        android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(this);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(16,16,16,16);
        int dp8 = dpToPx(8), size = dpToPx(90);
        int[] sel = {-1};
        List<View> borders = new ArrayList<>();
        for (int i = 0; i < crops.size(); i++) {
            final int idx = i;
            android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(dp8,0,dp8,0);
            frame.setLayoutParams(lp);
            ImageView iv = new ImageView(this);
            iv.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(crops.get(i));
            frame.addView(iv);
            borders.add(frame);
            frame.setOnClickListener(v -> {
                for (View b : borders) b.setAlpha(0.5f);
                frame.setAlpha(1.0f);
                sel[0] = idx;
            });
            frame.setAlpha(0.5f);
            row.addView(frame);
        }
        hsv.addView(row);
        new AlertDialog.Builder(this).setTitle("اختر الشخص المقصود")
            .setView(hsv)
            .setPositiveButton("تأكيد", (d,w) -> {
                if (sel[0] < 0) { showError("يرجى اختيار شخص"); return; }
                acceptPhoto(full, crops.get(sel[0]));
            })
            .setNegativeButton("إلغاء", (d,w) -> { if (!photoFiles.isEmpty()) photoFiles.remove(photoFiles.size()-1); })
            .setCancelable(false).show();
    }

    private void acceptPhoto(Bitmap full, Bitmap face) {
        Bitmap toStore = face != null ? face : full;
        photoBitmaps.add(toStore);
        addPhotoThumb(toStore);

        // [Phase 5] مراجعة الصورة تلقائياً في الخلفية
        final Bitmap moderationBmp = toStore;
        new Thread(() -> {
            ImageModerationService.ModerationReport report =
                ImageModerationService.moderate(ReportActivity.this, moderationBmp);
            android.util.Log.d("ReportActivity", "Moderation: " + report);
            runOnUiThread(() -> {
                if (tvAgeAiHint != null && cardAgeAiHint != null) {
                    // [تعديل] لا رفض تلقائي — الأسوأ هو المراجعة البشرية
                        switch (report.result) {
                        case APPROVED:
                            // ✅ آمن — لا تعليق
                            break;
                        case PENDING:
                            // ⚠️ سيُراجع إدارياً قبل النشر
                            tvAgeAiHint.setText("⚠️ ستُراجَع الصورة قبل النشر\n" + report.reason);
                            cardAgeAiHint.setVisibility(View.VISIBLE);
                            break;
                        case REJECTED:
                            // لن يُوصَل إليه بعد الآن — معالجة كـ PENDING احتياطياً
                            tvAgeAiHint.setText("⚠️ ستُراجَع الصورة قبل النشر");
                            cardAgeAiHint.setVisibility(View.VISIBLE);
                            break;
                    }
                }
            });
        }).start();

        if (face != null && !photoFiles.isEmpty()) {
            try {
                File f = createTempImageFile();
                try (FileOutputStream fos = new FileOutputStream(f)) { face.compress(Bitmap.CompressFormat.JPEG, 90, fos); }
                photoFiles.set(photoFiles.size()-1, f);
            } catch (IOException ignored) {}
        }
        if (cardPhotoPlaceholder != null) cardPhotoPlaceholder.setVisibility(View.GONE);
        if (photoBitmaps.size() == 1) runFaceAnalysis(toStore);
    }

    private Bitmap cropFace(Bitmap src, Face f) {
        android.graphics.Rect box = f.getBoundingBox();
        int m = (int)(box.width() * 0.20f);
        int l = Math.max(0, box.left-m), t = Math.max(0, box.top-m);
        int r = Math.min(src.getWidth(), box.right+m), b = Math.min(src.getHeight(), box.bottom+m);
        if (r<=l || b<=t) return src;
        return Bitmap.createBitmap(src, l, t, r-l, b-t);
    }

    private void addPhotoThumb(Bitmap bmp) {
        // [إصلاح] تحديث عداد الصور
        if (tvPhotoCount != null)
            tvPhotoCount.setText(photoBitmaps.size() + "/" + MAX_PHOTOS);
        if (cardPhotoPlaceholder != null && photoBitmaps.size() > 0)
            cardPhotoPlaceholder.setVisibility(View.GONE);
        ImageView iv = new ImageView(this);
        int size = dpToPx(80);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMarginEnd(dpToPx(8));
        iv.setLayoutParams(lp);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setClipToOutline(true);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dpToPx(12));
        bg.setColor(0xFFE8F0FE);
        iv.setBackground(bg);
        iv.setImageBitmap(bmp);
        if (layoutPhotos != null) layoutPhotos.addView(iv);
    }

    private void runFaceAnalysis(Bitmap bmp) {
        new Thread(() -> {
            try {
                float[] emb = FaceEmbeddingManager.extractEmbeddingSync(this, bmp);
                if (emb != null) {
                    firstFaceEmbedding.set(emb);
                    runOnUiThread(() -> {
                        if (tvMatchResult != null) tvMatchResult.setVisibility(View.GONE);
                    });
                } else {
                    // [إصلاح 4.2] رسالة واضحة إذا لم يُكتشف وجه
                    android.util.Log.w("ReportActivity",
                        "⚠️ لم يُستخرج embedding — المطابقة التلقائية لن تعمل لهذه الصورة");
                    runOnUiThread(() -> {
                        if (scrollReport != null) {
                            Snackbar.make(scrollReport,
                                "⚠️ لم يتم الكشف عن وجه واضح — يمكنك المتابعة لكن المطابقة التلقائية لن تعمل",
                                Snackbar.LENGTH_LONG)
                                .setAction("حسناً", v -> {})
                                .show();
                        }
                    });
                }
                int age = FaceEmbeddingManager.estimateAge(this, bmp);
                if (age > 0) onAgeEstimated(age);
                // [Phase 5] تقدير الجنس من الوجه وعرضه كاقتراح
                float[] genderConf = FaceEmbeddingManager.estimateGenderConfidence(bmp);
                onGenderEstimated(genderConf[0], genderConf[1]);
            } catch (Exception e) {
                android.util.Log.e("ReportActivity", "runFaceAnalysis error: " + e.getMessage());
            }
        }).start();
    }

    // ════════════════════════════════════════════════════
    //  GPS / Location
    // ════════════════════════════════════════════════════

    private void requestLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_PERM);
            return;
        }
        fusedClient.getLastLocation().addOnSuccessListener(loc -> {
            if (loc != null) {
                lat = loc.getLatitude();
                lng = loc.getLongitude();
                if (tvGpsStatus != null) {
                    tvGpsStatus.setText("📍 تم تحديد الموقع تلقائياً — يمكنك تعديله من الخريطة");
                    tvGpsStatus.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void openMapPicker() {
        Intent i = new Intent(this, MapActivity.class);
        i.putExtra(MapActivity.EXTRA_PICK_MODE, true);
        if (lat != 0) i.putExtra(MapActivity.EXTRA_LAT, lat);
        if (lng != 0) i.putExtra(MapActivity.EXTRA_LNG, lng);
        startActivityForResult(i, REQ_MAP);
    }

    // ════════════════════════════════════════════════════
    //  Submit
    // ════════════════════════════════════════════════════

    private void submit() {
        if (!validateCurrentStep()) return;
        if (!RateLimiter.canSubmitReport(this)) { showError("وصلت للحد اليومي من البلاغات"); return; }

        // [إصلاح DuplicateDetector] فحص التكرار قبل الرفع
        // إذا كان لدينا embedding وجه → فحص بالوجه (أدق)، وإلا تابع مباشرة
        float[] emb = firstFaceEmbedding.get();
        if (emb != null) {
            if (tvStatus != null) { tvStatus.setText("🔍 فحص التكرار..."); tvStatus.setVisibility(View.VISIBLE); }
            DuplicateDetector.checkByFace(emb, (isDuplicate, existingId, similarity, reason) ->
                runOnUiThread(() -> {
                    if (tvStatus != null) tvStatus.setVisibility(View.GONE);
                    if (isDuplicate) {
                        int pct = (int)(similarity * 100);
                        // [إصلاح] رسالة مختلفة حسب نوع التكرار
                        String title, message;
                        if ("missing_already_reported".equals(reason)) {
                            title   = "⚠️ هذا الشخص مُبلَّغ عنه مسبقاً";
                            message = "يوجد بلاغ مفقود مشابه بنسبة " + pct + "%"
                                + " (رقم البلاغ: " + existingId + ").\n\n"
                                + "هل تريد الاستمرار في رفع بلاغ جديد؟";
                        } else if ("found_already_reported".equals(reason)) {
                            title   = "✅ هذا الشخص مُبلَّغ عنه كمعثور مسبقاً";
                            message = "يوجد بلاغ عثور مشابه بنسبة " + pct + "%"
                                + " (رقم البلاغ: " + existingId + ").\n\n"
                                + "هل تريد رفع بلاغ إضافي على أي حال؟";
                        } else {
                            title   = "⚠️ بلاغ مشابه موجود";
                            message = "يوجد بلاغ مشابه بنسبة " + pct + "%. هل تريد المتابعة؟";
                        }
                        new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle(title)
                            .setMessage(message)
                            .setPositiveButton("متابعة الرفع", (d, w) -> doSubmit())
                            .setNegativeButton("إلغاء", (d, w) -> {
                                d.dismiss();
                                if (btnSubmit != null) btnSubmit.setEnabled(true);
                            })
                            .setCancelable(false)
                            .show();
                    } else {
                        doSubmit();
                    }
                })
            );
        } else {
            doSubmit();
        }
    }

    /** تنفيذ الرفع الفعلي بعد اجتياز فحص التكرار */
    private void doSubmit() {
        isUploading = true;
        btnSubmit.setEnabled(false);
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        if (!OfflineSyncManager.isOnline(this)) {
            if (tvStatus != null) { tvStatus.setText("📴 لا يوجد اتصال — سيُرفع البلاغ تلقائياً"); tvStatus.setVisibility(View.VISIBLE); }
            OfflineSyncManager.saveOffline(this, new HashMap<>(buildReportData(new ArrayList<>())));
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            new Handler(Looper.getMainLooper()).postDelayed(this::showSuccessScreen, 500);
            return;
        }

        if (tvStatus != null) { tvStatus.setText("⏳ جارٍ رفع الصور..."); tvStatus.setVisibility(View.VISIBLE); }
        uploadPhotosSequentially(new ArrayList<>(), 0, this::saveToFirebase);
    }

    private interface OnUploaded { void done(List<String> urls); }

    private void uploadPhotosSequentially(List<String> urls, int idx, OnUploaded cb) {
        if (idx >= photoFiles.size()) { cb.done(urls); return; }
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
            ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anon";
        if (tvStatus != null) tvStatus.setText("⏳ ضغط الصورة " + (idx+1) + " من " + photoFiles.size() + "...");

        // FIX #7: ضغط الصورة + تصحيح تدوير EXIF قبل الرفع
        final File originalFile = photoFiles.get(idx);
        com.missingpersons.app.utils.ImageCompressor.compress(this, originalFile,
            new com.missingpersons.app.utils.ImageCompressor.CompressCallback() {
                @Override public void onCompressed(File compressedFile) {
                    String ext = compressedFile.getName().endsWith(".webp") ? ".webp" : ".jpg";
                    String path = "reports/" + uid + "/photo_" + idx + "_"
                        + System.currentTimeMillis() + ext;
                    if (tvStatus != null)
                        tvStatus.setText("⏳ رفع الصورة " + (idx+1) + " من " + photoFiles.size() + "...");
                    FirebaseStorage.getInstance().getReference(path)
                        .putFile(Uri.fromFile(compressedFile))
                        .addOnSuccessListener(snap -> snap.getStorage().getDownloadUrl()
                            .addOnSuccessListener(u -> {
                                urls.add(u.toString());
                                uploadPhotosSequentially(urls, idx + 1, cb);
                            }))
                        .addOnFailureListener(e -> uploadPhotosSequentially(urls, idx + 1, cb));
                }
                @Override public void onError(String error) {
                    // Fallback: ارفع الملف الأصلي إذا فشل الضغط
                    String path = "reports/" + uid + "/photo_" + idx + "_"
                        + System.currentTimeMillis() + ".jpg";
                    FirebaseStorage.getInstance().getReference(path)
                        .putFile(Uri.fromFile(originalFile))
                        .addOnSuccessListener(snap -> snap.getStorage().getDownloadUrl()
                            .addOnSuccessListener(u -> {
                                urls.add(u.toString());
                                uploadPhotosSequentially(urls, idx + 1, cb);
                            }))
                        .addOnFailureListener(e -> uploadPhotosSequentially(urls, idx + 1, cb));
                }
            });
    }

    private void saveToFirebase(List<String> newUrls) {
        List<String> allUrls = new ArrayList<>(existingImageUrls);
        for (String u : newUrls) if (!allUrls.contains(u)) allUrls.add(u);
        Map<String, Object> report = buildReportData(allUrls);

        // FIX: كل البلاغات تذهب لـ "reports" مع حقل reportType للتمييز
        // هذا يضمن أن StatisticsActivity و NewHomeActivity و SuccessStoriesActivity
        // تقرأ من مصدر واحد بدون تشتت
        String dbPath = "reports";

        // نكتب مرجع في المسار الأصلي أيضاً للـ backward compat مع AdminDashboard القديم
        String mirrorPath = null;
        switch (currentType) {
            case TYPE_FOUND:    mirrorPath = "found_persons"; break;
            case TYPE_SIGHTING: mirrorPath = "sightings";     break;
        }

        String uid = (String) report.get("reporterId");
        int shortId = (int)(System.currentTimeMillis() % 10000);
        String reportId = "SND-" + String.format("%04d", shortId);
        savedReportId = reportId;
        report.put("reportId", reportId);

        if (tvStatus != null) tvStatus.setText("⏳ جارٍ حفظ البلاغ...");

        float[] emb = firstFaceEmbedding.get();
        if (emb != null) report.put("faceEmbedding", FaceEmbeddingManager.embeddingToString(emb));

        // FIX: يجب أن تكون final لاستخدامها داخل lambda
        final String finalMirrorPath = mirrorPath;

        // الكتابة الأساسية في reports
        FirebaseDatabase.getInstance().getReference(dbPath).child(reportId)
            .setValue(report)
            .addOnSuccessListener(v -> {
                // كتابة مرجع في المسار الثانوي (mirror) إن وجد
                if (finalMirrorPath != null) {
                    // نحفظ مرجع خفيف بدون صور لتوفير الفضاء
                    Map<String, Object> mirror = new HashMap<>();
                    mirror.put("reportId",   reportId);
                    mirror.put("reportType", currentType);
                    mirror.put("status",     "pending");
                    mirror.put("timestamp",  report.get("timestamp"));
                    mirror.put("personName", report.get("personName"));
                    FirebaseDatabase.getInstance().getReference(finalMirrorPath)
                        .child(reportId).setValue(mirror);
                }

                if (progressBar != null) progressBar.setVisibility(View.GONE);
                String pName = report.get("personName") instanceof String
                    ? (String) report.get("personName") : "مجهول";
                if (TYPE_SIGHTING.equals(currentType))
                    PointsManager.addPoints(PointsManager.ACTION_SIGHTING_REPORTED, pName);
                else
                    PointsManager.addPoints(PointsManager.ACTION_REPORT_SUBMITTED, pName);
                if (emb != null) {
                    String embStr = FaceEmbeddingManager.embeddingToString(emb);
                    if (TYPE_FOUND.equals(currentType))
                        CrossMatchManager.matchFoundPersonWithReports(reportId, uid, embStr);
                    else if (TYPE_MISSING.equals(currentType))
                        CrossMatchManager.matchReportWithFoundPersons(reportId, uid, pName, embStr);
                }
                notifyAdminNewReport(reportId, pName, report);
                showSuccessScreen();
            })
            .addOnFailureListener(e -> {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                isUploading = false;
                btnSubmit.setEnabled(true);
                if (tvStatus != null) tvStatus.setText("❌ فشل: " + e.getMessage());
            });
    }

    // ════════════════════════════════════════════════════
    //  SUCCESS SCREEN
    // ════════════════════════════════════════════════════

    private void showSuccessScreen() {
        // [إصلاح 4.3] امسح المسودة عند النجاح
        clearDraft();
        runOnUiThread(() -> {
            // Hide wizard, show success
            if (wizardContainer != null) wizardContainer.setVisibility(View.GONE);
            if (layoutSuccess   != null) layoutSuccess.setVisibility(View.VISIBLE);
            // Set report ID
            if (tvReportId != null) {
                String displayId = savedReportId.isEmpty()
                    ? "#SND-" + String.format("%04d", (int)(System.currentTimeMillis() % 10000))
                    : "#" + savedReportId;
                tvReportId.setText(displayId);
            }
        });
    }

    private void shareReport() {
        String msg = "🔍 بلاغ عن مفقود — سند | Sanad\n"
            + (tvReportId != null ? "رقم البلاغ: " + tvReportId.getText() : "")
            + "\nيرجى المشاركة للمساعدة في العثور على هذا الشخص.";
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, msg);
        startActivity(Intent.createChooser(share, "شارك البلاغ"));
    }


    // ════════════════════════════════════════════════════
    //  Admin Notification — يُستدعى بعد حفظ البلاغ
    // ════════════════════════════════════════════════════

    /**
     * يكتب سجل إشعار في "admin_notifications" و "notifications/{adminUid}".
     * AdminDashboardActivity يراقب admin_notifications بـ ChildEventListener
     * ويُحدّث الـ Dashboard ويعرض إشعاراً فورياً للأدمن.
     */
    private void notifyAdminNewReport(String reportId, String personName,
                                       Map<String, Object> reportData) {
        try {
            String gov = reportData.containsKey("governorate")
                    ? String.valueOf(reportData.get("governorate")) : "";
            String rType = currentType;

            HashMap<String, Object> notif = new HashMap<>();
            notif.put("type",        "new_report");
            notif.put("reportId",    reportId);
            notif.put("personName",  personName);
            notif.put("reportType",  rType);
            notif.put("governorate", gov);
            notif.put("reporterId",  FirebaseAuth.getInstance().getCurrentUser() != null
                    ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "");
            notif.put("timestamp",   System.currentTimeMillis());
            notif.put("read",        false);

            // ─── 1) كتابة في admin_notifications (يراقبه Dashboard) ───
            FirebaseDatabase.getInstance()
                    .getReference("admin_notifications")
                    .push()
                    .setValue(notif);

            // ─── 2) كتابة في notifications/{adminUid} ────────────────
            FirebaseDatabase.getInstance()
                    .getReference("users")
                    .orderByChild("role")
                    .equalTo("admin") // role-based — no ADMIN_EMAIL
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snap) {
                            for (DataSnapshot admin : snap.getChildren()) {
                                String adminUid = admin.getKey();
                                if (adminUid != null) {
                                    HashMap<String, Object> n2 = new HashMap<>();
                                    n2.put("type",      "new_report");
                                    n2.put("reportId",  reportId);
                                    n2.put("message",   "بلاغ جديد: " + personName);
                                    n2.put("timestamp", System.currentTimeMillis());
                                    n2.put("read",      false);
                                    FirebaseDatabase.getInstance()
                                            .getReference("notifications")
                                            .child(adminUid)
                                            .push()
                                            .setValue(n2);
                                }
                            }
                        }
                        @Override public void onCancelled(@NonNull DatabaseError e) {}
                    });

        } catch (Exception ignored) {
            // لا نوقف البلاغ إذا فشل الإشعار
        }
    }

    // ════════════════════════════════════════════════════
    //  Build Report Data
    // ════════════════════════════════════════════════════

    private Map<String, Object> buildReportData(List<String> imageUrls) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
            ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anon";
        String name     = getText(etName);
        String ageStr   = getText(etAge);
        String gov      = spGov != null  ? spGov.getText().toString().trim()  : "";
        String city     = spCity != null ? spCity.getText().toString().trim() : "";
        String area     = spArea != null ? spArea.getText().toString().trim() : "";
        String landmark = getText(etLandmark);
        String clothes  = getText(etClothes);
        String desc     = getText(etDescription);
        String phone    = getText(etPhone);
        String physical = getText(etPhysicalCondition);
        boolean chatEnabled = switchChat != null && switchChat.isChecked();
        boolean phonePublic = switchPhonePublic != null && switchPhonePublic.isChecked();
        List<String> markers = new ArrayList<>();
        if (chipGroupMarkers != null) for (int i=0; i<chipGroupMarkers.getChildCount(); i++) {
            View c = chipGroupMarkers.getChildAt(i);
            if (c instanceof Chip && ((Chip) c).isChecked()) markers.add(((Chip) c).getText().toString());
        }

        String fullAddress = EgyptAddressHelper.buildAddress(gov, city, area, landmark);
        if (fullAddress.isEmpty()) fullAddress = manualAddress;

        Map<String, Object> r = new HashMap<>();
        r.put("imageUrls",    imageUrls);
        r.put("imageUrl",     imageUrls.isEmpty() ? "" : imageUrls.get(0));
        r.put("reportType",   currentType);
        r.put("ageCategory",  selectedAgeCategory);
        r.put("personName",   name.isEmpty() ? "مجهول" : name);
        r.put("personAge",    ageStr.isEmpty() ? "؟" : ageStr);
        // FIX: نحفظ gender بالإنجليزي للـ backward compat
        r.put("gender",       selectedGender);
        // FIX: نحفظ personGender بالعربي لإحصائيات StatisticsActivity
        r.put("personGender", "female".equals(selectedGender) ? "أنثى" : "ذكر");
        // FIX: نحفظ ageRange بنفس مفاتيح StatisticsActivity
        r.put("ageRange",     mapAgeCategoryToRange(selectedAgeCategory));
        if (selectedIncidentDate > 0) r.put("incidentDate", selectedIncidentDate);
        r.put("governorate",  gov);
        if (!city.isEmpty())     r.put("cityDistrict", city);
        if (!area.isEmpty())     r.put("subDistrict", area);
        if (!landmark.isEmpty()) r.put("landmark", landmark);
        if (!clothes.isEmpty())  r.put("clothesDescription", clothes);
        if (!markers.isEmpty())  r.put("physicalMarkers", markers);
        if (!physical.isEmpty()) r.put("physicalCondition", physical);
        r.put("manualAddress", fullAddress);
        r.put("lat",           lat);
        r.put("lng",           lng);
        // FIX: نحفظ latitude/longitude أيضاً لأن NewHomeActivity يقرأهم بهذين الاسمين
        r.put("latitude",      lat);
        r.put("longitude",     lng);
        r.put("locationText",  fullAddress);
        if (!phone.isEmpty())    r.put("phone", phone);
        r.put("phonePublic",   phonePublic);
        r.put("chatEnabled",   chatEnabled);
        if (selectedFoundDate > 0) r.put("foundDate", selectedFoundDate);
        if (!desc.isEmpty())     r.put("description", desc);
        r.put("reporterId",    uid);
        r.put("timestamp",     System.currentTimeMillis());
        r.put("status",        "pending"); // ✅ إصلاح رئيسي: كان غائباً تماماً
        r.put("moderation_status", "pending_review"); // [Phase 5] مراجعة تلقائية
        return r;
    }

    // ════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════

    /** Auto-derive age category from numeric age string */
    private String deriveAgeCategory(String ageStr) {
        try {
            int age = Integer.parseInt(ageStr == null ? "" : ageStr.trim());
            if (age < 18)  return AGE_CAT_CHILD;
            if (age <= 60) return AGE_CAT_ADULT;
            return AGE_CAT_ELDER;
        } catch (NumberFormatException e) {
            return selectedAgeCategory; // keep current if non-numeric
        }
    }

    /**
     * FIX: تحويل ageCategory إلى ageRange بنفس مفاتيح StatisticsActivity
     * حتى تتطابق البيانات مع خريطة الفئات العمرية في الإحصائيات
     */
    private String mapAgeCategoryToRange(String ageCategory) {
        if (ageCategory == null) return "";
        switch (ageCategory) {
            case "baby":
            case "رضيع":      return "رضيع (0-2)";
            case "child":
            case "طفل":       return "طفل (3-12)";
            case "teen":
            case "مراهق":     return "مراهق (13-17)";
            case "young":
            case "شاب":       return "شاب (18-30)";
            case "adult":
            case "بالغ":      return "بالغ (31-50)";
            case "elderly":
            case "كبير":      return "كبير (50+)";
            default:          return ageCategory;
        }
    }

    private String getText(TextInputEditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString().trim();
    }

    /**
     * ExifInterface rotation fix — يقرأ بيانات EXIF من الـ URI
     * ويعيد الـ Bitmap بالاتجاه الصحيح.
     * يعمل مع كاميرا الجهاز والمعرض على API 24+.
     */
    private Bitmap fixExifRotation(Bitmap bitmap, Uri uri) {
        try {
            java.io.InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return bitmap;
            ExifInterface exif = new ExifInterface(is);
            is.close();
            int orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int degrees = 0;
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:  degrees =  90; break;
                case ExifInterface.ORIENTATION_ROTATE_180: degrees = 180; break;
                case ExifInterface.ORIENTATION_ROTATE_270: degrees = 270; break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    Matrix mfh = new Matrix(); mfh.preScale(-1, 1);
                    return Bitmap.createBitmap(bitmap, 0, 0,
                        bitmap.getWidth(), bitmap.getHeight(), mfh, true);
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    Matrix mfv = new Matrix(); mfv.preScale(1, -1);
                    return Bitmap.createBitmap(bitmap, 0, 0,
                        bitmap.getWidth(), bitmap.getHeight(), mfv, true);
                default: return bitmap; // ORIENTATION_NORMAL — لا تغيير
            }
            if (degrees == 0) return bitmap;
            Matrix m = new Matrix();
            m.postRotate(degrees);
            Bitmap rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
            if (rotated != bitmap) bitmap.recycle();
            return rotated;
        } catch (Exception e) {
            return bitmap; // fallback — أعد الأصل بدون دوران
        }
    }

    private Bitmap scaleBitmap(Bitmap src, int max) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= max && h <= max) return src;
        float s = Math.min((float)max/w, (float)max/h);
        return Bitmap.createScaledBitmap(src, (int)(w*s), (int)(h*s), true);
    }

    private File createTempImageFile() throws IOException {
        File dir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
        return File.createTempFile("photo_" + System.currentTimeMillis(), ".jpg", dir);
    }

    private void copyUriToFile(Uri uri, File dest) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[4096]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }

    private int getColorFromAttr(int attr) {
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    private int dpToPx(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        if (req == REQ_PERM && grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED)
            requestLocation();
    }

    // ════════════════════════════════════════════════════
    //  [إصلاح 4.3] Auto-save Draft
    // ════════════════════════════════════════════════════

    /** يحفظ البيانات المُدخلة في SharedPreferences */
    private void saveDraft() {
        try {
            android.content.SharedPreferences prefs =
                getSharedPreferences("report_draft", MODE_PRIVATE);
            android.content.SharedPreferences.Editor ed = prefs.edit();

            ed.putString("draft_type", currentType != null ? currentType : "");
            ed.putString("draft_step", String.valueOf(currentStep));

            // Step 2 fields
            if (etName != null && etName.getText() != null)
                ed.putString("draft_name", etName.getText().toString());
            if (etAge != null && etAge.getText() != null)
                ed.putString("draft_age",  etAge.getText().toString());
            if (etDescription != null && etDescription.getText() != null)
                ed.putString("draft_desc", etDescription.getText().toString());

            // Location
            ed.putFloat("draft_lat", (float) lat);
            ed.putFloat("draft_lng", (float) lng);

            ed.putLong("draft_ts", System.currentTimeMillis());
            ed.apply();

            android.util.Log.d("ReportActivity", "📝 draft saved step=" + currentStep);
        } catch (Exception e) {
            android.util.Log.w("ReportActivity", "saveDraft error: " + e.getMessage());
        }
    }

    /** يسترجع المسودة المحفوظة ويملأ الحقول */
    private void restoreDraftIfExists() {
        try {
            android.content.SharedPreferences prefs =
                getSharedPreferences("report_draft", MODE_PRIVATE);
            long draftTs = prefs.getLong("draft_ts", 0);
            if (draftTs == 0) return;

            // تجاهل المسودات الأقدم من 24 ساعة
            if (System.currentTimeMillis() - draftTs > 24 * 60 * 60 * 1000L) {
                prefs.edit().clear().apply();
                return;
            }

            String draftName = prefs.getString("draft_name", "");
            String draftAge  = prefs.getString("draft_age",  "");
            String draftDesc = prefs.getString("draft_desc", "");
            float  draftLat  = prefs.getFloat("draft_lat",   0f);
            float  draftLng  = prefs.getFloat("draft_lng",   0f);

            if (draftName.isEmpty() && draftAge.isEmpty()) return;

            // اعرض dialog للمستخدم ليختار الاستمرار
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("📝 استكمال مسودة")
                .setMessage("يوجد بلاغ غير مكتمل محفوظ. هل تريد الاستمرار من حيث توقفت؟")
                .setPositiveButton("استكمال", (d, w) -> {
                    // [إصلاح] استعادة كل البيانات والانتقال للخطوة المحفوظة
                    String draftType = prefs.getString("draft_type", "");
                    int    draftStep = Integer.parseInt(prefs.getString("draft_step", "1"));
                    if (!draftType.isEmpty()) setType(draftType);
                    if (etName != null && !draftName.isEmpty())
                        etName.setText(draftName);
                    if (etAge != null && !draftAge.isEmpty())
                        etAge.setText(draftAge);
                    if (etDescription != null && !draftDesc.isEmpty())
                        etDescription.setText(draftDesc);
                    if (draftLat != 0) { lat = draftLat; lng = draftLng; }
                    // انتقل للخطوة المحفوظة (ليس من البداية)
                    if (draftStep > 1 && draftStep <= 4)
                        updateStep(draftStep);
                    android.util.Log.d("ReportActivity", "✅ draft restored to step " + draftStep);
                })
                .setNegativeButton("بلاغ جديد", (d, w) -> {
                    prefs.edit().clear().apply();
                    updateStep(1); // ابدأ من الأول
                })
                .show();
        } catch (Exception e) {
            android.util.Log.w("ReportActivity", "restoreDraft error: " + e.getMessage());
        }
    }

    /** مسح المسودة بعد الإرسال الناجح */
    private void clearDraft() {
        getSharedPreferences("report_draft", MODE_PRIVATE).edit().clear().apply();
        android.util.Log.d("ReportActivity", "🗑️ draft cleared");
    }

    @Override
    protected void onPause() {
        super.onPause();
        // [إصلاح 4.3] احفظ المسودة إذا المستخدم خرج في المنتصف
        if (currentStep > 1 && (layoutSuccess == null ||
                layoutSuccess.getVisibility() != View.VISIBLE)) {
            saveDraft();
        }
    }

    @Override
    public void onBackPressed() {
        if (layoutSuccess != null && layoutSuccess.getVisibility() == View.VISIBLE) {
            finish(); return;
        }
        if (currentStep > 1) goToPrevStep();
        else super.onBackPressed();
    }
}
