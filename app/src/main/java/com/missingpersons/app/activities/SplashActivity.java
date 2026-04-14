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
        // ─── تحقق من الـ Onboarding أولاً ───
        SharedPreferences prefs = getSharedPreferences(
            OnboardingActivity.PREF_NAME, MODE_PRIVATE);
        boolean onboardingDone = prefs.getBoolean(
            OnboardingActivity.KEY_ONBOARDING_DONE, false);

        if (!onboardingDone) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }

        // ─── التوجيه حسب حالة المستخدم ───
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        Class<?> destination;
        if (user != null) {
            destination = NewHomeActivity.class;
        } else {
            destination = LoginActivity.class;
        }
        startActivity(new Intent(this, destination));
        finish();
    }
}
