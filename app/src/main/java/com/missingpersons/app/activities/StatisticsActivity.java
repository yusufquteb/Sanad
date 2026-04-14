package com.missingpersons.app.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.database.*;
import com.missingpersons.app.R;
import com.missingpersons.app.utils.LanguageHelper;
import com.missingpersons.app.utils.StatsCache;

import java.util.*;
import android.view.Gravity;

/**
 * StatisticsActivity — إحصائيات التطبيق
 *
 * يعرض:
 * - إجمالي البلاغات
 * - الحالات المحلولة هذا الشهر
 * - التوزيع حسب الجنس
 * - التوزيع حسب المحافظة (أعلى 5)
 * - التوزيع حسب الفئة العمرية
 * - إجمالي التطابقات الناجحة
 */
public class StatisticsActivity extends AppCompatActivity {

    private static final String TAG = "StatisticsActivity";

    // ─── كروت الإحصائيات ──────────────────────────────
    private TextView tvTotalReports, tvResolvedThisMonth, tvTotalMatches,
                     tvMaleCount, tvFemaleCount, tvPendingCount;
    private LinearLayout layoutGovStats, layoutAgeStats;
    private ProgressBar  progressStats;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LanguageHelper.applyLanguage(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        // FIX #12: Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bot = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(0, top, 0, bot);
            return insets;
        });

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("📊 إحصائيات التطبيق");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        bindViews();
        loadStatistics();
        initCharts();
    }

    private void bindViews() {
        tvTotalReports     = findViewById(R.id.tv_stat_total_reports);
        tvResolvedThisMonth = findViewById(R.id.tv_stat_resolved_month);
        tvTotalMatches     = findViewById(R.id.tv_stat_total_matches);
        tvMaleCount        = findViewById(R.id.tv_stat_male);
        tvFemaleCount      = findViewById(R.id.tv_stat_female);
        tvPendingCount     = findViewById(R.id.tv_stat_pending);
        layoutGovStats     = findViewById(R.id.layout_gov_stats);
        layoutAgeStats     = findViewById(R.id.layout_age_stats);
        progressStats      = findViewById(R.id.progress_stats);
    }

    // ─── تحميل البيانات — FIX: يقرأ من 3 nodes ───────────────

    private void loadStatistics() {
        if (progressStats != null) progressStats.setVisibility(View.VISIBLE);

        // [إصلاح إجمالي البلاغات] نُمرّر اسم الـ node مع كل snapshot
        // حتى تعدّ processAllData() total من reports فقط
        String[] nodes = {"reports", "found_persons", "sightings"};
        int[] remaining = {nodes.length};
        // قائمتان: الأولى من reports فقط (للعدد الإجمالي)، الثانية كل شيء (للتحليل)
        List<DataSnapshot> reportsSnapshots    = new ArrayList<>();
        List<DataSnapshot> allSnapshots        = new ArrayList<>();

        for (String node : nodes) {
            FirebaseDatabase.getInstance().getReference(node)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snap) {
                        for (DataSnapshot c : snap.getChildren()) {
                            allSnapshots.add(c);
                            if ("reports".equals(node)) reportsSnapshots.add(c);
                        }
                        remaining[0]--;
                        if (remaining[0] == 0) processAllData(allSnapshots, reportsSnapshots);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {
                        remaining[0]--;
                        if (remaining[0] == 0) processAllData(allSnapshots, reportsSnapshots);
                    }
                });
        }

        // إجمالي التطابقات
        FirebaseDatabase.getInstance().getReference("matches")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    if (tvTotalMatches != null)
                        tvTotalMatches.setText(String.valueOf(snap.getChildrenCount()));
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
    }

    // ─── معالجة البيانات المدمجة من كل الـ nodes ──────────────

    private void processAllData(List<DataSnapshot> snapshots, List<DataSnapshot> reportsOnly) {
        long total = 0, resolvedTotal = 0, resolvedThisMonth = 0,
             pending = 0, approved = 0, male = 0, female = 0;
        Map<String, Integer> govMap = new HashMap<>();
        Map<String, Integer> ageMap = new LinkedHashMap<>();

        ageMap.put("رضيع (0-2)",   0);
        ageMap.put("طفل (3-12)",   0);
        ageMap.put("مراهق (13-17)", 0);
        ageMap.put("شاب (18-30)",  0);
        ageMap.put("بالغ (31-50)", 0);
        ageMap.put("كبير (50+)",   0);

        long thisMonthStart = getThisMonthStart();

        // total = عدد بلاغات reports فقط (لا found_persons ولا sightings)
        total = reportsOnly.size();

        for (DataSnapshot c : snapshots) {
            // لا نزيد total هنا — حُسب مسبقاً من reportsOnly
            String status      = safeStr(c, "status");
            String governorate = safeStr(c, "governorate");
            String ageRange    = safeStr(c, "ageRange");
            String gender = safeStr(c, "personGender");
            if (gender.isEmpty()) gender = safeStr(c, "gender");
            Long   timestamp   = c.child("timestamp").getValue(Long.class);

            // FIX #1 + #10: تعريف موحد — نشطة = status=="approved" && approved==true
            Boolean approvedFlag = c.child("approved").getValue(Boolean.class);
            boolean isActive = "approved".equals(status) && Boolean.TRUE.equals(approvedFlag);

            if ("resolved".equals(status)) {
                resolvedTotal++;
                if (timestamp != null && timestamp >= thisMonthStart) resolvedThisMonth++;
            }
            if (isActive) approved++;
            if ("pending".equals(status) || "pending_edit".equals(status)) pending++;

            // FIX: تحقق من الاثنين — عربي وإنجليزي
            boolean isMale   = "ذكر".equals(gender)  || "male".equals(gender);
            boolean isFemale = "أنثى".equals(gender) || "female".equals(gender);
            if (isMale)   male++;
            if (isFemale) female++;

            if (!governorate.isEmpty())
                govMap.put(governorate, govMap.getOrDefault(governorate, 0) + 1);

            if (ageMap.containsKey(ageRange))
                ageMap.put(ageRange, ageMap.get(ageRange) + 1);
        }

        final long fTotal         = total;
        final long fResolved      = resolvedTotal;
        final long fResolvedMonth = resolvedThisMonth;
        final long fPending       = pending;
        final long fApproved      = approved;
        final long fMale          = male;
        final long fFemale        = female;
        final Map<String, Integer> fGovMap = govMap;
        final Map<String, Integer> fAgeMap = ageMap;

        runOnUiThread(() -> {
            if (progressStats != null) progressStats.setVisibility(View.GONE);
            if (tvTotalReports      != null) tvTotalReports.setText(String.valueOf(fTotal));
            if (tvResolvedThisMonth != null) tvResolvedThisMonth.setText(String.valueOf(fResolvedMonth));
            if (tvPendingCount      != null) tvPendingCount.setText(String.valueOf(fPending));
            if (tvMaleCount         != null) tvMaleCount.setText(String.valueOf(fMale));
            if (tvFemaleCount       != null) tvFemaleCount.setText(String.valueOf(fFemale));
            buildGovStats(fGovMap, fTotal);
            buildAgeStats(fAgeMap, fTotal);
        });

        // ✅ Fix#2: مزامنة stats/ cache — نفس الأرقام التي حسبناها نكتبها مباشرة
        // بدل إعادة قراءة Firebase من البداية (StatsCache.rebuild)، نستخدم ما عندنا
        // هكذا NewHomeActivity live listener يستقبل التحديث فوراً
        java.util.Map<String, Object> cacheUpdate = new java.util.HashMap<>();
        cacheUpdate.put("total",         fTotal);
        cacheUpdate.put("approved",      fApproved);
        cacheUpdate.put("resolved",      fResolved);
        cacheUpdate.put("pending",       fPending);
        cacheUpdate.put("resolvedMonth", fResolvedMonth);
        cacheUpdate.put("male",          fMale);
        cacheUpdate.put("female",        fFemale);
        cacheUpdate.put("lastUpdated",   System.currentTimeMillis());
        FirebaseDatabase.getInstance().getReference("stats").updateChildren(cacheUpdate);
    }

    // ─── خريطة حرارية للمحافظات ──────────────────────

    /** ألوان الحرارة: كثافة البلاغات من أخضر → أصفر → برتقالي → أحمر */
    private int heatColor(int count, int maxCount) {
        if (maxCount == 0) return 0xFF4CAF50;
        float ratio = (float) count / maxCount;
        if (ratio < 0.25f) return 0xFF4CAF50;   // أخضر  — منخفض
        if (ratio < 0.50f) return 0xFFFFC107;   // أصفر  — متوسط
        if (ratio < 0.75f) return 0xFFFF5722;   // برتقالي — مرتفع
        return 0xFFF44336;                        // أحمر  — مرتفع جداً
    }

    private void buildGovStats(Map<String, Integer> govMap, long total) {
        if (layoutGovStats == null) return;
        layoutGovStats.removeAllViews();

        // ترتيب تنازلي
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(govMap.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        int maxCount = sorted.isEmpty() ? 1 : sorted.get(0).getValue();

        // ── عنوان + مفتاح الألوان ─────────────────────────────────
        addHeatLegend(layoutGovStats);

        // ── شبكة الخريطة الحرارية (grid بـ 2 عمودين) ────────────────
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        grid.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout col1 = new LinearLayout(this);
        col1.setOrientation(LinearLayout.VERTICAL);
        col1.setLayoutParams(new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout col2 = new LinearLayout(this);
        col2.setOrientation(LinearLayout.VERTICAL);
        col2.setLayoutParams(new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Integer> e = sorted.get(i);
            LinearLayout target = (i % 2 == 0) ? col1 : col2;
            addHeatCell(target, e.getKey(), e.getValue(), maxCount, (int) total);
        }

        grid.addView(col1);
        grid.addView(col2);
        layoutGovStats.addView(grid);

        // ── أيضاً: شريط تقدم للأعلى 3 ──────────────────────────────
        if (sorted.size() > 0) {
            TextView tvTop = new TextView(this);
            tvTop.setText("أعلى المحافظات");
            tvTop.setTextSize(14f);
            tvTop.setTypeface(null, android.graphics.Typeface.BOLD);
            tvTop.setPadding(0, 20, 0, 8);
            layoutGovStats.addView(tvTop);

            int limit = Math.min(sorted.size(), 5);
            for (int i = 0; i < limit; i++) {
                Map.Entry<String, Integer> e = sorted.get(i);
                addProgressRow(layoutGovStats, e.getKey(), e.getValue(),
                    (int) total, heatColor(e.getValue(), maxCount));
            }
        }
    }

    private void addHeatLegend(LinearLayout parent) {
        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setPadding(0, 0, 0, 12);
        legend.setGravity(Gravity.CENTER_VERTICAL);

        addLegendItem(legend, "منخفض",   0xFF4CAF50);
        addLegendItem(legend, "متوسط",   0xFFFFC107);
        addLegendItem(legend, "مرتفع",   0xFFFF5722);
        addLegendItem(legend, "عالٍ جداً", 0xFFF44336);
        parent.addView(legend);
    }

    private void addLegendItem(LinearLayout parent, String label, int color) {
        View dot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(18, 18);
        dotLp.setMargins(0, 0, 6, 0);
        dot.setLayoutParams(dotLp);
        dot.setBackgroundColor(color);

        TextView tv = new TextView(this);
        tv.setText(label + "  ");
        tv.setTextSize(11f);

        parent.addView(dot);
        parent.addView(tv);
    }

    private void addHeatCell(LinearLayout col, String gov, int count,
                              int maxCount, int total) {
        float ratio   = maxCount > 0 ? (float) count / maxCount : 0;
        int   bgColor = heatColor(count, maxCount);
        float alpha   = 0.25f + ratio * 0.75f;

        android.graphics.drawable.GradientDrawable bg =
            new android.graphics.drawable.GradientDrawable();
        bg.setColor(bgColor);
        bg.setAlpha((int)(alpha * 255));
        bg.setCornerRadius(12f);

        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(10, 14, 10, 14);
        LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        cellLp.setMargins(4, 4, 4, 4);
        cell.setLayoutParams(cellLp);
        cell.setBackground(bg);

        // اسم المحافظة
        TextView tvGov = new TextView(this);
        tvGov.setText(gov);
        tvGov.setTextSize(12f);
        tvGov.setTypeface(null, android.graphics.Typeface.BOLD);
        tvGov.setTextColor(ratio > 0.5f ? 0xFFFFFFFF : 0xFF212121);
        tvGov.setGravity(Gravity.CENTER);
        cell.addView(tvGov);

        // العدد
        TextView tvCount = new TextView(this);
        tvCount.setText(String.valueOf(count));
        tvCount.setTextSize(20f);
        tvCount.setTypeface(null, android.graphics.Typeface.BOLD);
        tvCount.setTextColor(ratio > 0.5f ? 0xFFFFFFFF : 0xFF212121);
        tvCount.setGravity(Gravity.CENTER);
        cell.addView(tvCount);

        // النسبة
        float pct = total > 0 ? count * 100f / total : 0;
        TextView tvPct = new TextView(this);
        tvPct.setText(String.format("%.0f%%", pct));
        tvPct.setTextSize(11f);
        tvPct.setTextColor(ratio > 0.5f ? 0xCCFFFFFF : 0xFF757575);
        tvPct.setGravity(Gravity.CENTER);
        cell.addView(tvPct);

        col.addView(cell);
    }

    // ─── بناء قسم الفئات العمرية ─────────────────────

    private void buildAgeStats(Map<String, Integer> ageMap, long total) {
        if (layoutAgeStats == null) return;
        layoutAgeStats.removeAllViews();

        int[] colors = {0xFF7986CB, 0xFF42A5F5, 0xFF66BB6A, 0xFFFFA726, 0xFFEF5350, 0xFF8D6E63};
        int colorIdx = 0;
        for (Map.Entry<String, Integer> e : ageMap.entrySet()) {
            addProgressRow(layoutAgeStats, e.getKey(), e.getValue(), (int) total,
                colors[colorIdx % colors.length]);
            colorIdx++;
        }
    }

    // ─── صف تقدم ─────────────────────────────────────

    private void addProgressRow(LinearLayout parent, String label, int count, int total, int color) {
        if (count == 0 && total > 0) return;
        float percent = total > 0 ? (count * 100f / total) : 0;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 8, 0, 8);

        // اسم + نسبة
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(13f);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvCount = new TextView(this);
        tvCount.setText(count + " (" + String.format("%.0f", percent) + "%)");
        tvCount.setTextSize(12f);
        tvCount.setTextColor(0xFF757575);

        header.addView(tvLabel);
        header.addView(tvCount);
        row.addView(header);

        // شريط تقدم
        ProgressBar pb = new ProgressBar(this, null,
            android.R.attr.progressBarStyleHorizontal);
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 16);
        pbParams.setMargins(0, 4, 0, 0);
        pb.setLayoutParams(pbParams);
        pb.setMax(100);
        pb.setProgress((int) percent);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            pb.getProgressDrawable().setTint(color);
        }
        row.addView(pb);

        parent.addView(row);
    }

    // ─── Helpers ─────────────────────────────────────

    private long getThisMonthStart() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        return cal.getTimeInMillis();
    }

    private String safeStr(DataSnapshot snap, String key) {
        String v = snap.child(key).getValue(String.class);
        return v != null ? v : "";
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }

    // ════════════════════════════════════════════════════
    //  Charts — MPAndroidChart
    // ════════════════════════════════════════════════════

    private void initCharts() {
        loadPieChart();
        loadWeeklyBarChart();
        loadGovernorateChart();
    }

    private void loadPieChart() {
        com.github.mikephil.charting.charts.PieChart pie =
            findViewById(R.id.pie_chart_types);
        if (pie == null) return;

        // FIX: يقرأ من 3 nodes
        String[] nodes = {"reports", "found_persons", "sightings"};
        int[] remaining = {nodes.length};
        java.util.HashMap<String,Integer> typeMap = new java.util.HashMap<>();

        for (String node : nodes) {
            FirebaseDatabase.getInstance().getReference(node)
                .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                    @Override public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot snap) {
                        for (com.google.firebase.database.DataSnapshot c : snap.getChildren()) {
                            String t = c.child("reportType").getValue(String.class);
                            if (t == null) {
                                if ("found_persons".equals(node)) t = "found";
                                else if ("sightings".equals(node)) t = "sighting";
                                else t = "missing";
                            }
                            typeMap.put(t, typeMap.getOrDefault(t, 0) + 1);
                        }
                        remaining[0]--;
                        if (remaining[0] == 0) buildPieChart(pie, typeMap);
                    }
                    @Override public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError e) {
                        remaining[0]--;
                        if (remaining[0] == 0) buildPieChart(pie, typeMap);
                    }
                });
        }
    }

    private void buildPieChart(com.github.mikephil.charting.charts.PieChart pie,
                                java.util.HashMap<String,Integer> typeMap) {
        java.util.ArrayList<com.github.mikephil.charting.data.PieEntry> entries = new java.util.ArrayList<>();
        java.util.Map<String,String> labels = new java.util.HashMap<>();
        labels.put("missing","مفقود"); labels.put("found","معثور");
        labels.put("sighting","مشاهدة"); labels.put("homeless","مشرد");
        labels.put("emergency","طوارئ");
        for (java.util.Map.Entry<String,Integer> e : typeMap.entrySet())
            entries.add(new com.github.mikephil.charting.data.PieEntry(
                e.getValue(), labels.containsKey(e.getKey()) ? labels.get(e.getKey()) : e.getKey()));
        com.github.mikephil.charting.data.PieDataSet ds =
            new com.github.mikephil.charting.data.PieDataSet(entries, "");
        ds.setColors(new int[]{0xFF1565C0,0xFF2E7D32,0xFFE65100,0xFF7B1FA2,0xFF00838F});
        ds.setValueTextSize(11f);
        ds.setValueTextColor(0xFFFFFFFF);
        pie.setData(new com.github.mikephil.charting.data.PieData(ds));
        pie.setHoleRadius(40f);
        pie.setTransparentCircleRadius(45f);
        pie.getDescription().setEnabled(false);
        pie.getLegend().setTextSize(11f);
        pie.animateY(800);
        pie.invalidate();
    }

    // FIX: يقرأ من 3 nodes بدل reports فقط
    private void loadWeeklyBarChart() {
        com.github.mikephil.charting.charts.BarChart bar =
            findViewById(R.id.bar_chart_weekly);
        if (bar == null) return;

        long now = System.currentTimeMillis();
        long day = 24 * 60 * 60 * 1000L;
        final int[] counts = new int[7];
        String[] dayLabels = new String[7];
        java.text.SimpleDateFormat sdf =
            new java.text.SimpleDateFormat("E", new java.util.Locale("ar"));
        for (int i = 0; i < 7; i++)
            dayLabels[i] = sdf.format(new java.util.Date(now - (6 - i) * day));

        String[] nodes = {"reports", "found_persons", "sightings"};
        int[] remaining = {nodes.length};

        for (String node : nodes) {
            FirebaseDatabase.getInstance().getReference(node)
                .orderByChild("timestamp").startAt((double)(now - 7 * day))
                .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                    @Override public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot snap) {
                        for (com.google.firebase.database.DataSnapshot c : snap.getChildren()) {
                            Long ts = c.child("timestamp").getValue(Long.class);
                            if (ts == null) continue;
                            int idx = (int)((now - ts) / day);
                            if (idx >= 0 && idx < 7) counts[6 - idx]++;
                        }
                        remaining[0]--;
                        if (remaining[0] == 0) runOnUiThread(() -> buildWeeklyBarChart(bar, counts, dayLabels));
                    }
                    @Override public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError e) {
                        remaining[0]--;
                        if (remaining[0] == 0) runOnUiThread(() -> buildWeeklyBarChart(bar, counts, dayLabels));
                    }
                });
        }
    }

    private void buildWeeklyBarChart(com.github.mikephil.charting.charts.BarChart bar,
                                      int[] counts, String[] dayLabels) {
        java.util.ArrayList<com.github.mikephil.charting.data.BarEntry> entries = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++)
            entries.add(new com.github.mikephil.charting.data.BarEntry(i, counts[i]));
        com.github.mikephil.charting.data.BarDataSet ds =
            new com.github.mikephil.charting.data.BarDataSet(entries, "البلاغات");
        ds.setColor(0xFF1565C0);
        ds.setValueTextSize(10f);
        bar.setData(new com.github.mikephil.charting.data.BarData(ds));
        bar.getXAxis().setValueFormatter(
            new com.github.mikephil.charting.formatter.IndexAxisValueFormatter(dayLabels));
        bar.getXAxis().setPosition(
            com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
        bar.getXAxis().setGranularity(1f);
        bar.getDescription().setEnabled(false);
        bar.getLegend().setEnabled(false);
        bar.animateY(800);
        bar.invalidate();
    }

    // FIX: يقرأ من 3 nodes بدل reports فقط
    private void loadGovernorateChart() {
        com.github.mikephil.charting.charts.HorizontalBarChart hbar =
            findViewById(R.id.bar_chart_govs);
        if (hbar == null) return;

        String[] nodes = {"reports", "found_persons", "sightings"};
        int[] remaining = {nodes.length};
        java.util.HashMap<String,Integer> govMap = new java.util.HashMap<>();

        for (String node : nodes) {
            FirebaseDatabase.getInstance().getReference(node)
                .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                    @Override public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot snap) {
                        for (com.google.firebase.database.DataSnapshot c : snap.getChildren()) {
                            String g = c.child("governorate").getValue(String.class);
                            if (g == null || g.isEmpty()) continue;
                            govMap.put(g, govMap.getOrDefault(g, 0) + 1);
                        }
                        remaining[0]--;
                        if (remaining[0] == 0) runOnUiThread(() -> buildGovernorateChart(hbar, govMap));
                    }
                    @Override public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError e) {
                        remaining[0]--;
                        if (remaining[0] == 0) runOnUiThread(() -> buildGovernorateChart(hbar, govMap));
                    }
                });
        }
    }

    private void buildGovernorateChart(com.github.mikephil.charting.charts.HorizontalBarChart hbar,
                                        java.util.HashMap<String,Integer> govMap) {
        java.util.List<java.util.Map.Entry<String,Integer>> sorted =
            new java.util.ArrayList<>(govMap.entrySet());
        sorted.sort((a,b2) -> b2.getValue() - a.getValue());
        if (sorted.size() > 10) sorted = sorted.subList(0, 10);
        java.util.ArrayList<com.github.mikephil.charting.data.BarEntry> entries = new java.util.ArrayList<>();
        String[] govLabels = new String[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            entries.add(new com.github.mikephil.charting.data.BarEntry(i, sorted.get(i).getValue()));
            govLabels[i] = sorted.get(i).getKey();
        }
        com.github.mikephil.charting.data.BarDataSet ds =
            new com.github.mikephil.charting.data.BarDataSet(entries, "");
        ds.setColors(new int[]{0xFF1565C0,0xFF1976D2,0xFF1E88E5,0xFF2196F3,0xFF42A5F5,
                               0xFF64B5F6,0xFF0D47A1,0xFF0288D1,0xFF0097A7,0xFF00ACC1});
        ds.setValueTextSize(9f);
        hbar.setData(new com.github.mikephil.charting.data.BarData(ds));
        hbar.getAxisLeft().setAxisMinimum(0f);
        hbar.getXAxis().setValueFormatter(
            new com.github.mikephil.charting.formatter.IndexAxisValueFormatter(govLabels));
        hbar.getXAxis().setGranularity(1f);
        hbar.getXAxis().setTextSize(9f);
        hbar.getDescription().setEnabled(false);
        hbar.getLegend().setEnabled(false);
        hbar.animateX(1000);
        hbar.invalidate();
    }
}
