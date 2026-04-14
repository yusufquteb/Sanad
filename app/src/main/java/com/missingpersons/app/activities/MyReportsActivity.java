package com.missingpersons.app.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.missingpersons.app.R;
import com.missingpersons.app.utils.BookmarkManager;
import com.missingpersons.app.utils.CoilImageLoader;
import com.missingpersons.app.utils.LanguageHelper;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * MyReportsActivity — بلاغاتي
 *
 * يعرض كل البلاغات التي رفعها المستخدم الحالي مع حالة كل بلاغ:
 *   ⏳ قيد المراجعة  →  لون رمادي
 *   ✅ تمت الموافقة  →  لون أخضر
 *   ✏️ تم التعديل   →  لون برتقالي
 *   🔒 مُغلَق        →  لون أحمر
 */
public class MyReportsActivity extends AppCompatActivity {

    private LinearLayout layoutReports;
    private LinearLayout layoutBookmarks;
    private ProgressBar  progressBar;
    private TextView     tvEmpty;

    private final SimpleDateFormat sdf =
        new SimpleDateFormat("dd/MM/yyyy", new Locale("ar"));

    @Override
    protected void attachBaseContext(android.content.Context c) {
        super.attachBaseContext(LanguageHelper.applyLanguage(c));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_reports);
        // ── Edge-to-Edge: يمنع تداخل المحتوى مع Navigation Bar ──
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
            int navBot = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                         v.getPaddingRight(), navBot);
            return insets;
        });

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("📋 بلاغاتي");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        layoutReports   = findViewById(R.id.layout_my_reports);
        layoutBookmarks = findViewById(R.id.layout_bookmarks);
        progressBar     = findViewById(R.id.progress_my_reports);
        tvEmpty         = findViewById(R.id.tv_my_reports_empty);

        // [إصلاح BookmarkManager] ربط تبويبات بلاغاتي / المفضلة
        com.google.android.material.chip.ChipGroup tabs = findViewById(R.id.chip_group_my_tabs);
        if (tabs != null) {
            tabs.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.contains(R.id.chip_bookmarks)) {
                    showBookmarks();
                } else {
                    showMyReports();
                }
            });
        }

        loadMyReports();
    }

    private void loadMyReports() {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
            ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (uid == null) {
            if (tvEmpty != null) { tvEmpty.setVisibility(View.VISIBLE); tvEmpty.setText("سجّل دخولك لعرض بلاغاتك"); }
            return;
        }

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        // اجمع من reports + found_persons + sightings
        String[] nodes = {"reports", "found_persons", "sightings"};
        List<DataSnapshot> allSnaps = new ArrayList<>();
        int[] pending = {nodes.length};

        for (String node : nodes) {
            FirebaseDatabase.getInstance().getReference(node)
                .orderByChild("reporterId").equalTo(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snap) {
                        for (DataSnapshot c : snap.getChildren()) allSnaps.add(c);
                        if (--pending[0] == 0) renderAll(allSnaps);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {
                        if (--pending[0] == 0) renderAll(allSnaps);
                    }
                });
        }
    }

    private void renderAll(List<DataSnapshot> list) {
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (layoutReports != null) layoutReports.removeAllViews();

        if (list.isEmpty()) {
            if (tvEmpty != null) {
                tvEmpty.setVisibility(View.VISIBLE);
                tvEmpty.setText("لم ترفع أي بلاغ بعد\nاضغط ＋ لرفع أول بلاغ وساعد في إيجاد مفقود");
            }
            return;
        }
        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);

        list.sort((a, b) -> {
            Long ta = a.child("timestamp").getValue(Long.class);
            Long tb = b.child("timestamp").getValue(Long.class);
            return Long.compare(tb != null ? tb : 0L, ta != null ? ta : 0L);
        });

        for (DataSnapshot report : list) addReportCard(report);
    }

    private void addReportCard(DataSnapshot snap) {
        String id     = snap.getKey();
        String name   = snap.child("personName").getValue(String.class);
        String addr   = snap.child("manualAddress").getValue(String.class);
        String status = snap.child("status").getValue(String.class);
        Boolean approved = snap.child("approved").getValue(Boolean.class);
        Long    ts    = snap.child("timestamp").getValue(Long.class);
        Long    editedAt = snap.child("editedAt").getValue(Long.class);
        Object  urlsObj  = snap.child("imageUrls").getValue();

        // ── تحديد الحالة ─────────────────────────────────────────────
        String statusLabel;
        int    statusColor;
        String statusIcon;

        if (Boolean.TRUE.equals(approved)) {
            if (editedAt != null) {
                statusLabel = "تم التعديل";
                statusColor = 0xFFE65100;
                statusIcon  = "✏️";
            } else {
                statusLabel = "تمت الموافقة";
                statusColor = 0xFF2E7D32;
                statusIcon  = "✅";
            }
        } else if ("deleted".equals(status) || "closed".equals(status)) {
            statusLabel = "مُغلَق";
            statusColor = 0xFFB71C1C;
            statusIcon  = "🔒";
        } else {
            statusLabel = "قيد المراجعة";
            statusColor = 0xFF757575;
            statusIcon  = "⏳";
        }

        // ── بناء الكارد ──────────────────────────────────────────────
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, 16);
        card.setLayoutParams(cardLp);
        card.setRadius(16f);
        card.setCardElevation(3f);
        card.setStrokeColor(statusColor);
        card.setStrokeWidth(3);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(16, 16, 16, 16);

        // صورة مصغرة
        ImageView ivPhoto = new ImageView(this);
        LinearLayout.LayoutParams photoLp = new LinearLayout.LayoutParams(90, 90);
        photoLp.setMargins(0, 0, 16, 0);
        ivPhoto.setLayoutParams(photoLp);
        ivPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (urlsObj instanceof List && !((List<?>) urlsObj).isEmpty()) {
            CoilImageLoader.loadRounded(this, ((List<?>) urlsObj).get(0).toString(),
                ivPhoto, R.drawable.ic_face_placeholder, 8f);
        }
        row.addView(ivPhoto);

        // معلومات
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // الاسم
        TextView tvName = new TextView(this);
        tvName.setText(name != null ? name : "مجهول");
        tvName.setTextSize(16f);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setTextColor(0xFF1A1C1E);
        info.addView(tvName);

        // الموقع
        if (addr != null && !addr.isEmpty()) {
            TextView tvAddr = new TextView(this);
            tvAddr.setText("📍 " + (addr.length() > 30
                ? addr.substring(0, 30) + "…" : addr));
            tvAddr.setTextSize(13f);
            tvAddr.setTextColor(0xFF555555);
            info.addView(tvAddr);
        }

        // التاريخ
        if (ts != null) {
            TextView tvDate = new TextView(this);
            tvDate.setText("🗓 " + sdf.format(new Date(ts)));
            tvDate.setTextSize(12f);
            tvDate.setTextColor(0xFF888888);
            info.addView(tvDate);
        }

        // شارة الحالة
        Chip chip = new Chip(this);
        chip.setText(statusIcon + " " + statusLabel);
        chip.setTextColor(Color.WHITE);
        chip.setChipBackgroundColor(
            android.content.res.ColorStateList.valueOf(statusColor));
        chip.setClickable(false);
        chip.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        info.addView(chip);

        // ── أزرار تعديل وحذف ─────────────────────────────────────────
        if (!"deleted".equals(status) && !"closed".equals(status)) {
            LinearLayout btnRow = new LinearLayout(this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.setPadding(0, 8, 0, 0);

            MaterialButton btnEdit = new MaterialButton(this,null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btnEdit.setText("✏️ تعديل");
            btnEdit.setTextSize(12f);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            ep.setMarginEnd(8);
            btnEdit.setLayoutParams(ep);
            btnEdit.setOnClickListener(v -> confirmEdit(snap, dbNode(snap)));

            MaterialButton btnDelete = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btnDelete.setText("🗑 حذف");
            btnDelete.setTextSize(12f);
            btnDelete.setStrokeColor(android.content.res.ColorStateList.valueOf(0xFFB71C1C));
            btnDelete.setTextColor(0xFFB71C1C);
            btnDelete.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            btnDelete.setOnClickListener(v -> confirmDelete(id, dbNode(snap)));

            btnRow.addView(btnEdit);
            btnRow.addView(btnDelete);
            info.addView(btnRow);
        }

        row.addView(info);
        card.addView(row);

        // الضغط → تفاصيل البلاغ
        final String cardNode = dbNode(snap);
        card.setOnClickListener(v -> {
            if (id != null) {
                startActivity(new Intent(this, CaseDetailActivity.class)
                    .putExtra("reportId", id)
                    .putExtra("editReportId", id)
                    .putExtra("editReportNode", cardNode));
            }
        });

        if (layoutReports != null) layoutReports.addView(card);
    }

    private String dbNode(DataSnapshot snap) {
        String type = snap.child("reportType").getValue(String.class);
        if ("found".equals(type))    return "found_persons";
        if ("sighting".equals(type)) return "sightings";
        return "reports";
    }

    /** حذف البلاغ بعد تأكيد */
    private void confirmDelete(String id, String node) {
        new AlertDialog.Builder(this)
            .setTitle("🗑 حذف البلاغ")
            .setMessage("هل أنت متأكد من حذف هذا البلاغ؟ لا يمكن التراجع.")
            .setPositiveButton("حذف", (d, w) -> {
                FirebaseDatabase.getInstance().getReference(node).child(id)
                    .removeValue()
                    .addOnSuccessListener(v -> {
                        Snackbar.make(layoutReports, "✅ تم حذف البلاغ", Snackbar.LENGTH_SHORT).show();
                        loadMyReports();
                    })
                    .addOnFailureListener(e ->
                        Toast.makeText(this, "❌ فشل الحذف", Toast.LENGTH_SHORT).show());
            })
            .setNegativeButton("إلغاء", null)
            .show();
    }

    /** تعديل البلاغ — يُحجب ويُرسل للمراجعة مرة أخرى */
    private void confirmEdit(DataSnapshot snap, String node) {
        new AlertDialog.Builder(this)
            .setTitle("✏️ تعديل البلاغ")
            .setMessage("سيتم إخفاء البلاغ مؤقتاً وإرساله للإدارة للمراجعة قبل إعادة نشره.")
            .setPositiveButton("موافق، عدّل", (d, w) -> {
                String id = snap.getKey();
                // علّم البلاغ بأنه قيد التعديل
                FirebaseDatabase.getInstance().getReference(node).child(id)
                    .updateChildren(new java.util.HashMap<String, Object>() {{
                        put("status",   "pending_edit");
                        put("approved", false);
                        put("editedAt", System.currentTimeMillis());
                    }})
                    .addOnSuccessListener(v -> {
                        Toast.makeText(this,
                            "⏳ تم إخفاء البلاغ — سيُعاد نشره بعد موافقة الإدارة",
                            Toast.LENGTH_LONG).show();
                        // افتح ReportActivity مع بيانات البلاغ للتعديل
                        Intent intent = new Intent(this, ReportActivity.class);
                        intent.putExtra("editMode",  true);
                        intent.putExtra("reportId",  id);
                        intent.putExtra("editReportId",  id);
                        intent.putExtra("editReportNode", "reports");
                        intent.putExtra("reportNode", node);
                        startActivity(intent);
                    });
            })
            .setNegativeButton("إلغاء", null)
            .show();
    }

    @Override public boolean onSupportNavigateUp() { onBackPressed(); return true; }
    // ─── تبديل العرض بين بلاغاتي والمفضلة ─────────────────

    private void showMyReports() {
        if (layoutReports   != null) layoutReports.setVisibility(View.VISIBLE);
        if (layoutBookmarks != null) layoutBookmarks.setVisibility(View.GONE);
        if (tvEmpty != null && layoutReports != null
                && layoutReports.getChildCount() == 0) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("لم ترفع أي بلاغ بعد");
        }
    }

    private void showBookmarks() {
        if (layoutReports   != null) layoutReports.setVisibility(View.GONE);
        if (layoutBookmarks != null) {
            layoutBookmarks.setVisibility(View.VISIBLE);
            layoutBookmarks.removeAllViews();
            loadBookmarks();
        }
    }

    /**
     * يعرض البلاغات المحفوظة في المفضلة (BookmarkManager)
     * يجلب تفاصيل كل بلاغ من Firebase بـ ID محفوظ محلياً
     */
    private void loadBookmarks() {
        java.util.Set<String> ids = BookmarkManager.getBookmarkedIds(this);

        if (ids.isEmpty()) {
            if (tvEmpty != null) {
                tvEmpty.setVisibility(View.VISIBLE);
                tvEmpty.setText("لا توجد بلاغات في المفضلة\nاضغط 🔖 في أي بلاغ لحفظه");
            }
            return;
        }

        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        int[] remaining = {ids.size()};
        java.util.List<DataSnapshot> snaps = new java.util.ArrayList<>();

        for (String id : ids) {
            // حاول في reports أولاً، ثم found_persons
            com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("reports").child(id)
                .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                    @Override public void onDataChange(@androidx.annotation.NonNull
                            com.google.firebase.database.DataSnapshot snap) {
                        if (snap.exists()) snaps.add(snap);
                        if (--remaining[0] == 0) renderBookmarks(snaps);
                    }
                    @Override public void onCancelled(@androidx.annotation.NonNull
                            com.google.firebase.database.DatabaseError e) {
                        if (--remaining[0] == 0) renderBookmarks(snaps);
                    }
                });
        }
    }

    private void renderBookmarks(java.util.List<DataSnapshot> list) {
        runOnUiThread(() -> {
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            if (layoutBookmarks == null) return;
            layoutBookmarks.removeAllViews();

            if (list.isEmpty()) {
                if (tvEmpty != null) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText("لم يُعثر على البلاغات المحفوظة");
                }
                return;
            }

            for (DataSnapshot snap : list) {
                String name   = snap.child("personName").getValue(String.class);
                String status = snap.child("status").getValue(String.class);
                String addr   = snap.child("manualAddress").getValue(String.class);
                Long   ts     = snap.child("timestamp").getValue(Long.class);
                String rid    = snap.getKey();

                if (name == null) name = "غير محدد";
                if (addr == null) addr = snap.child("governorate").getValue(String.class);
                if (addr == null) addr = "";

                com.google.android.material.card.MaterialCardView card =
                    new com.google.android.material.card.MaterialCardView(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, 12);
                card.setLayoutParams(lp);
                card.setRadius(16f);
                card.setCardElevation(2f);
                card.setUseCompatPadding(true);

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(32, 24, 32, 24);

                TextView tvN = new TextView(this);
                tvN.setText("🔖 " + name);
                tvN.setTextSize(15f);
                tvN.setTypeface(null, android.graphics.Typeface.BOLD);

                TextView tvA = new TextView(this);
                tvA.setText("📍 " + addr);
                tvA.setTextSize(12f);
                tvA.setPadding(0, 4, 0, 0);

                TextView tvS = new TextView(this);
                tvS.setText("الحالة: " + (status != null ? status : "—"));
                tvS.setTextSize(12f);

                row.addView(tvN);
                row.addView(tvA);
                row.addView(tvS);
                card.addView(row);

                final String finalRid = rid;
                card.setOnClickListener(v ->
                    startActivity(new Intent(this, CaseDetailActivity.class)
                        .putExtra("reportId", finalRid)));

                layoutBookmarks.addView(card);
            }
        });
    }


}
