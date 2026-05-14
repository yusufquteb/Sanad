package com.missingpersons.app.activities;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.missingpersons.app.utils.LanguageHelper;

/**
 * EnhancedAdminDashboardActivity — لوحة تحكم الإدارة المحسّنة.
 * تفويض جميع المنطق إلى AdminDashboardActivity الحالية حتى يكتمل التطوير.
 */
public class EnhancedAdminDashboardActivity extends AdminDashboardActivity {

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LanguageHelper.applyLanguage(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}
