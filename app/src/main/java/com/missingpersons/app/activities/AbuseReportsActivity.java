package com.missingpersons.app.activities;

import android.content.res.ColorStateList;
import android.content.Context;
import android.os.Bundle;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.missingpersons.app.R;
import com.missingpersons.app.utils.LanguageHelper;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * AbuseReportsActivity — عرض بلاغات سوء الاستخدام
 *
 * ✅ Filter Chips (الكل / جديد / معالَج / مرفوض)
 * ✅ Extended FAB — تمييز الكل كمقروء
 * ✅ Entry animations للقائمة
 * ✅ Refresh في Empty State
 * ✅ M3 chip colors — resolved via theme attribute (لا darker_gray)
 */
public class AbuseReportsActivity extends AppCompatActivity {

    // ─── Views ──────────────────────────────────────────────────────
    private RecyclerView               recyclerView;
    private LinearProgressIndicator   progressIndicator;
    private LinearLayout               layoutEmpty;
    private TextView                   tvEmpty;
    private MaterialButton             btnEmptyRefresh;
    private ExtendedFloatingActionButton fabMarkAllRead;
    private ChipGroup                  chipGroupFilter;

    // ─── Firebase ───────────────────────────────────────────────────
    private DatabaseReference dbRef;
    private String            currentUid = "";

    // ─── Data ────────────────────────────────────────────────────────
    private final List<HashMap<String, Object>> allReports      = new ArrayList<>();
    private final List<HashMap<String, Object>> filteredReports = new ArrayList<>();
    private AbuseAdapter adapter;

    private String currentFilter = null;

