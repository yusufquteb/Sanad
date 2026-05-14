package com.missingpersons.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.missingpersons.app.R;
import com.missingpersons.app.utils.LanguageHelper;

/**
 * SettingsActivity — إعدادات التطبيق.
 */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LanguageHelper.applyLanguage(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.settings_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
