package com.missingpersons.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.*;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.*;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.*;
import com.missingpersons.app.R;
import com.missingpersons.app.utils.AnalyticsHelper;
import com.missingpersons.app.utils.CoilImageLoader;
import com.missingpersons.app.utils.SmartSearchRanker;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * SearchFilterActivity — بحث متقدم
 *
 * [مرحلة 3.1] Smart Search Ranking:
 *   ✅ ترتيب النتائج بـ SmartSearchRanker بدلاً من التاريخ فقط
 *   ✅ عرض Smart Score بجانب كل نتيجة
 *   ✅ زر "إعادة ضبط الفلاتر"
 *   ✅ Empty state مخصص حسب السياق
 */
public class SearchFilterActivity extends AppCompatActivity {

    private TextInputEditText etSearch;
    private ChipGroup chipGender, chipAge, chipGovernorate;
    private MaterialButton btnSearch, btnReset;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty, tvCount;

    // موقع المستخدم (يُمرَّر من الـ Intent إن توفر)
    private double userLat = 0, userLng = 0;

    private final List<HashMap<String, Object>> results = new ArrayList<>();
    private ResultAdapter adapter;

    private final String[] governorates = {
        "الكل", "القاهرة", "الإسكندرية", "الجيزة", "القليوبية", "الشرقية",
        "الدقهلية", "البحيرة", "المنوفية", "الغربية", "كفر الشيخ", "دمياط",
        "بورسعيد", "الإسماعيلية", "السويس", "شمال سيناء", "جنوب سيناء",
        "مطروح", "الوادي الجديد", "أسيوط", "سوهاج", "قنا", "الأقصر", "أسوان",
        "المنيا", "بني سويف", "الفيوم", "البحر الأحمر"
    };

