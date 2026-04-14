package com.missingpersons.app.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import com.missingpersons.app.R;
import com.missingpersons.app.ui.common.AppViewModelFactory;
import com.missingpersons.app.ui.profile.ProfileViewModel;
import com.missingpersons.app.utils.CoilImageLoader;
import com.missingpersons.app.utils.LanguageHelper;
import com.missingpersons.app.utils.RoleManager;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * ProfileActivity
 *
 * [إصلاح 2.1] تعديل الاسم بـ dialog
 * [إصلاح 2.3] card_admin_access مخفية حتى تأكيد Firebase
 * [إصلاح 2.4] switch_notifications مرتبط بـ FCM
 * [مرحلة 7.5] Rank progress bar — يعرض التقدم نحو الرتبة التالية
 *             مع إحصائيات: بلاغاتي / تطابقات / مُحلّة
 */
public class ProfileActivity extends AppCompatActivity {

    private static final String PREF_PROFILE    = "profile_cache";
    private static final String KEY_ROLE        = "cached_role";
    private static final int    PICK_IMAGE_CODE = 201;
    private static final String PLAY_STORE_LINK =
        "https://play.google.com/store/apps/details?id=com.missingpersons.app";

    // Rank thresholds
    private static final int[] RANK_THRESHOLDS = {0, 500, 2000, 5000};
    private static final String[] RANK_LABELS  = {"عضو", "نشط", "متميز", "بطل"};
    private static final String[] RANK_EMOJIS  = {"🟢", "🔵", "⭐", "🏆"};

    private ProfileViewModel viewModel;

    // ── UI ──
    private CircleImageView       ivAvatar;
    private TextView              tvName, tvEmail, tvRole, tvReportsCount, tvPoints, tvJoinDate;
    private MaterialCardView      cardAdmin;
    private SwitchCompat          switchNotifications;

    // [7.5] Rank card views
    private TextView              tvRankEmoji, tvRankLabel, tvRankPoints;
    private TextView              tvRankNextLabel, tvRankCurrentPts, tvRankTargetPts;
    private LinearProgressIndicator progressRank;
    private Chip                  chipPointsBadge;
    private TextView              tvStatMyReports, tvStatMatched, tvStatResolved;

    private FirebaseUser currentUser;

