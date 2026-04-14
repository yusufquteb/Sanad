package com.missingpersons.app.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.missingpersons.app.R;

public class OnboardingActivity extends AppCompatActivity {

    public static final String PREF_NAME = "app_prefs";
    public static final String KEY_ONBOARDING_DONE = "onboarding_done";

    private ViewPager2 viewPager;
    private TabLayout tabIndicator;
    private MaterialButton btnNext, btnSkip;

    private static final int[][] PAGES = {
        {R.drawable.ic_search_person, R.string.onboard_title_1, R.string.onboard_desc_1},
        {R.drawable.ic_camera,        R.string.onboard_title_2, R.string.onboard_desc_2},
        {R.drawable.ic_found,         R.string.onboard_title_3, R.string.onboard_desc_3},
        // [Phase 4.4] شريحة الذكاء الاصطناعي
        {R.drawable.ic_result,        R.string.onboard_title_4, R.string.onboard_desc_4},
    };

    @Override
    protected void attachBaseContext(android.content.Context c) {
        super.attachBaseContext(com.missingpersons.app.utils.LanguageHelper.applyLanguage(c));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── الـ FLAG_LAYOUT_NO_LIMITS يُضبط قبل setContentView ──
        getWindow().setFlags(
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        setContentView(R.layout.activity_onboarding);

        // ── إخفاء شريط التنقل — بعد setContentView حتى يتوفر DecorView ──
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController ctrl = getWindow().getInsetsController();
            if (ctrl != null) {
                ctrl.hide(android.view.WindowInsets.Type.navigationBars());
                ctrl.setSystemBarsBehavior(
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            //noinspection deprecation
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        viewPager    = findViewById(R.id.vp_onboarding);
        tabIndicator = findViewById(R.id.tab_indicator);
        btnNext      = findViewById(R.id.btn_next);
        btnSkip      = findViewById(R.id.btn_skip);

        viewPager.setAdapter(new OnboardingAdapter());

        new TabLayoutMediator(tabIndicator, viewPager, (tab, pos) -> {}).attach();

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int pos) {
                if (pos == PAGES.length - 1) {
                    btnNext.setText("ابدأ الآن");
                    btnSkip.setVisibility(View.INVISIBLE);
                } else {
                    btnNext.setText("التالي");
                    btnSkip.setVisibility(View.VISIBLE);
                }
            }
        });

        btnNext.setOnClickListener(v -> {
            int current = viewPager.getCurrentItem();
            if (current < PAGES.length - 1) {
                viewPager.setCurrentItem(current + 1);
            } else {
                finishOnboarding();
            }
        });

        btnSkip.setOnClickListener(v -> finishOnboarding());
    }

    private void finishOnboarding() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    // ═══════════════════════════════════════
    //  ADAPTER
    // ═══════════════════════════════════════
    class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.VH> {

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_onboarding_page, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            h.ivIcon.setImageResource(PAGES[pos][0]);
            h.tvTitle.setText(PAGES[pos][1]);
            h.tvDesc.setText(PAGES[pos][2]);
        }

        @Override public int getItemCount() { return PAGES.length; }

        class VH extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvTitle, tvDesc;
            VH(@NonNull View v) {
                super(v);
                ivIcon  = v.findViewById(R.id.iv_onboard_icon);
                tvTitle = v.findViewById(R.id.tv_onboard_title);
                tvDesc  = v.findViewById(R.id.tv_onboard_desc);
            }
        }
    }
}