    @Override
    protected void attachBaseContext(android.content.Context c) {
        super.attachBaseContext(com.missingpersons.app.utils.LanguageHelper.applyLanguage(c));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_filter);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
            int navBot = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                         v.getPaddingRight(), navBot);
            return insets;
        });

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("🔎 بحث متقدم");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // قراءة موقع المستخدم إذا مُرِّر من النشاط السابق
        userLat = getIntent().getDoubleExtra("userLat", 0);
        userLng = getIntent().getDoubleExtra("userLng", 0);

        initViews();
        setupGovernorateChips();
    }

    private void initViews() {
        etSearch        = findViewById(R.id.et_filter_search);
        chipGender      = findViewById(R.id.chip_group_gender);
        chipAge         = findViewById(R.id.chip_group_age);
        chipGovernorate = findViewById(R.id.chip_group_governorate);
        btnSearch       = findViewById(R.id.btn_filter_search);
        btnReset        = findViewById(R.id.btn_reset_filters);
        recyclerView    = findViewById(R.id.rv_filter_results);
        progressBar     = findViewById(R.id.progress_filter);
        tvEmpty         = findViewById(R.id.tv_filter_empty);
        tvCount         = findViewById(R.id.tv_filter_count);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);
        adapter = new ResultAdapter(results, this::openCase);
        recyclerView.setAdapter(adapter);

        btnSearch.setOnClickListener(v -> performSearch());

        // زر إعادة الضبط
        if (btnReset != null) {
            btnReset.setOnClickListener(v -> resetFilters());
        }
    }

    private void setupGovernorateChips() {
        chipGovernorate.removeAllViews();
        for (String gov : governorates) {
            Chip chip = new Chip(this);
            chip.setText(gov);
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            chipGovernorate.addView(chip);
        }
        if (chipGovernorate.getChildCount() > 0) {
            ((Chip) chipGovernorate.getChildAt(0)).setChecked(true);
        }
    }

    /** إعادة ضبط كل الفلاتر */
    private void resetFilters() {
        if (etSearch != null) etSearch.setText("");

        // إلغاء تحديد Gender
        if (chipGender != null) chipGender.clearCheck();

        // إلغاء تحديد Age
        if (chipAge != null) chipAge.clearCheck();

        // إعادة تحديد "الكل" في المحافظات
        if (chipGovernorate != null && chipGovernorate.getChildCount() > 0) {
            chipGovernorate.clearCheck();
            ((Chip) chipGovernorate.getChildAt(0)).setChecked(true);
        }

        results.clear();
        adapter.notifyDataSetChanged();
        if (tvCount != null) tvCount.setText("");
        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
    }

    private void performSearch() {
        String query = etSearch.getText() != null
            ? etSearch.getText().toString().trim().toLowerCase() : "";

        // Gender filter
        String genderFilter = null;
        int genderId = chipGender.getCheckedChipId();
        if (genderId == R.id.chip_filter_male)        genderFilter = "ذكر";
        else if (genderId == R.id.chip_filter_female) genderFilter = "أنثى";

        // Age filter
        int ageMin = 0, ageMax = 200;
        int ageId = chipAge.getCheckedChipId();
        if (ageId == R.id.chip_age_child)       { ageMin = 0;  ageMax = 12;  }
        else if (ageId == R.id.chip_age_teen)   { ageMin = 13; ageMax = 17;  }
        else if (ageId == R.id.chip_age_young)  { ageMin = 18; ageMax = 30;  }
        else if (ageId == R.id.chip_age_adult)  { ageMin = 31; ageMax = 50;  }
        else if (ageId == R.id.chip_age_old)    { ageMin = 51; ageMax = 200; }

        // Governorate filter
        String governorateFilter = null;
        int govId = chipGovernorate.getCheckedChipId();
        if (govId != View.NO_ID) {
            Chip selectedChip = chipGovernorate.findViewById(govId);
            if (selectedChip != null && !selectedChip.getText().toString().equals("الكل")) {
                governorateFilter = selectedChip.getText().toString();
            }
        }

        AnalyticsHelper.logSearchWithFilters(genderFilter,
            ageMin + "-" + ageMax, query);

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        results.clear();
        adapter.notifyDataSetChanged();

        final String finalGender      = genderFilter;
        final int    fAgeMin          = ageMin;
        final int    fAgeMax          = ageMax;
        final String finalGovernorate = governorateFilter;
        final String finalQuery       = query;

        FirebaseDatabase.getInstance().getReference("reports")
            .orderByChild("approved").equalTo(true)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    List<HashMap<String, Object>> raw = new ArrayList<>();

                    for (DataSnapshot child : snapshot.getChildren()) {
                        // Gender filter
                        if (finalGender != null) {
                            String g = child.child("personGender").getValue(String.class);
                            if (!finalGender.equals(g)) continue;
                        }

                        // Age filter
                        Long age = child.child("personAge").getValue(Long.class);
                        if (age != null && (age < fAgeMin || age > fAgeMax)) continue;

                        // Governorate filter
                        if (finalGovernorate != null) {
                            String gov = child.child("governorate").getValue(String.class);
                            if (gov == null || !gov.equals(finalGovernorate)) continue;
                        }

                        // Text search
                        if (!finalQuery.isEmpty()) {
                            String name = child.child("personName").getValue(String.class);
                            String addr = child.child("manualAddress").getValue(String.class);
                            String loc  = child.child("locationText").getValue(String.class);
                            boolean match =
                                (name != null && name.toLowerCase().contains(finalQuery))
                                || (addr != null && addr.toLowerCase().contains(finalQuery))
                                || (loc  != null && loc.toLowerCase().contains(finalQuery));
                            if (!match) continue;
                        }

                        HashMap<String, Object> map = new HashMap<>();
                        for (DataSnapshot f : child.getChildren())
                            map.put(f.getKey(), f.getValue());
                        map.put("reportId", child.getKey());
                        raw.add(map);
                    }

                    // ══ [مرحلة 3.1] ترتيب ذكي بدلاً من التاريخ فقط ══
                    List<HashMap<String, Object>> ranked =
                        SmartSearchRanker.rank(raw, userLat, userLng, finalQuery);

                    results.clear();
                    results.addAll(ranked);

                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    if (tvCount != null) {
                        tvCount.setText("النتائج: " + results.size()
                            + (userLat != 0 ? " • مرتبة بالقرب" : " • مرتبة بالأحدث"));
                    }
                    // Empty state مخصص
                    if (tvEmpty != null) {
                        if (results.isEmpty()) {
                            boolean hasFilters = finalGender != null
                                || finalGovernorate != null
                                || !finalQuery.isEmpty();
                            tvEmpty.setText(hasFilters
                                ? "لا نتائج بهذه الفلاتر — جرّب تغييرها"
                                : "لا توجد بلاغات حالياً");
                            tvEmpty.setVisibility(View.VISIBLE);
                        } else {
                            tvEmpty.setVisibility(View.GONE);
                        }
                    }
                    adapter.notifyDataSetChanged();
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                }
            });
    }

    private void openCase(HashMap<String, Object> r) {
        Intent intent = new Intent(this, CaseDetailActivity.class);
        intent.putExtra("reportId", (String) r.get("reportId"));
        startActivity(intent);
    }

    // ════════════════════════════════════════════════════════════════════

    static class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.VH> {
        private final List<HashMap<String, Object>> items;
        private final OnClick listener;
        interface OnClick { void onClick(HashMap<String, Object> r); }

        ResultAdapter(List<HashMap<String, Object>> items, OnClick l) {
            this.items = items;
            this.listener = l;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_case_card, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            HashMap<String, Object> r = items.get(pos);
            h.tvName.setText(r.getOrDefault("personName", "غير محدد") + "");
            h.tvAddr.setText("📍 " + r.getOrDefault("manualAddress", ""));

            // [مرحلة 3.1] عرض Smart Score
            Object scoreObj = r.get("_smartScore");
            if (h.tvScore != null && scoreObj instanceof Float) {
                int pct = Math.round((Float) scoreObj * 100);
                h.tvScore.setText("⚡ " + pct + "%");
                h.tvScore.setVisibility(View.VISIBLE);
            } else if (h.tvScore != null) {
                h.tvScore.setVisibility(View.GONE);
            }

            Object urlsObj = r.get("imageUrls");
            if (urlsObj instanceof List && !((List<?>) urlsObj).isEmpty()) {
                CoilImageLoader.loadRounded(h.ivPhoto.getContext(),
                    ((List<?>) urlsObj).get(0).toString(), h.ivPhoto,
                    R.drawable.ic_face_placeholder, 12f);
            }

            h.itemView.setOnClickListener(v -> listener.onClick(r));
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            ImageView ivPhoto;
            TextView tvName, tvAddr, tvScore;
            VH(@NonNull View v) {
                super(v);
                ivPhoto  = v.findViewById(R.id.iv_case_photo);
                tvName   = v.findViewById(R.id.tv_case_name);
                tvAddr   = v.findViewById(R.id.tv_case_addr);
                tvScore  = v.findViewById(R.id.tv_smart_score); // قد لا يكون موجوداً
            }
        }
    }

    @Override public boolean onSupportNavigateUp() { onBackPressed(); return true; }
}