    @Override
    protected void attachBaseContext(android.content.Context c) {
        super.attachBaseContext(LanguageHelper.applyLanguage(c));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bot = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), bot);
            return insets;
        });

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("حسابي");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || currentUser.isAnonymous()) {
            showLoginRequiredDialog(); return;
        }

        viewModel = new ViewModelProvider(this, new AppViewModelFactory(this))
                .get(ProfileViewModel.class);

        initViews();
        observeViewModel();
        viewModel.loadProfile(currentUser.getUid());
        applyCachedRole();
        setupActions();
    }

    // ════════════════════════════════════════════════════
    //  Observers
    // ════════════════════════════════════════════════════

    private void observeViewModel() {
        viewModel.getProfileData().observe(this, data -> {
            if (data == null) return;

            if (tvName != null)
                tvName.setText(data.name.isEmpty()
                        ? (currentUser.getDisplayName() != null
                           ? currentUser.getDisplayName() : "مستخدم")
                        : data.name);

            if (tvEmail != null)
                tvEmail.setText(data.email.isEmpty()
                        ? (currentUser.getEmail() != null ? currentUser.getEmail() : "")
                        : data.email);

            if (tvRole != null) { tvRole.setText(data.role); cacheRole(data.rawRole); }

            if (tvPoints != null) tvPoints.setText(String.valueOf(data.points));
            if (tvReportsCount != null) tvReportsCount.setText(String.valueOf(data.totalReports));

            // تاريخ الانضمام
            if (tvJoinDate != null && data.joinDate > 0) {
                java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("dd MMM yyyy", new java.util.Locale("ar"));
                tvJoinDate.setText("انضم في " + sdf.format(new java.util.Date(data.joinDate)));
            }

            // صورة الأفاتار
            if (ivAvatar != null) {
                String photoUrl = data.photoUrl;
                if (photoUrl != null && !photoUrl.isEmpty())
                    CoilImageLoader.load(this, photoUrl, ivAvatar);
                else if (currentUser.getPhotoUrl() != null)
                    CoilImageLoader.load(this, currentUser.getPhotoUrl().toString(), ivAvatar);
            }

            // [إصلاح 2.3] card_admin فقط بعد تأكيد Firebase
            if (cardAdmin != null)
                cardAdmin.setVisibility(
                    (data.isAdmin() || data.isManager()) ? View.VISIBLE : View.GONE);

            // [7.5] تحديث Rank card
            updateRankCard(data);
        });

        viewModel.getErrorMsg().observe(this, error -> {
            if (error != null && !error.isEmpty())
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
        });
    }

    // ════════════════════════════════════════════════════
    //  [7.5] Rank Card
    // ════════════════════════════════════════════════════

    private void updateRankCard(ProfileViewModel.ProfileData data) {
        int pts = data.points;

        // تحديد الرتبة الحالية والتالية
        int rankIdx = 0;
        for (int i = RANK_THRESHOLDS.length - 1; i >= 0; i--) {
            if (pts >= RANK_THRESHOLDS[i]) { rankIdx = i; break; }
        }

        int nextIdx      = Math.min(rankIdx + 1, RANK_THRESHOLDS.length - 1);
        int currentFloor = RANK_THRESHOLDS[rankIdx];
        int nextTarget   = RANK_THRESHOLDS[nextIdx];
        boolean maxRank  = (rankIdx == RANK_THRESHOLDS.length - 1);

        // Emoji + Label
        if (tvRankEmoji != null) tvRankEmoji.setText(RANK_EMOJIS[rankIdx]);
        if (tvRankLabel != null) tvRankLabel.setText(RANK_LABELS[rankIdx]);
        if (tvRankPoints != null)
            tvRankPoints.setText(pts + " نقطة" + (maxRank ? " — الرتبة الأعلى! 🎉" : ""));

        // Chip
        if (chipPointsBadge != null) chipPointsBadge.setText(pts + " pts");

        // Progress bar
        if (progressRank != null) {
            if (maxRank) {
                progressRank.setProgress(100);
            } else {
                int range    = nextTarget - currentFloor;
                int achieved = pts - currentFloor;
                int pct      = range > 0 ? (int)((achieved * 100f) / range) : 100;
                progressRank.setProgress(Math.min(100, pct));
            }
        }

        // Labels تحت الـ progress bar
        if (tvRankCurrentPts != null) tvRankCurrentPts.setText(String.valueOf(pts));
        if (tvRankTargetPts  != null)
            tvRankTargetPts.setText(maxRank ? "MAX" : String.valueOf(nextTarget));

        // Next rank label
        if (tvRankNextLabel != null) {
            if (maxRank) {
                tvRankNextLabel.setText("🏆 وصلت للرتبة الأعلى!");
            } else {
                int remaining = nextTarget - pts;
                tvRankNextLabel.setText("تبقى " + remaining
                    + " نقطة للوصول إلى رتبة " + RANK_LABELS[nextIdx]
                    + " " + RANK_EMOJIS[nextIdx]);
            }
        }

        // إحصائيات
        if (tvStatMyReports != null) tvStatMyReports.setText(String.valueOf(data.totalReports));
        if (tvStatMatched   != null) tvStatMatched.setText(String.valueOf(data.matchedReports));
        if (tvStatResolved  != null) tvStatResolved.setText(String.valueOf(data.resolvedReports));
    }

    // ════════════════════════════════════════════════════
    //  initViews
    // ════════════════════════════════════════════════════

    private void initViews() {
        ivAvatar            = findViewById(R.id.iv_profile_avatar);
        tvName              = findViewById(R.id.tv_profile_name);
        tvEmail             = findViewById(R.id.tv_profile_email);
        tvRole              = findViewById(R.id.tv_profile_role);
        tvReportsCount      = findViewById(R.id.tv_profile_reports_count);
        tvPoints            = findViewById(R.id.tv_profile_points);
        tvJoinDate          = findViewById(R.id.tv_profile_join_date);
        cardAdmin           = findViewById(R.id.card_admin_access);
        switchNotifications = findViewById(R.id.switch_notifications);

        // [إصلاح 2.3] إخفاء cardAdmin افتراضياً
        if (cardAdmin != null) cardAdmin.setVisibility(View.GONE);

        // [7.5] Rank card views (في layout_profile_rank_card المُضمَّن)
        tvRankEmoji     = findViewById(R.id.tv_rank_emoji);
        tvRankLabel     = findViewById(R.id.tv_rank_label);
        tvRankPoints    = findViewById(R.id.tv_rank_points);
        tvRankNextLabel = findViewById(R.id.tv_rank_next_label);
        progressRank    = findViewById(R.id.progress_rank);
        tvRankCurrentPts= findViewById(R.id.tv_rank_current_pts);
        tvRankTargetPts = findViewById(R.id.tv_rank_target_pts);
        chipPointsBadge = findViewById(R.id.chip_points_badge);
        tvStatMyReports = findViewById(R.id.tv_stat_my_reports);
        tvStatMatched   = findViewById(R.id.tv_stat_matched);
        tvStatResolved  = findViewById(R.id.tv_stat_resolved);

        View btnChangeAvatar = findViewById(R.id.btn_change_avatar);
        if (btnChangeAvatar != null) btnChangeAvatar.setOnClickListener(v -> openImagePicker());
        if (ivAvatar != null) ivAvatar.setOnClickListener(v -> openImagePicker());
    }

    // ════════════════════════════════════════════════════
    //  Actions
    // ════════════════════════════════════════════════════

    private void setupActions() {
        if (cardAdmin != null)
            cardAdmin.setOnClickListener(v ->
                startActivity(new Intent(this, AdminDashboardActivity.class)));

        setClick(R.id.btn_edit_profile_main, this::showEditNameDialog);
        setClick(R.id.btn_my_reports,
            () -> startActivity(new Intent(this, MyReportsActivity.class)));

        setClick(R.id.btn_share_app, () -> {
            // [7.6] مشاركة التطبيق
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, "تطبيق سند — للبحث عن المفقودين");
            share.putExtra(Intent.EXTRA_TEXT,
                "📱 تطبيق سند للبحث عن الأشخاص المفقودين\n"
                + "حمّل التطبيق: " + PLAY_STORE_LINK);
            startActivity(Intent.createChooser(share, "شارك التطبيق"));
        });

        setClick(R.id.btn_about,
            () -> startActivity(new Intent(this, AboutActivity.class)));
        setClick(R.id.btn_disclaimer,
            () -> startActivity(new Intent(this, DisclaimerActivity.class)));

        setClick(R.id.btn_logout, () -> new AlertDialog.Builder(this)
            .setTitle("تسجيل الخروج")
            .setMessage("هل تريد تسجيل الخروج؟")
            .setPositiveButton("خروج", (d, w) -> {
                RoleManager.reset();
                FirebaseAuth.getInstance().signOut();
                startActivity(new Intent(this, LoginActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            })
            .setNegativeButton("إلغاء", null).show());

        // [إصلاح 2.4] FCM Switch
        if (switchNotifications != null) {
            SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
            switchNotifications.setChecked(prefs.getBoolean("notifications_enabled", true));
            switchNotifications.setOnCheckedChangeListener((btn, isChecked) -> {
                prefs.edit().putBoolean("notifications_enabled", isChecked).apply();
                if (!isChecked) {
                    FirebaseMessaging.getInstance().deleteToken()
                        .addOnSuccessListener(v ->
                            Toast.makeText(this, "تم إيقاف الإشعارات", Toast.LENGTH_SHORT).show());
                } else {
                    FirebaseMessaging.getInstance().getToken()
                        .addOnSuccessListener(token -> {
                            if (currentUser != null)
                                FirebaseDatabase.getInstance()
                                    .getReference("users").child(currentUser.getUid())
                                    .child("fcmToken").setValue(token);
                            Toast.makeText(this, "✅ تم تفعيل الإشعارات", Toast.LENGTH_SHORT).show();
                        });
                }
            });
        }
    }

    // ════════════════════════════════════════════════════
    //  Edit Name
    // ════════════════════════════════════════════════════

    private void showEditNameDialog() {
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        et.setHint("اسمك الكامل");
        if (tvName != null) et.setText(tvName.getText());
        et.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        et.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
            .setTitle("تعديل الاسم").setView(et)
            .setPositiveButton("حفظ", (d, w) -> {
                String name = et.getText().toString().trim();
                if (!name.isEmpty()) updateDisplayName(name);
                else Toast.makeText(this, "الاسم لا يمكن أن يكون فارغاً", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("إلغاء", null).show();
    }

    private void updateDisplayName(String name) {
        if (currentUser == null) return;
        currentUser.updateProfile(new UserProfileChangeRequest.Builder()
                .setDisplayName(name).build())
            .addOnSuccessListener(v -> {
                FirebaseDatabase.getInstance()
                    .getReference("users").child(currentUser.getUid())
                    .child("name").setValue(name);
                if (tvName != null) tvName.setText(name);
                Toast.makeText(this, "✅ تم تحديث الاسم", Toast.LENGTH_SHORT).show();
            })
            .addOnFailureListener(e ->
                Toast.makeText(this, "❌ " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // ════════════════════════════════════════════════════
    //  Image Picker
    // ════════════════════════════════════════════════════

    private void openImagePicker() {
        Intent i = new Intent(Intent.ACTION_PICK);
        i.setType("image/*");
        startActivityForResult(i, PICK_IMAGE_CODE);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK_IMAGE_CODE && res == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null || currentUser == null) return;
            Toast.makeText(this, "⏳ جارٍ رفع الصورة...", Toast.LENGTH_SHORT).show();
            StorageReference ref = FirebaseStorage.getInstance()
                    .getReference("avatars/" + currentUser.getUid() + ".jpg");
            ref.putFile(uri)
                .addOnSuccessListener(snap -> ref.getDownloadUrl().addOnSuccessListener(dlUri -> {
                    currentUser.updateProfile(new UserProfileChangeRequest.Builder()
                            .setPhotoUri(dlUri).build());
                    FirebaseDatabase.getInstance()
                        .getReference("users").child(currentUser.getUid())
                        .child("photoUrl").setValue(dlUri.toString());
                    if (ivAvatar != null) CoilImageLoader.load(this, dlUri.toString(), ivAvatar);
                    Toast.makeText(this, "✅ تم تحديث الصورة", Toast.LENGTH_SHORT).show();
                }))
                .addOnFailureListener(e ->
                    Toast.makeText(this, "❌ " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    // ════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════

    private void applyCachedRole() {
        SharedPreferences p = getSharedPreferences(PREF_PROFILE, MODE_PRIVATE);
        String cached = p.getString(KEY_ROLE, "member");
        if (tvRole != null && !cached.isEmpty()) tvRole.setText(mapRoleToArabic(cached));
        // لا تُظهر cardAdmin من الـ cache — انتظر Firebase
    }

    private void cacheRole(String role) {
        getSharedPreferences(PREF_PROFILE, MODE_PRIVATE).edit()
                .putString(KEY_ROLE, role).apply();
    }

    private String mapRoleToArabic(String r) {
        if (r == null) return "عضو";
        switch (r) {
            case "admin":   return "مدير النظام";
            case "manager": return "مدير";
            default:        return "عضو";
        }
    }

    private void setClick(int id, Runnable a) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(x -> a.run());
    }

    private void showLoginRequiredDialog() {
        new AlertDialog.Builder(this)
            .setTitle("تسجيل الدخول مطلوب")
            .setMessage("يجب تسجيل الدخول للوصول إلى ملفك الشخصي.")
            .setPositiveButton("تسجيل الدخول", (d, w) ->
                startActivity(new Intent(this, LoginActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)))
            .setNegativeButton("إلغاء", (d, w) -> finish())
            .setCancelable(false).show();
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }
}
