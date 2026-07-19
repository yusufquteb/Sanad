package com.missingpersons.app.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import com.missingpersons.app.R;
import com.missingpersons.app.utils.CoilImageLoader;
import com.missingpersons.app.utils.LanguageHelper;
import com.missingpersons.app.utils.RoleManager;

import java.util.HashMap;
import java.util.Map;

/**
 * SettingsActivity — إعدادات التطبيق
 *
 * Sections:
 *  1. الإشعارات  — master toggle, match notifications, amber alerts
 *  2. التطبيق    — language selector, clear image cache
 *  3. الخصوصية   — privacy policy, terms of use (opens browser)
 *  4. عن التطبيق  — app version, non-profit note, contact us (email)
 *
 * NOTE: Language toggle and master notification switch already exist in
 * ProfileActivity, but are intentionally duplicated here as a convenience
 * so users can reach all settings from one place. The sub-notification
 * toggles (match, amber) and cache-clear are unique to this screen.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String PREF_SETTINGS  = "settings";
    private static final String SUPPORT_EMAIL  = "support@sanad-app.com";

    // SharedPrefs keys
    private static final String KEY_NOTIF_MASTER = "notifications_enabled";
    private static final String KEY_NOTIF_MATCH  = "match_notifications_enabled";
    private static final String KEY_NOTIF_AMBER  = "amber_alerts_enabled";

    // Toggles
    private SwitchCompat switchMasterNotif;
    private SwitchCompat switchMatchNotif;
    private SwitchCompat switchAmberNotif;

    private SharedPreferences prefs;

    // ════════════════════════════════════════════════════
    //  Lifecycle
    // ════════════════════════════════════════════════════

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LanguageHelper.applyLanguage(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
                    int bot = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
                    v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bot);
                    return insets;
                });

        // Set up MaterialToolbar as support action bar
        MaterialToolbar toolbar = findViewById(R.id.toolbar_settings);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.settings_title);
        }

        prefs = getSharedPreferences(PREF_SETTINGS, MODE_PRIVATE);

        initViews();
        bindNotificationToggles();
        bindAppSection();
        bindPrivacySection();
        bindAboutSection();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    // ════════════════════════════════════════════════════
    //  Section 1: Notifications
    // ════════════════════════════════════════════════════

    private void initViews() {
        switchMasterNotif = findViewById(R.id.switch_master_notif);
        switchMatchNotif  = findViewById(R.id.switch_match_notif);
        switchAmberNotif  = findViewById(R.id.switch_amber_notif);
    }

    private void bindNotificationToggles() {
        // ── Master toggle ──
        boolean masterEnabled = prefs.getBoolean(KEY_NOTIF_MASTER, true);
        if (switchMasterNotif != null) {
            switchMasterNotif.setChecked(masterEnabled);
            // Sub-toggles enabled only when master is on
            setSubNotifsEnabled(masterEnabled);

            switchMasterNotif.setOnCheckedChangeListener((btn, isChecked) -> {
                prefs.edit().putBoolean(KEY_NOTIF_MASTER, isChecked).apply();
                setSubNotifsEnabled(isChecked);
            });

            // Tapping the row row also toggles the switch
            View rowMaster = findViewById(R.id.row_master_notif);
            if (rowMaster != null) {
                rowMaster.setOnClickListener(v -> switchMasterNotif.setChecked(!switchMasterNotif.isChecked()));
            }
        }

        // ── Match notifications ──
        if (switchMatchNotif != null) {
            switchMatchNotif.setChecked(prefs.getBoolean(KEY_NOTIF_MATCH, true));
            switchMatchNotif.setOnCheckedChangeListener((btn, isChecked) ->
                    prefs.edit().putBoolean(KEY_NOTIF_MATCH, isChecked).apply());

            View rowMatch = findViewById(R.id.row_match_notif);
            if (rowMatch != null) {
                rowMatch.setOnClickListener(v -> {
                    if (switchMatchNotif.isEnabled()) {
                        switchMatchNotif.setChecked(!switchMatchNotif.isChecked());
                    }
                });
            }
        }

        // ── Amber alerts ──
        if (switchAmberNotif != null) {
            switchAmberNotif.setChecked(prefs.getBoolean(KEY_NOTIF_AMBER, true));
            switchAmberNotif.setOnCheckedChangeListener((btn, isChecked) ->
                    prefs.edit().putBoolean(KEY_NOTIF_AMBER, isChecked).apply());

            View rowAmber = findViewById(R.id.row_amber_notif);
            if (rowAmber != null) {
                rowAmber.setOnClickListener(v -> {
                    if (switchAmberNotif.isEnabled()) {
                        switchAmberNotif.setChecked(!switchAmberNotif.isChecked());
                    }
                });
            }
        }
    }

    /** Enable/disable sub-toggles based on master switch state */
    private void setSubNotifsEnabled(boolean enabled) {
        if (switchMatchNotif != null) {
            switchMatchNotif.setEnabled(enabled);
            switchMatchNotif.setAlpha(enabled ? 1.0f : 0.4f);
        }
        if (switchAmberNotif != null) {
            switchAmberNotif.setEnabled(enabled);
            switchAmberNotif.setAlpha(enabled ? 1.0f : 0.4f);
        }
    }

    // ════════════════════════════════════════════════════
    //  Section 2: App
    // ════════════════════════════════════════════════════

    private void bindAppSection() {
        // Language
        setClick(R.id.row_language, this::showLanguageDialog);

        // Clear cache
        setClick(R.id.row_clear_cache, this::clearImageCache);
    }

    private void showLanguageDialog() {
        String currentLang = LanguageHelper.getLanguage(this);
        boolean isArabic   = LanguageHelper.ARABIC.equals(currentLang);

        String[] options  = {"🇸🇦 العربية", "🇺🇸 English"};
        int checkedItem   = isArabic ? 0 : 1;

        new AlertDialog.Builder(this)
                .setTitle("اختر اللغة / Choose Language")
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    dialog.dismiss();
                    String newLang = (which == 0) ? LanguageHelper.ARABIC : LanguageHelper.ENGLISH;
                    if (!newLang.equals(currentLang)) {
                        LanguageHelper.setLanguage(this, newLang);
                        // LanguageHelper.setLanguage() calls finish() + startActivity() internally,
                        // so no further action needed here.
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void clearImageCache() {
        try {
            CoilImageLoader.clearCache(this);
            Toast.makeText(this, R.string.settings_clear_cache_done, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            // Fallback: cache clear failed silently — still show confirmation to user
            Toast.makeText(this, R.string.settings_clear_cache_done, Toast.LENGTH_SHORT).show();
        }
    }

    // ════════════════════════════════════════════════════
    //  Section 3: Privacy
    // ════════════════════════════════════════════════════

    private void bindPrivacySection() {
        setClick(R.id.row_privacy_policy,  () -> openUrl(getString(R.string.privacy_policy_url)));
        setClick(R.id.row_terms,           () -> openUrl(getString(R.string.terms_url)));
        setClick(R.id.row_delete_account,  this::confirmDeleteAccount);
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, url, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * حذف الحساب والبيانات — مطلوب من Google Play (User Data policy:
     * Account deletion) لأي تطبيق يتيح إنشاء حساب. يسجّل طلب الحذف في
     * Firebase (يعالجه فريق الإدارة يدوياً/عبر Cloud Function لاحقاً
     * لضمان حذف آمن للبلاغات والمطابقات المرتبطة)، ثم يسجّل خروج
     * المستخدم فوراً.
     */
    private void confirmDeleteAccount() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            openEmailContact();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle(R.string.settings_delete_account_title)
            .setMessage(R.string.settings_delete_account_message)
            .setPositiveButton(R.string.settings_delete_account_confirm, (d, w) -> requestAccountDeletion())
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void requestAccountDeletion() {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
            ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;

        Map<String, Object> update = new HashMap<>();
        update.put("deletionRequested",   true);
        update.put("deletionRequestedAt", System.currentTimeMillis());

        FirebaseDatabase.getInstance().getReference("users").child(uid)
            .updateChildren(update)
            .addOnSuccessListener(v -> {
                Toast.makeText(this, R.string.settings_delete_account_sent, Toast.LENGTH_LONG).show();
                RoleManager.reset();
                FirebaseAuth.getInstance().signOut();
                startActivity(new Intent(this, LoginActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                finish();
            })
            .addOnFailureListener(e ->
                Toast.makeText(this, R.string.settings_delete_account_error, Toast.LENGTH_LONG).show());
    }

    // ════════════════════════════════════════════════════
    //  Section 4: About
    // ════════════════════════════════════════════════════

    private void bindAboutSection() {
        android.widget.TextView tvVersion = findViewById(R.id.tv_app_version);
        if (tvVersion != null) {
            tvVersion.setText(getString(R.string.version_full));
        }
        setClick(R.id.row_contact, this::openEmailContact);
    }

    private void openEmailContact() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + SUPPORT_EMAIL));
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.settings_contact)));
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, SUPPORT_EMAIL, Toast.LENGTH_LONG).show();
        }
    }

    // ════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════

    private void setClick(int id, Runnable action) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(x -> action.run());
    }
}
