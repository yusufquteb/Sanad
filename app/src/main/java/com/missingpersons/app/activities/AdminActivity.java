package com.missingpersons.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.missingpersons.app.R;
import com.missingpersons.app.utils.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * AdminActivity — إدارة البلاغات
 *
 * الإصلاحات في هذا الإصدار:
 *  [خطأ-02] تاب التطابقات: زر "موافقة" يفتح dialog تأكيد حقيقي
 *            يُعدَّل status في reports/ و found_persons/ معاً
 *            ويُبلَّغ كلا المستخدمين بالتطابق المؤكد
 *  [خطأ-03] حذف ADMIN_EMAIL من الكود — التحقق عبر RoleManager فقط
 *
 * الصلاحيات:
 *  - canApproveReports  → تبويب "معلقة" مع أزرار الموافقة/الرفض
 *  - canDeleteReports   → زر الحذف
 *  - canViewAllReports  → تبويبات "موافق" و"مرفوضة"
 *  - canEditReports     → زر التعديل
 *  - isAdmin()          → تبويب "التطابقات" + تأكيد التطابق
 */
public class AdminActivity extends AppCompatActivity {

    // ═══════════════════════════════════════════════════════
    // خطأ-03: ADMIN_EMAIL محذوف — استخدم RoleManager.get().isAdmin()
    // ═══════════════════════════════════════════════════════

    private RecyclerView    recyclerView;
    private TextView        tvStats;
    private TabLayout       tabLayout;
    private ProgressBar     progressBar;
    private TextView        tvEmpty;
    private List<HashMap<String, Object>> reportsList = new ArrayList<>();
    private AdminAdapter    adapter;
    private DatabaseReference reportsRef;
    private DatabaseReference foundPersonsRef;
    private ValueEventListener foundPersonsListener;
    private ValueEventListener currentReportsListener;
    private int totalReports = 0, pendingCount = 0, approvedCount = 0;

    // هل نحن الآن في تبويب التطابقات؟
    private boolean isMatchesTab = false;

