package com.missingpersons.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.Observer;

import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.api.ApiException;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import com.missingpersons.app.R;
import com.missingpersons.app.ui.auth.AuthViewModel;
import com.missingpersons.app.ui.common.AppViewModelFactory;

/**
 * LoginActivity — شاشة تسجيل الدخول
 *
 * MVVM:
 *   - AuthViewModel يدير: Firebase Auth، التحقق من البان، حالة التحميل
 *   - Activity تفعل فقط: Google Sign-In client، observe AuthState، تحديث الـ UI
 *
 * ما نُقل للـ ViewModel:
 *   ✅ Firebase signInWithCredential
 *   ✅ Anonymous sign-in
 *   ✅ التحقق من البان
 *   ✅ حفظ بيانات المستخدم في Firebase DB
 *
 * ما بقي في Activity:
 *   - GoogleSignInClient (يحتاج Context + onActivityResult)
 *   - Legal Warning dialog (UI فقط)
 *   - Navigation بعد النجاح
 */
public class LoginActivity extends AppCompatActivity {

    private static final int RC_SIGN_IN = 100;
    public  static final String WEB_CLIENT_ID =
        "891319690324-9c2a8lnnr0pkbtjbn2lvcjtnetun9nlj.apps.googleusercontent.com";

    private AuthViewModel         viewModel;
    private GoogleSignInClient    googleSignInClient;
    private MaterialButton        btnGoogleSignIn;
    private MaterialButton        btnGuest;
    private CircularProgressIndicator progressBar;

    @Override
    protected void attachBaseContext(android.content.Context c) {
        super.attachBaseContext(com.missingpersons.app.utils.LanguageHelper.applyLanguage(c));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_v2);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
            int navBot = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                         v.getPaddingRight(), navBot);
            return insets;
        });

        // ── ViewModel ──────────────────────────────────────
        viewModel = new ViewModelProvider(this, new AppViewModelFactory(this))
                .get(AuthViewModel.class);

        observeViewModel();

        // إذا كان مسجلاً مسبقاً — ViewModel يتحقق ويوجّه
        viewModel.checkInitialState();

        initViews();
        setupGoogleSignIn();
        showLegalWarning();
    }

    // ════════════════════════════════════════════════════════
    //  ViewModel Observers
    // ════════════════════════════════════════════════════════

    private void observeViewModel() {

        viewModel.getAuthState().observe(this, state -> {
            switch (state) {
                case LOADING:
                    showLoading(true);
                    break;

                case SUCCESS:
                    showLoading(false);
                    navigateToHome();
                    break;

                case BANNED:
                    showLoading(false);
                    showBannedDialog();
                    break;

                case ERROR:
                    showLoading(false);
                    // الخطأ يُعرض عبر observer التالي
                    break;

                case IDLE:
                default:
                    showLoading(false);
                    break;
            }
        });

        viewModel.getErrorMsg().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ════════════════════════════════════════════════════════
    //  Views & Google Sign-In
    // ════════════════════════════════════════════════════════

    private void initViews() {
        progressBar     = findViewById(R.id.progress_bar);
        btnGoogleSignIn = findViewById(R.id.btn_google_signin);
        btnGuest        = findViewById(R.id.btn_guest);

        btnGoogleSignIn.setOnClickListener(v -> startGoogleSignIn());
        btnGuest.setOnClickListener(v -> showGuestDialog());

        android.widget.TextView tvDisclaimer = findViewById(R.id.tv_disclaimer_link);
        if (tvDisclaimer != null)
            tvDisclaimer.setOnClickListener(v ->
                startActivity(new Intent(this, DisclaimerActivity.class)));
    }

    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions
                .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(WEB_CLIENT_ID)
                .requestEmail()
                .requestProfile()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    private void startGoogleSignIn() {
        showLoading(true);
        googleSignInClient.signOut().addOnCompleteListener(t ->
            startActivityForResult(googleSignInClient.getSignInIntent(), RC_SIGN_IN));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            try {
                GoogleSignInAccount account = GoogleSignIn
                    .getSignedInAccountFromIntent(data)
                    .getResult(ApiException.class);
                // تمرير idToken للـ ViewModel بدل استدعاء Firebase مباشرة
                viewModel.signInWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                showLoading(false);
                Toast.makeText(this,
                    viewModel.resolveGoogleError(e.getStatusCode()),
                    Toast.LENGTH_LONG).show();
            }
        }
    }

    // ════════════════════════════════════════════════════════
    //  Guest Dialog
    // ════════════════════════════════════════════════════════

    private void showGuestDialog() {
        new AlertDialog.Builder(this)
            .setTitle("🚶 الدخول كزائر")
            .setMessage("يمكنك كزائر:\n✅ تصفح بلاغات المفقودين\n✅ عرض الخريطة\n\n"
                      + "يتطلب تسجيل الدخول:\n🔒 البحث بالذكاء الاصطناعي\n"
                      + "🔒 إضافة بلاغ جديد\n🔒 التواصل مع الأسر")
            .setPositiveButton("تصفح كزائر", (d, w) ->
                viewModel.signInAsGuest())          // ← ViewModel بدل Firebase مباشرة
            .setNegativeButton("تسجيل الدخول", null)
            .show();
    }

    // ════════════════════════════════════════════════════════
    //  Legal Warning
    // ════════════════════════════════════════════════════════

    private void showLegalWarning() {
        new AlertDialog.Builder(this)
            .setTitle("⚠️ شروط الاستخدام")
            .setMessage("هذا التطبيق منصة خدمية مجانية للبحث عن المفقودين.\n\n"
                      + "بالاستخدام تقر بـ:\n"
                      + "• عدم دفع أي مبالغ مالية\n"
                      + "• الإبلاغ عن أي ابتزاز للشرطة فوراً\n"
                      + "• أن التطبيق غير مسؤول قانونياً عن سوء الاستخدام")
            .setCancelable(false)
            .setPositiveButton("أوافق وأتابع", null)
            .setNeutralButton("📋 الحقوق والشروط",
                (d, w) -> startActivity(new Intent(this, DisclaimerActivity.class)))
            .setNegativeButton("خروج", (d, w) -> finish())
            .show();
    }

    // ════════════════════════════════════════════════════════
    //  Banned Dialog
    // ════════════════════════════════════════════════════════

    private void showBannedDialog() {
        new AlertDialog.Builder(this)
            .setTitle("حساب محظور")
            .setMessage("تم حظر حسابك.\nللاستفسار: albaramost@gmail.com")
            .setPositiveButton("حسناً", (d, w) -> finish())
            .setCancelable(false)
            .show();
    }

    // ════════════════════════════════════════════════════════
    //  UI Helpers
    // ════════════════════════════════════════════════════════

    private void showLoading(boolean show) {
        if (progressBar != null)
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (btnGoogleSignIn != null) btnGoogleSignIn.setEnabled(!show);
        if (btnGuest != null)        btnGuest.setEnabled(!show);
    }

    private void navigateToHome() {
        startActivity(new Intent(this, NewHomeActivity.class));
        finish();
    }
}
