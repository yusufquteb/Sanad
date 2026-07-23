package com.missingpersons.app.activities;


import com.missingpersons.app.utils.RoleManager;
import com.missingpersons.app.utils.GovernorateManager;
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
 * [FIX-1] إضافة null check على idToken قبل إرساله للـ ViewModel
 * [FIX-2] إصلاح double finish() في navigateToHome() — كان يُغلق التطبيق
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

        viewModel = new ViewModelProvider(this, new AppViewModelFactory(this))
                .get(AuthViewModel.class);

        observeViewModel();
        viewModel.checkInitialState();
        initViews();
        setupGoogleSignIn();
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

                // [FIX-1] null check على idToken — يمنع NullPointerException الصامت
                // يحدث عند انقطاع الإنترنت أو خطأ في google-services.json
                String idToken = account.getIdToken();
                if (idToken == null || idToken.isEmpty()) {
                    showLoading(false);
                    Toast.makeText(this,
                        "فشل الحصول على رمز Google — تحقق من الإنترنت وأعد المحاولة",
                        Toast.LENGTH_LONG).show();
                    return;
                }

                viewModel.signInWithGoogle(idToken);

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
            .setPositiveButton("تصفح كزائر", (d, w) -> viewModel.signInAsGuest())
            .setNegativeButton("تسجيل الدخول", null)
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
        // منع الاستدعاء المتكرر إذا كانت الـ Activity في حالة إنهاء
        if (isFinishing() || isDestroyed()) return;

        RoleManager.reset();

        // [FIX-2] إصلاح double finish() — كانت النسخة القديمة تستدعي finish() مرتين:
        //   مرة داخل كل branch، ومرة أخرى بعد الـ if/else مباشرة → crash فوري
        if (!GovernorateManager.isSetupDone(this)) {
            GovernorateManager.showSetupDialog(this, () -> {
                startActivity(new Intent(this, NewHomeActivity.class));
                finish(); // finish مرة واحدة فقط
            });
        } else {
            startActivity(new Intent(this, NewHomeActivity.class));
            finish(); // finish مرة واحدة فقط
        }
        // ❌ السطر المحذوف: finish(); — كان يتسبب بـ double finish = IllegalStateException
    }
}
