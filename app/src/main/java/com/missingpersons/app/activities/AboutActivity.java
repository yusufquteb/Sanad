package com.missingpersons.app.activities;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.missingpersons.app.R;
import com.missingpersons.app.utils.LanguageHelper;

/**
 * AboutActivity — عن التطبيق
 *
 * [إصلاح] أُعيد تصميم الشاشة لتكون شبيهة بالشروط والأحكام:
 * بطاقات Material3 منظمة بدل نص عادي طويل غير منسّق.
 * المحتوى ثابت في activity_about.xml.
 */
public class AboutActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LanguageHelper.applyLanguage(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bot = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), bot);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar_about);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("عن التطبيق");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        TextView tvVersion = findViewById(R.id.tv_about_version);
        if (tvVersion != null) {
            tvVersion.setText("الإصدار " + getString(R.string.app_version_name) + " — 2025");
        }
    }

    @Override
    public boolean onSupportNavigateUp() { onBackPressed(); return true; }
}