    @Override
    protected void attachBaseContext(android.content.Context c) {
        super.attachBaseContext(LanguageHelper.applyLanguage(c));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(this, "يجب تسجيل الدخول أولاً", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_admin);

        // [إصلاح المرحلة 3] Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bot = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), bot);
            return insets;
        });

        // التحقق عبر RoleManager (Firebase DB) — لا ADMIN_EMAIL
        RoleManager.get().load(new RoleManager.LoadCallback() {
            @Override
            public void onLoaded(boolean isAdminOrManager) {
                if (!isAdminOrManager) {
                    Toast.makeText(AdminActivity.this,
                        "غير مصرح لك بالدخول", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                runOnUiThread(() -> {
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle("لوحة الإدارة");
                        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                    }
                    initViews();
                    loadReports("pending");
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(AdminActivity.this,
                        "خطأ في التحقق: " + message, Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void initViews() {
        recyclerView = findViewById(R.id.rv_admin);
        tvStats      = findViewById(R.id.tv_stats);
        tabLayout    = findViewById(R.id.tab_layout);
        progressBar  = findViewById(R.id.progress_admin);
        tvEmpty      = findViewById(R.id.tv_empty_admin);

        if (recyclerView != null)
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AdminAdapter(reportsList, this::onReportAction);
        if (recyclerView != null) recyclerView.setAdapter(adapter);
        reportsRef = FirebaseDatabase.getInstance().getReference("reports");

        buildTabs();
        loadStats();
    }

    private void buildTabs() {
        RoleManager role = RoleManager.get();
        tabLayout.removeAllTabs();

        if (role.canApproveReports())
            tabLayout.addTab(tabLayout.newTab().setText("معلقة"));
        if (role.canViewAllReports())
            tabLayout.addTab(tabLayout.newTab().setText("موافق عليها"));
        if (role.isAdmin())
            tabLayout.addTab(tabLayout.newTab().setText("التطابقات"));
        if (role.canDeleteReports())
            tabLayout.addTab(tabLayout.newTab().setText("محذوفة"));
        if (role.canManageMembers())
            tabLayout.addTab(tabLayout.newTab().setText("الأعضاء"));
        if (role.isAdmin())
            tabLayout.addTab(tabLayout.newTab().setText("المديرون"));
        if (role.isAdmin())
            tabLayout.addTab(tabLayout.newTab().setText("المحادثات"));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                String title = tab.getText() != null ? tab.getText().toString() : "";
                switch (title) {
                    case "معلقة":
                        isMatchesTab = false;
                        loadReports("pending");
                        break;
                    case "موافق عليها":
                        isMatchesTab = false;
                        loadReports("approved");
                        break;
                    case "التطابقات":
                        isMatchesTab = true;
                        loadMatchesTab();
                        break;
                    case "محذوفة":
                        isMatchesTab = false;
                        loadReports("deleted");
                        break;
                    case "الأعضاء":
                        startActivity(new Intent(AdminActivity.this, MembersActivity.class));
                        break;
                    case "المديرون":
                        startActivity(new Intent(AdminActivity.this, ManagersActivity.class));
                        break;
                    case "المحادثات":
                        startActivity(new Intent(AdminActivity.this, AdminChatsActivity.class));
                        break;
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void loadStats() {
        reportsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                totalReports = 0; pendingCount = 0; approvedCount = 0;
                for (DataSnapshot c : snap.getChildren()) {
                    totalReports++;
                    String s = c.child("status").getValue(String.class);
                    if ("pending".equals(s))       pendingCount++;
                    else if ("approved".equals(s)) approvedCount++;
                }
                runOnUiThread(() -> {
                    if (tvStats != null)
                        tvStats.setText("الإجمالي: " + totalReports +
                            "  |  معلقة: " + pendingCount +
                            "  |  موافق: " + approvedCount);
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }

    private void loadReports(String status) {
        isMatchesTab = false;
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (currentReportsListener != null && reportsRef != null)
            reportsRef.removeEventListener(currentReportsListener);

        Query query = reportsRef.orderByChild("status").equalTo(status);
        currentReportsListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                reportsList.clear();
                for (DataSnapshot c : snap.getChildren()) {
                    HashMap<String, Object> map = new HashMap<>();
                    if (c.getValue() instanceof Map) {
                        //noinspection unchecked
                        map.putAll((Map<String, Object>) c.getValue());
                    }
                    if (!map.containsKey("reportId")) map.put("reportId", c.getKey());
                    reportsList.add(map);
                }
                reportsList.sort((a, b) -> {
                    Object ta = a.get("timestamp"); Object tb = b.get("timestamp");
                    long la = ta instanceof Long ? (Long) ta : 0L;
                    long lb = tb instanceof Long ? (Long) tb : 0L;
                    return Long.compare(lb, la);
                });
                runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    boolean empty = reportsList.isEmpty();
                    if (tvEmpty != null) tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                    if (recyclerView != null) recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
                    if (adapter != null) adapter.notifyDataSetChanged();
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                if (progressBar != null)
                    runOnUiThread(() -> progressBar.setVisibility(View.GONE));
            }
        };
        query.addValueEventListener(currentReportsListener);
    }

    /**
     * تحميل تبويب التطابقات من matches/
     *
     * [إصلاح خطأ-02]:
     *  - نحفظ المفتاح في "_matchKey" ولا نتجاوز "reportId" الأصلي
     *    حتى يعمل confirmMatchRecord() بشكل صحيح
     */
    private void loadMatchesTab() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        FirebaseDatabase.getInstance().getReference("matches")
            .orderByChild("timestamp")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    reportsList.clear();
                    for (DataSnapshot c : snap.getChildren()) {
                        HashMap<String, Object> map = new HashMap<>();
                        if (c.getValue() instanceof Map) {
                            //noinspection unchecked
                            map.putAll((Map<String, Object>) c.getValue());
                        }
                        // [إصلاح] نحفظ مفتاح matches/ في _matchKey
                        // ولا نُدمر "reportId" الأصلي الموجود في البيانات
                        map.put("_matchKey", c.getKey());
                        // إذا لم يكن reportId موجوداً في البيانات، نضع المفتاح fallback
                        if (!map.containsKey("reportId"))
                            map.put("reportId", c.getKey());
                        // نضع علامة لتمييزه كسجل تطابق
                        map.put("_isMatchRecord", true);
                        reportsList.add(map);
                    }
                    // ترتيب من الأحدث
                    reportsList.sort((a, b) -> {
                        Object ta = a.get("timestamp"); Object tb = b.get("timestamp");
                        long la = ta instanceof Long ? (Long) ta : 0L;
                        long lb = tb instanceof Long ? (Long) tb : 0L;
                        return Long.compare(lb, la);
                    });
                    runOnUiThread(() -> {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        boolean empty = reportsList.isEmpty();
                        if (tvEmpty != null) tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                        if (recyclerView != null) recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
                        if (adapter != null) adapter.notifyDataSetChanged();
                    });
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {
                    if (progressBar != null)
                        runOnUiThread(() -> progressBar.setVisibility(View.GONE));
                }
            });
    }

    // ════════════════════════════════════════════════════════
    //  إجراءات البلاغات
    // ════════════════════════════════════════════════════════

    private void onReportAction(HashMap<String, Object> report, String action) {
        RoleManager role = RoleManager.get();
        String reportId = (String) report.get("reportId");
        if (reportId == null) return;

        // [إصلاح خطأ-02] — كشف سجلات التطابق
        boolean isMatchRecord = Boolean.TRUE.equals(report.get("_isMatchRecord"))
                || report.containsKey("foundId");

        switch (action) {
            case "approve":
                if (!role.canApproveReports()) {
                    showPermError("الموافقة على البلاغات"); return;
                }
                if (isMatchRecord) {
                    // [إصلاح خطأ-02] — تأكيد التطابق بدلاً من approve عادي
                    confirmMatchRecord(report);
                } else {
                    updateReportStatus(reportId, "approved");
                }
                break;

            case "reject":
                if (!role.canApproveReports()) {
                    showPermError("الموافقة/رفض البلاغات"); return;
                }
                if (isMatchRecord) {
                    rejectMatchRecord(report);
                } else {
                    updateReportStatus(reportId, "rejected");
                }
                break;

            case "delete":
                if (!role.canDeleteReports()) {
                    showPermError("حذف البلاغات"); return;
                }
                confirmDelete(reportId);
                break;

            case "edit":
                if (!role.canEditReports()) {
                    showPermError("تعديل البلاغات"); return;
                }
                startEditReport(report);
                break;

            case "resolve":
                if (!role.canApproveReports() && !role.isAdmin()) {
                    showPermError("تعليم البلاغ كمحلول"); return;
                }
                updateReportStatus(reportId, "resolved");
                break;

            case "match":
                String matchName = (String) report.getOrDefault("name", "");
                String matchUid  = (String) report.getOrDefault("reporterUid", "");
                String matchEmb  = (String) report.getOrDefault("faceEmbedding", "");
                CrossMatchManager.matchReportWithFoundPersons(
                        reportId, matchUid, matchName, matchEmb);
                Toast.makeText(this, "🔗 جاري البحث عن تطابق...", Toast.LENGTH_SHORT).show();
                break;

            case "amber":
                String amberName = (String) report.getOrDefault("name", "");
                String amberGov  = (String) report.getOrDefault("governorate", "");
                String amberImg  = (String) report.getOrDefault("photoUrl", "");
                Object ageObj    = report.get("age");
                int amberAge     = ageObj instanceof Long ? ((Long) ageObj).intValue() : 0;
                AmberAlertManager.issueAlert(
                        this, reportId, amberName, amberGov, amberImg, amberAge);
                Toast.makeText(this, "✅ تنبيه أصفر أُرسل", Toast.LENGTH_SHORT).show();
                break;

            default:
                if (!isMatchRecord)
                    startActivity(new Intent(this, CaseDetailActivity.class)
                        .putExtra("reportId", reportId));
                break;
        }
    }

    // ════════════════════════════════════════════════════════
    //  [خطأ-02] تأكيد التطابق — confirmMatchRecord
    //
    //  يُعدَّل:
    //   reports/{reportId}/status        → "resolved"
    //   found_persons/{foundId}/status   → "matched"
    //   matches/{matchKey}/status        → "confirmed"
    //  ويُبلَّغ المستخدمان بالتأكيد
    // ════════════════════════════════════════════════════════

    private void confirmMatchRecord(HashMap<String, Object> matchData) {
        String origReportId = (String) matchData.get("reportId");
        String foundId      = (String) matchData.get("foundId");
        String matchKey     = (String) matchData.get("_matchKey");
        String reporterUid  = (String) matchData.get("reporterUid");
        String finderUid    = (String) matchData.get("finderUid");
        Object simObj       = matchData.get("similarity");
        float  sim          = simObj instanceof Double ? ((Double) simObj).floatValue()
                            : simObj instanceof Float  ? (Float) simObj : 0f;
        int    percent      = (int)(sim * 100);

        if (origReportId == null || foundId == null) {
            Toast.makeText(this,
                "❌ بيانات التطابق ناقصة — لا يمكن التأكيد",
                Toast.LENGTH_LONG).show();
            return;
        }

        String msg = "تأكيد تطابق البلاغ مع الشخص المعثور عليه\n"
                   + "نسبة التطابق: " + percent + "%\n\n"
                   + "سيتم:\n"
                   + "• تعليم البلاغ كـ «محلول»\n"
                   + "• إشعار أسرة المفقود والمُبلِّغ عن العثور\n"
                   + "• منح نقاط للطرفين";

        new AlertDialog.Builder(this)
            .setTitle("✅ تأكيد التطابق")
            .setMessage(msg)
            .setPositiveButton("تأكيد", (d, w) ->
                doConfirmMatch(origReportId, foundId, matchKey,
                               reporterUid, finderUid, percent))
            .setNegativeButton("إلغاء", null)
            .show();
    }

    private void doConfirmMatch(String reportId, String foundId, String matchKey,
                                 String reporterUid, String finderUid, int percent) {
        long now = System.currentTimeMillis();
        String reviewerUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";

        // 1. تحديث reports/{reportId}
        Map<String, Object> reportUpdate = new HashMap<>();
        reportUpdate.put("status",      "resolved");
        reportUpdate.put("resolvedAt",  now);
        reportUpdate.put("resolvedBy",  reviewerUid);
        reportUpdate.put("matchedWith", foundId);
        FirebaseDatabase.getInstance()
            .getReference("reports").child(reportId)
            .updateChildren(reportUpdate);

        // 2. تحديث found_persons/{foundId}
        Map<String, Object> foundUpdate = new HashMap<>();
        foundUpdate.put("status",      "matched");
        foundUpdate.put("matchedWith", reportId);
        foundUpdate.put("matchedAt",   now);
        FirebaseDatabase.getInstance()
            .getReference("found_persons").child(foundId)
            .updateChildren(foundUpdate);

        // 3. تحديث matches/{matchKey}
        if (matchKey != null) {
            Map<String, Object> matchUpdate = new HashMap<>();
            matchUpdate.put("status",      "confirmed");
            matchUpdate.put("confirmedAt", now);
            matchUpdate.put("confirmedBy", reviewerUid);
            FirebaseDatabase.getInstance()
                .getReference("matches").child(matchKey)
                .updateChildren(matchUpdate);
        }

        // 4. إشعار صاحب بلاغ المفقود
        if (reporterUid != null && !reporterUid.isEmpty()) {
            sendConfirmedMatchNotif(reporterUid,
                "🎉 تم تأكيد تطابق بلاغك! شخص وجده أحد المستخدمين يطابق المفقود بنسبة "
                + percent + "%", reportId, foundId);
            PointsManager.addPointsForUser(reporterUid,
                PointsManager.ACTION_MATCH_CONFIRMED,
                "تأكيد تطابق — بلاغ المفقود");
        }

        // 5. إشعار المُبلِّغ عن العثور
        if (finderUid != null && !finderUid.isEmpty()
                && !finderUid.equals(reporterUid)) {
            sendConfirmedMatchNotif(finderUid,
                "🙌 بلاغ العثور الذي أضفته طابق مفقوداً مؤكداً! نسبة التطابق "
                + percent + "%", reportId, foundId);
            PointsManager.addPointsForUser(finderUid,
                PointsManager.ACTION_MATCH_CONFIRMED,
                "تأكيد تطابق — بلاغ العثور");
        }

        // [إصلاح 3.3] Audit Log — تسجيل كل إجراء إداري
        Map<String, Object> auditLog = new HashMap<>();
        auditLog.put("action",     "confirm_match");
        auditLog.put("adminUid",   reviewerUid);
        auditLog.put("reportId",   reportId);
        auditLog.put("foundId",    foundId);
        auditLog.put("percent",    percent);
        auditLog.put("timestamp",  now);
        FirebaseDatabase.getInstance()
            .getReference("admin_logs")
            .push().setValue(auditLog);

        Toast.makeText(this, "✅ تم تأكيد التطابق وإشعار المستخدمين",
                Toast.LENGTH_LONG).show();

        // إعادة تحميل التبويب
        loadMatchesTab();
    }

    private void sendConfirmedMatchNotif(String uid, String message,
                                          String reportId, String foundId) {
        HashMap<String, Object> notif = new HashMap<>();
        notif.put("type",      "match_confirmed");
        notif.put("reportId",  reportId);
        notif.put("foundId",   foundId);
        notif.put("message",   message);
        notif.put("timestamp", System.currentTimeMillis());
        notif.put("read",      false);
        FirebaseDatabase.getInstance()
            .getReference("notifications").child(uid)
            .push().setValue(notif);
    }

    private void rejectMatchRecord(HashMap<String, Object> matchData) {
        String matchKey = (String) matchData.get("_matchKey");
        if (matchKey == null) return;
        new AlertDialog.Builder(this)
            .setTitle("رفض التطابق")
            .setMessage("هل أنت متأكد من رفض هذا التطابق؟")
            .setPositiveButton("رفض", (d, w) -> {
                Map<String, Object> upd = new HashMap<>();
                upd.put("status", "rejected");
                upd.put("rejectedAt", System.currentTimeMillis());
                FirebaseDatabase.getInstance()
                    .getReference("matches").child(matchKey)
                    .updateChildren(upd);
                Toast.makeText(this, "تم رفض التطابق", Toast.LENGTH_SHORT).show();
                loadMatchesTab();
            })
            .setNegativeButton("إلغاء", null).show();
    }

    // ════════════════════════════════════════════════════════
    //  تحديث حالة البلاغ العادي
    // ════════════════════════════════════════════════════════

    private void updateReportStatus(String reportId, String status) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status",     status);
        updates.put("reviewedAt", System.currentTimeMillis());
        updates.put("reviewedBy", FirebaseAuth.getInstance().getCurrentUser() != null
            ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "");
        reportsRef.child(reportId).updateChildren(updates)
            .addOnSuccessListener(v -> {
                Toast.makeText(this, "✅ تم تحديث الحالة", Toast.LENGTH_SHORT).show();
                if ("approved".equals(status))
                    triggerCrossMatchAfterApproval(reportId);
            })
            .addOnFailureListener(e ->
                Toast.makeText(this, "❌ فشل: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
    }

    private void triggerCrossMatchAfterApproval(String reportId) {
        reportsRef.child(reportId)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snap) {
                    String emb        = snap.child("faceEmbedding").getValue(String.class);
                    String reporterUid = snap.child("reporterId").getValue(String.class);
                    String personName  = snap.child("personName").getValue(String.class);
                    String reportType  = snap.child("reportType").getValue(String.class);
                    if (emb == null || emb.isEmpty()) return;
                    if (personName == null) personName = "مجهول";
                    if ("found".equals(reportType) || "found_person".equals(reportType)) {
                        CrossMatchManager.matchFoundPersonWithReports(
                                reportId, reporterUid, emb);
                    } else {
                        CrossMatchManager.matchReportWithFoundPersons(
                                reportId, reporterUid, personName, emb);
                    }
                }
                @Override
                public void onCancelled(@NonNull DatabaseError e) {}
            });
    }

    private void confirmDelete(String reportId) {
        new AlertDialog.Builder(this)
            .setTitle("تأكيد الحذف")
            .setMessage("هل أنت متأكد من حذف هذا البلاغ؟")
            .setPositiveButton("حذف", (d, w) ->
                reportsRef.child(reportId).child("status").setValue("deleted"))
            .setNegativeButton("إلغاء", null).show();
    }

    private void startEditReport(HashMap<String, Object> report) {
        startActivity(new Intent(this, ReportUpdateActivity.class)
            .putExtra("reportId", (String) report.get("reportId")));
    }

    private void showPermError(String action) {
        Toast.makeText(this, "⛔ ليس لديك صلاحية: " + action,
                Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (reportsRef != null && currentReportsListener != null)
            reportsRef.removeEventListener(currentReportsListener);
        if (foundPersonsRef != null && foundPersonsListener != null)
            foundPersonsRef.removeEventListener(foundPersonsListener);
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }

    // ═══════════════════════════════════════════════════════
    //  Inner Adapter
    // ═══════════════════════════════════════════════════════

    interface AdminActionListener {
        void onAction(HashMap<String, Object> report, String action);
    }

    static class AdminAdapter
            extends RecyclerView.Adapter<AdminAdapter.VH> {

        private final List<HashMap<String, Object>> items;
        private final AdminActionListener listener;
        private final SimpleDateFormat sdf =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", new Locale("ar"));

        AdminAdapter(List<HashMap<String, Object>> items,
                     AdminActionListener listener) {
            this.items    = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_report, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            HashMap<String, Object> r = items.get(pos);
            android.content.Context ctx = h.itemView.getContext();

            boolean isMatch = Boolean.TRUE.equals(r.get("_isMatchRecord"));

            // عرض اسم الشخص أو معلومات التطابق
            if (isMatch) {
                Object simObj = r.get("similarity");
                float sim = simObj instanceof Double ? ((Double) simObj).floatValue()
                          : simObj instanceof Float  ? (Float) simObj : 0f;
                h.tvName.setText("🔗 تطابق " + (int)(sim * 100) + "%");
                h.tvAddr.setText("بلاغ: " + s(r, "reportId", "")
                               + " ↔ معثور: " + s(r, "foundId", ""));
                h.tvEmail.setText("الحالة: " + s(r, "status", "pending_review"));
            } else {
                h.tvName.setText(s(r, "name", "مجهول"));
                h.tvAddr.setText(s(r, "lastSeenAddress", s(r, "address", "")));
                h.tvEmail.setText(s(r, "reporterPhone", s(r, "reporterEmail", "")));
            }

            Object ts = r.get("timestamp");
            h.tvTime.setText(ts instanceof Long
                ? sdf.format(new Date((Long) ts)) : "");

            String photoUrl = s(r, "photoUrl", "");
            if (!photoUrl.isEmpty()) {
                coil.Coil.imageLoader(ctx).enqueue(
                    new coil.request.ImageRequest.Builder(ctx)
                        .data(photoUrl).target(h.ivPhoto)
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person).build());
            } else {
                h.ivPhoto.setImageResource(R.drawable.ic_person);
            }

            RoleManager role = RoleManager.get();

            // للتطابقات: زر "موافقة" = تأكيد التطابق، زر "رفض" = رفض
            h.btnApprove.setVisibility(role.canApproveReports()
                    ? View.VISIBLE : View.GONE);
            h.btnReject.setVisibility(role.canApproveReports()
                    ? View.VISIBLE : View.GONE);
            h.btnEdit.setVisibility(!isMatch && role.canEditReports()
                    ? View.VISIBLE : View.GONE);
            h.btnDelete.setVisibility(!isMatch && role.canDeleteReports()
                    ? View.VISIBLE : View.GONE);
            h.btnBan.setVisibility(!isMatch && role.canBanUsers()
                    ? View.VISIBLE : View.GONE);

            if (isMatch) {
                h.btnApprove.setText("✅ تأكيد التطابق");
                h.btnReject.setText("❌ رفض");
            } else {
                h.btnApprove.setText("موافقة");
                h.btnReject.setText("رفض");
            }

            h.btnApprove.setOnClickListener(v -> listener.onAction(r, "approve"));
            h.btnReject.setOnClickListener(v  -> listener.onAction(r, "reject"));
            h.btnEdit.setOnClickListener(v    -> listener.onAction(r, "edit"));
            h.btnDelete.setOnClickListener(v  -> listener.onAction(r, "delete"));
            h.btnBan.setOnClickListener(v     -> listener.onAction(r, "ban"));
            h.itemView.setOnClickListener(v   -> listener.onAction(r, "view"));
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            android.widget.ImageView ivPhoto;
            android.widget.TextView  tvName, tvAddr, tvEmail, tvTime;
            com.google.android.material.button.MaterialButton
                    btnApprove, btnReject, btnEdit, btnDelete, btnBan;

            VH(View v) {
                super(v);
                ivPhoto    = v.findViewById(R.id.iv_admin_photo);
                tvName     = v.findViewById(R.id.tv_admin_name);
                tvAddr     = v.findViewById(R.id.tv_admin_addr);
                tvEmail    = v.findViewById(R.id.tv_admin_email);
                tvTime     = v.findViewById(R.id.tv_admin_time);
                btnApprove = v.findViewById(R.id.btn_approve);
                btnReject  = v.findViewById(R.id.btn_reject);
                btnEdit    = v.findViewById(R.id.btn_edit);
                btnDelete  = v.findViewById(R.id.btn_delete);
                btnBan     = v.findViewById(R.id.btn_ban);
            }
        }

        private static String s(HashMap<String, Object> m, String k, String def) {
            Object v = m.get(k);
            return (v instanceof String && !((String) v).isEmpty())
                    ? (String) v : def;
        }
    }
}
