package com.missingpersons.app.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.core.view.WindowCompat;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.missingpersons.app.R;
import com.missingpersons.app.utils.LanguageHelper;

/**
 * SplashActivity
 *
 * [FIX-4] إضافة user.reload() للتحقق من صلاحية الـ token قبل توجيه المستخدم للـ Home
 *         بدونها: يذهب لـ HomeActivity بـ token منتهي → ترفض Firebase الطلبات صامتةً
 */
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY = 2500;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LanguageHelper.applyLanguage(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        new Handler(Looper.getMainLooper()).postDelayed(this::navigate, SPLASH_DELAY);
    }

    private void navigate() {
        // تحقق من الـ Onboarding أولاً
        SharedPreferences prefs = getSharedPreferences(
            OnboardingActivity.PREF_NAME, MODE_PRIVATE);
        boolean onboardingDone = prefs.getBoolean(
            OnboardingActivity.KEY_ONBOARDING_DONE, false);

        if (!onboardingDone) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }

        // التوجيه حسب حالة المستخدم
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            goToLogin();
            return;
        }

        // [FIX-4] التحقق من صلاحية الـ token قبل التوجيه للـ Home
        // getCurrentUser() قد يعيد مستخدماً بـ token منتهي (cached locally)
        // reload() يتحقق من Firebase مباشرة
        user.reload().addOnCompleteListener(task -> {
            if (isFinishing() || isDestroyed()) return;

            if (task.isSuccessful()) {
                // Token صالح → اذهب للـ Home
                goToHome();
            } else {
                // Token منتهي أو الحساب محذوف → اطلب تسجيل دخول جديد
                FirebaseAuth.getInstance().signOut();
                goToLogin();
            }
        });
    }

    private void goToHome() {
        startActivity(new Intent(this, NewHomeActivity.class));
        finish();
    }

    private void goToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