    // ────────────────────────────────────────────────────────────────
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LanguageHelper.applyLanguage(base));
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_abuse_reports);
        // [إصلاح 6 — Edge-to-Edge]
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bot = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), bot);
            return insets;
        });

        // ── Auth check ───────────────────────────────────────────────
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) { finish(); return; }
        currentUid = auth.getCurrentUser().getUid();

        // ── Toolbar ──────────────────────────────────────────────────
        MaterialToolbar toolbar = findViewById(R.id.toolbar_abuse);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("بلاغات سوء الاستخدام");
        }

        // ── Bind Views ───────────────────────────────────────────────
        recyclerView       = findViewById(R.id.rv_abuse_reports);
        progressIndicator  = findViewById(R.id.progress_abuse);
        layoutEmpty        = findViewById(R.id.layout_empty_abuse);
        tvEmpty            = findViewById(R.id.tv_empty_abuse);
        btnEmptyRefresh    = findViewById(R.id.btn_empty_refresh);
        fabMarkAllRead     = findViewById(R.id.fab_mark_all_read);
        chipGroupFilter    = findViewById(R.id.chipgroup_filter);

        // ── RecyclerView ─────────────────────────────────────────────
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AbuseAdapter(filteredReports, this::onAction);
        recyclerView.setAdapter(adapter);

        // ── Shrink FAB on scroll ─────────────────────────────────────
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 6)  fabMarkAllRead.shrink();
                if (dy < -6) fabMarkAllRead.extend();
            }
        });

        // ── Firebase ─────────────────────────────────────────────────
        dbRef = FirebaseDatabase.getInstance().getReference();

        // ── Listeners ────────────────────────────────────────────────
        fabMarkAllRead.setOnClickListener(v -> markAllNotificationsRead());
        btnEmptyRefresh.setOnClickListener(v -> loadReports());

        setupChipFilter();
        loadReports();
    }

    // ────────────────────────────────────────────────────────────────
    //  Chip Filter
    // ────────────────────────────────────────────────────────────────

    private void setupChipFilter() {
        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) { currentFilter = null; applyFilter(); return; }
            int id = checkedIds.get(0);
            if      (id == R.id.chip_filter_all)       currentFilter = null;
            else if (id == R.id.chip_filter_new)       currentFilter = "pending";
            else if (id == R.id.chip_filter_resolved)  currentFilter = "resolved";
            else if (id == R.id.chip_filter_dismissed) currentFilter = "dismissed";
            applyFilter();
        });
    }

    private void applyFilter() {
        filteredReports.clear();
        if (currentFilter == null) {
            filteredReports.addAll(allReports);
        } else {
            for (HashMap<String, Object> r : allReports) {
                String status = r.get("status") instanceof String ? (String) r.get("status") : "pending";
                if (currentFilter.equals(status)) filteredReports.add(r);
            }
        }
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    // ────────────────────────────────────────────────────────────────
    //  Load Data
    // ────────────────────────────────────────────────────────────────

    private void loadReports() {
        if (progressIndicator != null) progressIndicator.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);

        dbRef.child("abuse_reports").orderByChild("timestamp")
            .addValueEventListener(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                    allReports.clear();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        HashMap<String, Object> map = new HashMap<>();
                        for (DataSnapshot f : child.getChildren()) map.put(f.getKey(), f.getValue());
                        map.put("_key", child.getKey());
                        allReports.add(map);
                    }
                    // الأحدث أولاً
                    allReports.sort((a, b) -> {
                        long la = a.get("timestamp") instanceof Long ? (Long) a.get("timestamp") : 0;
                        long lb = b.get("timestamp") instanceof Long ? (Long) b.get("timestamp") : 0;
                        return Long.compare(lb, la);
                    });

                    if (progressIndicator != null) progressIndicator.setVisibility(View.GONE);
                    applyFilter();
                    markAllNotificationsRead();
                }

                @Override public void onCancelled(@NonNull DatabaseError e) {
                    if (progressIndicator != null) progressIndicator.setVisibility(View.GONE);
                    Toast.makeText(AbuseReportsActivity.this,
                        "خطأ في التحميل، يُرجى المحاولة مجدداً", Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void updateEmptyState() {
        boolean empty = filteredReports.isEmpty();
        layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);

        if (empty && tvEmpty != null) {
            tvEmpty.setText(currentFilter == null
                ? "لا توجد بلاغات حالياً"
                : "لا توجد بلاغات بهذا التصنيف");
        }
    }

    // ────────────────────────────────────────────────────────────────
    //  Actions
    // ────────────────────────────────────────────────────────────────

    private void onAction(String action, HashMap<String, Object> report) {
        String key = (String) report.get("_key");
        if (key == null) return;

        switch (action) {
            case "resolve":
                dbRef.child("abuse_reports").child(key).child("status").setValue("resolved");
                Toast.makeText(this, "تم تمييز البلاغ كمعالَج", Toast.LENGTH_SHORT).show();
                break;

            case "dismiss":
                dbRef.child("abuse_reports").child(key).child("status").setValue("dismissed");
                Toast.makeText(this, "تم تجاهل البلاغ", Toast.LENGTH_SHORT).show();
                break;

            case "delete":
                new AlertDialog.Builder(this)
                    .setTitle("حذف البلاغ")
                    .setMessage("هل تريد حذف هذا البلاغ نهائياً؟")
                    .setPositiveButton("حذف", (d, w) -> {
                        dbRef.child("abuse_reports").child(key).removeValue();
                        Toast.makeText(this, "تم الحذف", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("إلغاء", null)
                    .show();
                break;
        }
    }

    private void markAllNotificationsRead() {
        if (currentUid.isEmpty()) return;
        dbRef.child("notifications").child(currentUid)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        child.getRef().child("read").setValue(true);
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
    }

    @Override
    public boolean onSupportNavigateUp() { onBackPressed(); return true; }

    // ════════════════════════════════════════════════════════════════
    //  ADAPTER
    // ════════════════════════════════════════════════════════════════

    static class AbuseAdapter extends RecyclerView.Adapter<AbuseAdapter.VH> {

        private final List<HashMap<String, Object>> items;
        private final ActionListener listener;
        interface ActionListener { void onAction(String action, HashMap<String, Object> r); }

        AbuseAdapter(List<HashMap<String, Object>> items, ActionListener l) {
            this.items = items;
            this.listener = l;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_abuse_report, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            HashMap<String, Object> r = items.get(pos);

            String targetName    = r.get("targetName")    instanceof String ? (String) r.get("targetName")    : "مجهول";
            String reason        = r.get("reason")        instanceof String ? (String) r.get("reason")        : "-";
            String details       = r.get("details")       instanceof String ? (String) r.get("details")       : "-";
            String reporterEmail = r.get("reporterEmail") instanceof String ? (String) r.get("reporterEmail") : "مجهول";
            String status        = r.get("status")        instanceof String ? (String) r.get("status")        : "pending";
            long   ts            = r.get("timestamp") instanceof Long ? (Long) r.get("timestamp") : 0;

            h.tvTarget.setText(targetName);
            h.tvReason.setText("السبب: " + reason);
            h.tvDetails.setText(details);
            h.tvReporter.setText("المُبلِّغ: " + reporterEmail);
            h.tvTime.setText(ts > 0
                ? new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date(ts))
                : "");

            // ── Status chip colors — M3 theme attributes ─────────────
            Context ctx = h.chipStatus.getContext();
            switch (status) {
                case "resolved":
                    h.chipStatus.setText("معالَج");
                    h.chipStatus.setChipBackgroundColor(
                        ColorStateList.valueOf(resolveAttrColor(ctx,
                            com.google.android.material.R.attr.colorTertiaryContainer)));
                    h.chipStatus.setTextColor(
                        ColorStateList.valueOf(resolveAttrColor(ctx,
                            com.google.android.material.R.attr.colorOnTertiaryContainer)));
                    break;
                case "dismissed":
                    h.chipStatus.setText("مرفوض");
                    // ✅ M3 surface variant — بدل android.R.color.darker_gray
                    h.chipStatus.setChipBackgroundColor(
                        ColorStateList.valueOf(resolveAttrColor(ctx,
                            com.google.android.material.R.attr.colorSurfaceVariant)));
                    h.chipStatus.setTextColor(
                        ColorStateList.valueOf(resolveAttrColor(ctx,
                            com.google.android.material.R.attr.colorOnSurfaceVariant)));
                    break;
                default: // pending / جديد
                    h.chipStatus.setText("جديد");
                    h.chipStatus.setChipBackgroundColor(
                        ColorStateList.valueOf(resolveAttrColor(ctx,
                            com.google.android.material.R.attr.colorSecondaryContainer)));
                    h.chipStatus.setTextColor(
                        ColorStateList.valueOf(resolveAttrColor(ctx,
                            com.google.android.material.R.attr.colorOnSecondaryContainer)));
            }

            h.btnResolve.setOnClickListener(v -> listener.onAction("resolve", r));
            h.btnDismiss.setOnClickListener(v -> listener.onAction("dismiss", r));
            h.btnDelete .setOnClickListener(v -> listener.onAction("delete",  r));
        }

        /**
         * يحوّل theme attribute إلى لون فعلي — الطريقة الصحيحة للحصول على
         * M3 dynamic colors في الكود بدلاً من colorResource ثابت.
         */
        private static int resolveAttrColor(Context ctx, int attrRes) {
            TypedValue tv = new TypedValue();
            ctx.getTheme().resolveAttribute(attrRes, tv, true);
            return tv.data;
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView       tvTarget, tvReason, tvDetails, tvReporter, tvTime;
            Chip           chipStatus;
            MaterialButton btnResolve, btnDismiss, btnDelete;

            VH(@NonNull View v) {
                super(v);
                tvTarget   = v.findViewById(R.id.tv_abuse_target);
                tvReason   = v.findViewById(R.id.tv_abuse_reason);
                tvDetails  = v.findViewById(R.id.tv_abuse_details);
                tvReporter = v.findViewById(R.id.tv_abuse_reporter);
                tvTime     = v.findViewById(R.id.tv_abuse_time);
                chipStatus = v.findViewById(R.id.chip_abuse_status);
                btnResolve = v.findViewById(R.id.btn_abuse_resolve);
                btnDismiss = v.findViewById(R.id.btn_abuse_dismiss);
                btnDelete  = v.findViewById(R.id.btn_abuse_delete);
            }
        }
    }
}
