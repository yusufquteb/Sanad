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
import androidx.annotation.NonNull;
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
    // التبويب الحالي النشط
    private String  currentTabStatus = "pending";

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
        reportsRef      = FirebaseDatabase.getInstance().getReference("reports");
        foundPersonsRef = FirebaseDatabase.getInstance().getReference("found_persons");

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
            tabLayout.addTab(tabLayout.newTab().setText("📘 استيراد FB"));
        if (role.isAdmin())
            tabLayout.addTab(tabLayout.newTab().setText("المحادثات"));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                String title = tab.getText() != null ? tab.getText().toString() : "";
                switch (title) {
                    case "معلقة":
                        isMatchesTab = false; currentTabStatus = "pending";
                        if (recyclerView != null) recyclerView.setVisibility(android.view.View.VISIBLE);
                        hideFbScrollView();
                        loadReports("pending");
                        break;
                    case "موافق عليها":
                        isMatchesTab = false; currentTabStatus = "approved";
                        loadReports("approved");
                        break;
                    case "التطابقات":
                        isMatchesTab = true; currentTabStatus = "matches";
                        loadMatchesTab();
                        break;
                    case "محذوفة":
                        isMatchesTab = false; currentTabStatus = "deleted";
                        loadReports("deleted");
                        break;
                    case "الأعضاء":
                        startActivity(new Intent(AdminActivity.this, MembersActivity.class));
                        break;
                    case "المديرون":
                        startActivity(new Intent(AdminActivity.this, ManagersActivity.class));
                        break;
                    case "📘 استيراد FB":
                        isMatchesTab = false; currentTabStatus = "fb_import";
                        loadFbImportTab();
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

        // 4+5. [Phase 3] إشعار متقاطع — يجلب بيانات الطرف الآخر
        final String fReporterUid = reporterUid;
        final String fFinderUid   = finderUid;
        final int    fPercent     = percent;

        FirebaseDatabase.getInstance().getReference("reports").child(reportId)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot rSnap) {
                    String missingName  = safeStr(rSnap, "personName");
                    String missingPhoto = safeStr(rSnap, "photoUrl");
                    String missingAddr  = safeStr(rSnap, "lastSeenAddress");
                    String shortR = reportId.length() > 4
                        ? "SND-" + reportId.substring(reportId.length() - 4).toUpperCase() : reportId;

                    FirebaseDatabase.getInstance().getReference("found_persons").child(foundId)
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override public void onDataChange(@NonNull DataSnapshot fSnap) {
                                String foundName  = safeStr(fSnap, "name");
                                if (foundName.isEmpty()) foundName = "معثور";
                                String foundAddr  = safeStr(fSnap, "lastSeenAddress");
                                String shortF = foundId.length() > 4
                                    ? "SND-" + foundId.substring(foundId.length() - 4).toUpperCase() : foundId;

                                // لصاحب المفقود: بيانات المعثور
                                if (fReporterUid != null && !fReporterUid.isEmpty()) {
                                    sendConfirmedMatchNotif(fReporterUid,
                                        "🎉 تم تأكيد التطابق " + fPercent + "%\nتم العثور على \"" + foundName + "\" في " + foundAddr,
                                        reportId, foundId);
                                    PointsManager.addPointsForUser(fReporterUid,
                                        PointsManager.ACTION_MATCH_CONFIRMED, "تأكيد تطابق");
                                }
                                // لصاحب المعثور: بيانات المفقود
                                if (fFinderUid != null && !fFinderUid.isEmpty()
                                        && !fFinderUid.equals(fReporterUid)) {
                                    sendConfirmedMatchNotif(fFinderUid,
                                        "🙌 تأكيد تطابق " + fPercent + "%\nالشخص الذي وجدته هو \"" + missingName + "\" من " + missingAddr,
                                        reportId, foundId);
                                    PointsManager.addPointsForUser(fFinderUid,
                                        PointsManager.ACTION_MATCH_CONFIRMED, "تأكيد تطابق");
                                }
                            }
                            @Override public void onCancelled(@NonNull DatabaseError e) {}
                        });
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });

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
        notif.put("title",     "🎉 تم تأكيد التطابق");
        notif.put("reportId",  reportId);
        notif.put("foundId",   foundId);
        notif.put("message",   message);
        notif.put("timestamp", System.currentTimeMillis());
        notif.put("read",      false);
        FirebaseDatabase.getInstance()
            .getReference("notifications").child(uid)
            .push().setValue(notif);
    }

    /** [Phase 3] قراءة String آمنة من DataSnapshot */
    private static String safeStr(DataSnapshot snap, String key) {
        Object v = snap.child(key).getValue();
        return (v instanceof String) ? (String) v : "";
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

    /**
     * [إصلاح] confirmDelete
     * • من تبويب "محذوفة" → حذف نهائي (removeValue) من reports/ و found_persons/
     * • من أي تبويب آخر  → حذف ناعم (status = "deleted")
     */
    private void confirmDelete(String reportId) {
        boolean isPermanent = "deleted".equals(currentTabStatus);
        String msg = isPermanent
            ? "سيُحذف هذا البلاغ نهائياً ولا يمكن التراجع. هل أنت متأكد؟"
            : "سيُنقل هذا البلاغ إلى المحذوفات. هل أنت متأكد؟";
        String btnTxt = isPermanent ? "حذف نهائي" : "حذف";

        new AlertDialog.Builder(this)
            .setTitle(isPermanent ? "⚠️ حذف نهائي" : "تأكيد الحذف")
            .setMessage(msg)
            .setPositiveButton(btnTxt, (d, w) -> {
                if (isPermanent) {
                    // حذف نهائي من reports/ و found_persons/ و Room
                    reportsRef.child(reportId).removeValue()
                        .addOnSuccessListener(v -> {
                            // حذف من found_persons أيضاً لو كان موجوداً
                            foundPersonsRef.child(reportId).removeValue();
                            // حذف من Room
                            new Thread(() -> {
                                try {
                                    com.missingpersons.app.models.AppDatabase
                                        .getInstance(AdminActivity.this)
                                        .reportDao().delete(reportId);
                                } catch (Exception ignored) {}
                            }).start();
                            Toast.makeText(AdminActivity.this,
                                "✅ تم الحذف النهائي", Toast.LENGTH_SHORT).show();
                            loadReports("deleted");
                        })
                        .addOnFailureListener(e -> Toast.makeText(AdminActivity.this,
                            "❌ فشل الحذف: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                } else {
                    // حذف ناعم
                    reportsRef.child(reportId).child("status").setValue("deleted")
                        .addOnSuccessListener(v -> {
                            Toast.makeText(AdminActivity.this,
                                "تم نقله للمحذوفات", Toast.LENGTH_SHORT).show();
                        });
                }
            })
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

        private static final int TYPE_REPORT = 0;
        private static final int TYPE_MATCH  = 1;

        @Override
        public int getItemViewType(int pos) {
            HashMap<String, Object> r = items.get(pos);
            return Boolean.TRUE.equals(r.get("_isMatchRecord")) ? TYPE_MATCH : TYPE_REPORT;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layoutId = (viewType == TYPE_MATCH)
                ? R.layout.item_match_comparison
                : R.layout.item_admin_report;
            View v = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
            return new VH(v, viewType == TYPE_MATCH);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            HashMap<String, Object> r = items.get(pos);
            android.content.Context ctx = h.itemView.getContext();
            boolean isMatch = Boolean.TRUE.equals(r.get("_isMatchRecord"));

            if (isMatch) {
                bindMatchItem(h, r, ctx);
            } else {
                bindReportItem(h, r, ctx);
            }
        }

        /** ربط كارت التطابق الجديد بـ item_match_comparison.xml */
        private void bindMatchItem(@NonNull VH h, HashMap<String, Object> r,
                                    android.content.Context ctx) {
            // ── نسبة التطابق الحقيقية ──
            Object simObj = r.get("similarity");
            float sim = 0f;
            if (simObj instanceof Double)  sim = ((Double)  simObj).floatValue();
            else if (simObj instanceof Float)   sim = (Float) simObj;
            else if (simObj instanceof Long)    sim = ((Long) simObj) / 100f;
            else if (simObj instanceof Integer) sim = ((Integer) simObj) / 100f;
            int percent = Math.min(100, (int)(sim * 100));

            if (h.chipMatchScore != null)
                h.chipMatchScore.setText(percent + "%");
            if (h.progressMatch != null)
                h.progressMatch.setProgress(percent);

            // ── رقم البلاغ المفقود (SND-XXXX) ──
            String rawReportId = s(r, "reportId", "—");
            // استخدم الـ shortId المحفوظ أولاً، ثم احسبه من الـ key
            String shortReport = s(r, "shortReportId", "");
            if (shortReport.isEmpty()) {
                shortReport = rawReportId.length() > 8
                    ? "SND-" + rawReportId.substring(rawReportId.length() - 4).toUpperCase()
                    : rawReportId;
            }
            // ── رقم المعثور ──
            String rawFoundId = s(r, "foundId", "—");
            String shortFound = s(r, "shortFoundId", "");
            if (shortFound.isEmpty()) {
                shortFound = rawFoundId.length() > 8
                    ? "SND-" + rawFoundId.substring(rawFoundId.length() - 4).toUpperCase()
                    : rawFoundId;
            }

            if (h.tvMatchReportId != null)
                h.tvMatchReportId.setText("بلاغ مفقود: " + shortReport);
            if (h.tvMatchFoundId != null)
                h.tvMatchFoundId.setText("بلاغ معثور: " + shortFound);

            // أسماء (يُجلبان لاحقاً من Firebase حسب الـ ID)
            if (h.tvMatchReportName != null)
                h.tvMatchReportName.setText(s(r, "personName", s(r, "reporterName", "مفقود")));
            if (h.tvMatchFoundName != null)
                h.tvMatchFoundName.setText(s(r, "finderName", "معثور"));

            // ── الحالة مترجمة ──
            String statusRaw = s(r, "status", "pending_review");
            String statusAr  = translateMatchStatus(statusRaw);
            if (h.tvMatchStatus != null) {
                h.tvMatchStatus.setText(statusAr);
                // ألوان دلالية
                int bgColor, txtColor;
                switch (statusRaw) {
                    case "confirmed":
                        bgColor = 0xFFE8F5E9; txtColor = 0xFF2E7D32; break;
                    case "rejected":
                        bgColor = 0xFFFFEBEE; txtColor = 0xFFC62828; break;
                    default: // pending_review
                        bgColor = 0xFFFFF3E0; txtColor = 0xFFF57C00; break;
                }
                h.tvMatchStatus.setBackgroundColor(bgColor);
                h.tvMatchStatus.setTextColor(txtColor);
            }

            // ── التوقيت ──
            Object ts = r.get("timestamp");
            if (h.tvMatchTime != null)
                h.tvMatchTime.setText(ts instanceof Long ? sdf.format(new Date((Long) ts)) : "");

            // ── صور ── جلب من الـ cache أو مباشرة من Firebase
            String reportPhotoUrl = s(r, "reportPhotoUrl", s(r, "photoUrl", ""));
            String foundPhotoUrl  = s(r, "foundPhotoUrl",  s(r, "foundPhoto", ""));
            String rawRepId = s(r, "reportId", "");
            String rawFndId = s(r, "foundId",  "");

            if (h.ivMatchReport != null) {
                if (!reportPhotoUrl.isEmpty()) {
                    loadImg(ctx, reportPhotoUrl, h.ivMatchReport);
                } else if (!rawRepId.isEmpty()) {
                    // جلب مباشر من reports/
                    final android.widget.ImageView iv = h.ivMatchReport;
                    com.google.firebase.database.FirebaseDatabase.getInstance()
                        .getReference("reports").child(rawRepId)
                        .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                            @Override public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot snap) {
                                // [إصلاح] جرّب photoUrl ثم imageUrl ثم imageUrls[0]
                                String url = snap.child("photoUrl").getValue(String.class);
                                if (url == null || url.isEmpty())
                                    url = snap.child("imageUrl").getValue(String.class);
                                if (url == null || url.isEmpty()) {
                                    Object arr = snap.child("imageUrls").getValue();
                                    if (arr instanceof java.util.List && !((java.util.List<?>)arr).isEmpty())
                                        url = ((java.util.List<?>)arr).get(0).toString();
                                }
                                if (url != null && !url.isEmpty()) loadImg(ctx, url, iv);
                                else iv.setImageResource(R.drawable.ic_person);
                            }
                            @Override public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError e) {
                                iv.setImageResource(R.drawable.ic_person);
                            }
                        });
                } else {
                    h.ivMatchReport.setImageResource(R.drawable.ic_person);
                }
            }
            if (h.ivMatchFound != null) {
                if (!foundPhotoUrl.isEmpty()) {
                    loadImg(ctx, foundPhotoUrl, h.ivMatchFound);
                } else if (!rawFndId.isEmpty()) {
                    // جلب مباشر من found_persons/
                    final android.widget.ImageView iv2 = h.ivMatchFound;
                    com.google.firebase.database.FirebaseDatabase.getInstance()
                        .getReference("found_persons").child(rawFndId)
                        .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                            @Override public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot snap) {
                                String url = snap.child("photoUrl").getValue(String.class);
                                if (url == null || url.isEmpty())
                                    url = snap.child("imageUrl").getValue(String.class);
                                if (url == null || url.isEmpty()) {
                                    Object arr = snap.child("imageUrls").getValue();
                                    if (arr instanceof java.util.List && !((java.util.List<?>)arr).isEmpty())
                                        url = ((java.util.List<?>)arr).get(0).toString();
                                }
                                if (url != null && !url.isEmpty()) loadImg(ctx, url, iv2);
                                else iv2.setImageResource(R.drawable.ic_person);
                            }
                            @Override public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError e) {
                                iv2.setImageResource(R.drawable.ic_person);
                            }
                        });
                } else {
                    h.ivMatchFound.setImageResource(R.drawable.ic_person);
                }
            }

            // ── [جديد] جلب تفاصيل كاملة من Firebase ──
            final VH fh = h;
            final android.content.Context fCtx = ctx;
            final String rawRId = s(r, "reportId", "");
            final String rawFId = s(r, "foundId",  "");

            if (!rawRId.isEmpty()) {
                com.google.firebase.database.FirebaseDatabase.getInstance()
                    .getReference("reports").child(rawRId)
                    .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                        @Override public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot snap) {
                            // الصورة الرئيسية
                            String mu = snap.child("imageUrl").getValue(String.class);
                            if (mu == null || mu.isEmpty()) {
                                Object a = snap.child("imageUrls").getValue();
                                if (a instanceof java.util.List && !((java.util.List<?>)a).isEmpty())
                                    mu = ((java.util.List<?>)a).get(0).toString();
                            }
                            if (mu != null && !mu.isEmpty() && fh.ivMatchReport != null)
                                loadImg(fCtx, mu, fh.ivMatchReport);
                            // الصور الإضافية
                            if (fh.llReportExtraPhotos != null) {
                                fh.llReportExtraPhotos.removeAllViews();
                                Object a = snap.child("imageUrls").getValue();
                                if (a instanceof java.util.List) {
                                    java.util.List<?> urls = (java.util.List<?>)a;
                                    for (int i = 1; i < urls.size(); i++) {
                                        android.widget.ImageView iv = new android.widget.ImageView(fCtx);
                                        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(dpToPx(50, fCtx), dpToPx(50, fCtx));
                                        lp.setMargins(0, 0, dpToPx(6, fCtx), 0);
                                        iv.setLayoutParams(lp); iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                                        loadImg(fCtx, urls.get(i).toString(), iv);
                                        fh.llReportExtraPhotos.addView(iv);
                                    }
                                }
                            }
                            // تفاصيل
                            String nm = snap.child("personName").getValue(String.class);
                            if (nm != null && !nm.isEmpty() && fh.tvMatchReportName != null) fh.tvMatchReportName.setText(nm);
                            Object ageRaw = snap.child("personAge").getValue();
                            if (fh.tvMatchReportAge != null) {
                                int age = ageRaw instanceof Long ? ((Long)ageRaw).intValue() : ageRaw instanceof Integer ? (Integer)ageRaw : 0;
                                if (age > 0) fh.tvMatchReportAge.setText("العمر: " + age);
                            }
                            String gend = snap.child("personGender").getValue(String.class);
                            if (gend == null) gend = snap.child("gender").getValue(String.class);
                            if (fh.tvMatchReportGender != null && gend != null) fh.tvMatchReportGender.setText("النوع: " + gend);
                            String gov = snap.child("governorate").getValue(String.class);
                            String city = snap.child("city").getValue(String.class);
                            String addr = snap.child("manualAddress").getValue(String.class);
                            if (fh.tvMatchReportAddr != null) {
                                StringBuilder sb = new StringBuilder();
                                if (gov  != null && !gov.isEmpty())  sb.append(gov);
                                if (city != null && !city.isEmpty()) sb.append(" - ").append(city);
                                if (sb.length() == 0 && addr != null) sb.append(addr);
                                fh.tvMatchReportAddr.setText(sb.toString());
                            }
                            String phone = snap.child("phone").getValue(String.class);
                            if (fh.tvMatchReportPhone != null) {
                                Boolean pub = snap.child("phonePublic").getValue(Boolean.class);
                                fh.tvMatchReportPhone.setText((phone != null && !phone.isEmpty() && (pub==null||pub)) ? "📞 " + phone : "—");
                            }
                        }
                        @Override public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError e) {}
                    });
            }

            if (!rawFId.isEmpty()) {
                com.google.firebase.database.FirebaseDatabase.getInstance()
                    .getReference("found_persons").child(rawFId)
                    .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                        @Override public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot snap) {
                            String mu = snap.child("photoUrl").getValue(String.class);
                            if (mu == null || mu.isEmpty()) mu = snap.child("imageUrl").getValue(String.class);
                            if (mu == null || mu.isEmpty()) {
                                Object a = snap.child("imageUrls").getValue();
                                if (a instanceof java.util.List && !((java.util.List<?>)a).isEmpty())
                                    mu = ((java.util.List<?>)a).get(0).toString();
                            }
                            if (mu != null && !mu.isEmpty() && fh.ivMatchFound != null) loadImg(fCtx, mu, fh.ivMatchFound);
                            if (fh.llFoundExtraPhotos != null) {
                                fh.llFoundExtraPhotos.removeAllViews();
                                Object a = snap.child("imageUrls").getValue();
                                if (a instanceof java.util.List) {
                                    java.util.List<?> urls = (java.util.List<?>)a;
                                    for (int i = 1; i < urls.size(); i++) {
                                        android.widget.ImageView iv = new android.widget.ImageView(fCtx);
                                        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(dpToPx(50, fCtx), dpToPx(50, fCtx));
                                        lp.setMargins(0, 0, dpToPx(6, fCtx), 0);
                                        iv.setLayoutParams(lp); iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                                        loadImg(fCtx, urls.get(i).toString(), iv);
                                        fh.llFoundExtraPhotos.addView(iv);
                                    }
                                }
                            }
                            String nm = snap.child("name").getValue(String.class);
                            if (nm != null && !nm.isEmpty() && fh.tvMatchFoundName != null) fh.tvMatchFoundName.setText(nm);
                            Object ageRaw = snap.child("age").getValue();
                            if (fh.tvMatchFoundAge != null) {
                                int age = ageRaw instanceof Long ? ((Long)ageRaw).intValue() : ageRaw instanceof Integer ? (Integer)ageRaw : 0;
                                if (age > 0) fh.tvMatchFoundAge.setText("العمر: " + age);
                            }
                            String gend = snap.child("gender").getValue(String.class);
                            if (fh.tvMatchFoundGender != null && gend != null) fh.tvMatchFoundGender.setText("النوع: " + gend);
                            String gov = snap.child("governorate").getValue(String.class);
                            if (fh.tvMatchFoundAddr != null && gov != null) fh.tvMatchFoundAddr.setText(gov);
                            String phone = snap.child("phone").getValue(String.class);
                            if (fh.tvMatchFoundPhone != null && phone != null && !phone.isEmpty()) fh.tvMatchFoundPhone.setText("📞 " + phone);
                        }
                        @Override public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError e) {}
                    });
            }

            // ── [جديد] الضغط على صف المفقود → فتح CaseDetail ──
            final String fRawRepId = rawRId, fRawFndId = rawFId;
            if (h.rowMissing != null && !fRawRepId.isEmpty()) {
                h.rowMissing.setOnClickListener(v -> {
                    android.content.Intent i = new android.content.Intent(
                        (android.content.Context)ctx, CaseDetailActivity.class);
                    i.putExtra("reportId", fRawRepId);
                    i.putExtra("node", "reports");
                    ((android.content.Context)ctx).startActivity(i);
                });
            }
            if (h.rowFound != null && !fRawFndId.isEmpty()) {
                h.rowFound.setOnClickListener(v -> {
                    android.content.Intent i = new android.content.Intent(
                        (android.content.Context)ctx, CaseDetailActivity.class);
                    i.putExtra("reportId", fRawFndId);
                    i.putExtra("node", "found_persons");
                    ((android.content.Context)ctx).startActivity(i);
                });
            }

            // ── [جديد] share match card ──
            if (h.btnShareMatch != null) {
                final android.view.View cardView = h.itemView;
                h.btnShareMatch.setOnClickListener(v -> shareMatchCard(cardView, ctx));
            }

            // ── أزرار ──
            RoleManager role = RoleManager.get();
            if (h.btnApprove != null) {
                h.btnApprove.setVisibility(role.canApproveReports() ? View.VISIBLE : View.GONE);
                h.btnApprove.setOnClickListener(v -> listener.onAction(r, "approve"));
            }
            if (h.btnReject != null) {
                h.btnReject.setVisibility(role.canApproveReports() ? View.VISIBLE : View.GONE);
                h.btnReject.setOnClickListener(v -> listener.onAction(r, "reject"));
            }
        }

        private static String translateMatchStatus(String raw) {
            if (raw == null) return "قيد المراجعة";
            switch (raw) {
                case "pending_review": return "قيد المراجعة";
                case "confirmed":      return "مؤكد ✅";
                case "rejected":       return "مرفوض ❌";
                case "auto_matched":   return "تطابق تلقائي";
                default:               return raw;
            }
        }

        private void loadImg(android.content.Context ctx, String url,
                              android.widget.ImageView iv) {
            coil.Coil.imageLoader(ctx).enqueue(
                new coil.request.ImageRequest.Builder(ctx)
                    .data(url).target(iv)
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person).build());
        }

        /** ربط كارت البلاغ العادي بـ item_admin_report.xml */
        private void bindReportItem(@NonNull VH h, HashMap<String, Object> r,
                                     android.content.Context ctx) {
            h.tvName.setText(s(r, "personName", s(r, "name", "مجهول")));
            h.tvAddr.setText(s(r, "lastSeenAddress", s(r, "address", "")));
            h.tvEmail.setText(s(r, "reporterPhone", s(r, "reporterEmail", "")));

            Object ts = r.get("timestamp");
            h.tvTime.setText(ts instanceof Long ? sdf.format(new Date((Long) ts)) : "");

            String photoUrl = s(r, "photoUrl", s(r, "imageUrl", ""));
            if (!photoUrl.isEmpty()) loadImg(ctx, photoUrl, h.ivPhoto);
            else if (h.ivPhoto != null) h.ivPhoto.setImageResource(R.drawable.ic_person);

            RoleManager role = RoleManager.get();
            if (h.btnApprove != null) {
                h.btnApprove.setVisibility(role.canApproveReports() ? View.VISIBLE : View.GONE);
                h.btnApprove.setText("موافقة");
                h.btnApprove.setOnClickListener(v -> listener.onAction(r, "approve"));
            }
            if (h.btnReject != null) {
                h.btnReject.setVisibility(role.canApproveReports() ? View.VISIBLE : View.GONE);
                h.btnReject.setText("رفض");
                h.btnReject.setOnClickListener(v -> listener.onAction(r, "reject"));
            }
            if (h.btnEdit != null) {
                h.btnEdit.setVisibility(role.canEditReports() ? View.VISIBLE : View.GONE);
                h.btnEdit.setOnClickListener(v -> listener.onAction(r, "edit"));
            }
            if (h.btnDelete != null) {
                h.btnDelete.setVisibility(role.canDeleteReports() ? View.VISIBLE : View.GONE);
                h.btnDelete.setOnClickListener(v -> listener.onAction(r, "delete"));
            }
            if (h.btnBan != null) {
                h.btnBan.setVisibility(role.canBanUsers() ? View.VISIBLE : View.GONE);
                h.btnBan.setOnClickListener(v -> listener.onAction(r, "ban"));
            }
            h.itemView.setOnClickListener(v -> listener.onAction(r, "view"));
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            // ── بلاغ عادي ──
            android.widget.ImageView ivPhoto;
            android.widget.TextView  tvName, tvAddr, tvEmail, tvTime;
            com.google.android.material.button.MaterialButton
                    btnApprove, btnReject, btnEdit, btnDelete, btnBan;

            // ── كارت التطابق ──
            android.widget.ImageView ivMatchReport, ivMatchFound;
            android.widget.TextView  tvMatchReportName, tvMatchFoundName;
            android.widget.TextView  tvMatchReportId,   tvMatchFoundId;
            android.widget.TextView  tvMatchStatus,     tvMatchTime;
            android.widget.TextView  chipMatchScore;
            com.google.android.material.progressindicator.LinearProgressIndicator progressMatch;
            // [جديد] تفاصيل إضافية + navigation
            android.widget.LinearLayout rowMissing, rowFound;
            android.widget.TextView  tvMatchReportAge,  tvMatchFoundAge;
            android.widget.TextView  tvMatchReportGender, tvMatchFoundGender;
            android.widget.TextView  tvMatchReportAddr, tvMatchFoundAddr;
            android.widget.TextView  tvMatchReportPhone, tvMatchFoundPhone;
            android.widget.LinearLayout llReportExtraPhotos, llFoundExtraPhotos;
            com.google.android.material.button.MaterialButton btnShareMatch;

            VH(View v, boolean isMatch) {
                super(v);
                if (isMatch) {
                    // item_match_comparison.xml
                    chipMatchScore     = v.findViewById(R.id.chip_match_score);
                    progressMatch      = v.findViewById(R.id.progress_match);
                    ivMatchReport      = v.findViewById(R.id.iv_match_report);
                    ivMatchFound       = v.findViewById(R.id.iv_match_found);
                    tvMatchReportName  = v.findViewById(R.id.tv_match_report_name);
                    tvMatchFoundName   = v.findViewById(R.id.tv_match_found_name);
                    tvMatchReportId    = v.findViewById(R.id.tv_match_report_id);
                    tvMatchFoundId     = v.findViewById(R.id.tv_match_found_id);
                    tvMatchStatus      = v.findViewById(R.id.tv_match_status);
                    tvMatchTime        = v.findViewById(R.id.tv_match_time);
                    btnApprove         = v.findViewById(R.id.btn_approve);
                    btnReject          = v.findViewById(R.id.btn_reject);
                    // [جديد] تفاصيل إضافية
                    rowMissing         = v.findViewById(R.id.row_missing);
                    rowFound           = v.findViewById(R.id.row_found);
                    tvMatchReportAge   = v.findViewById(R.id.tv_match_report_age);
                    tvMatchFoundAge    = v.findViewById(R.id.tv_match_found_age);
                    tvMatchReportGender = v.findViewById(R.id.tv_match_report_gender);
                    tvMatchFoundGender  = v.findViewById(R.id.tv_match_found_gender);
                    tvMatchReportAddr  = v.findViewById(R.id.tv_match_report_addr);
                    tvMatchFoundAddr   = v.findViewById(R.id.tv_match_found_addr);
                    tvMatchReportPhone = v.findViewById(R.id.tv_match_report_phone);
                    tvMatchFoundPhone  = v.findViewById(R.id.tv_match_found_phone);
                    llReportExtraPhotos = v.findViewById(R.id.ll_report_extra_photos);
                    llFoundExtraPhotos  = v.findViewById(R.id.ll_found_extra_photos);
                    btnShareMatch      = v.findViewById(R.id.btn_share_match);
                } else {
                    // item_admin_report.xml
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
        }

        private static String s(HashMap<String, Object> m, String k, String def) {
            Object v = m.get(k);
            return (v instanceof String && !((String) v).isEmpty())
                    ? (String) v : def;
        }
    }
    // ════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════

    /**
     * تصدير كارد التطابق كصورة
     * [إصلاح] نستخدم postDelayed لضمان اكتمال رسم Coil قبل أخذ الـ screenshot
     */
    private static void shareMatchCard(android.view.View cardView, android.content.Context ctx) {
        // انتظر frame واحد حتى تكتمل صور Coil
        cardView.postDelayed(() -> {
            try {
                // إجبار رسم الـ view
                cardView.setDrawingCacheEnabled(true);
                cardView.buildDrawingCache(true);
                android.graphics.Bitmap cacheBmp = cardView.getDrawingCache();
                android.graphics.Bitmap bmp = cacheBmp != null
                    ? android.graphics.Bitmap.createBitmap(cacheBmp)
                    : android.graphics.Bitmap.createBitmap(
                        cardView.getWidth() > 0 ? cardView.getWidth() : 800,
                        cardView.getHeight() > 0 ? cardView.getHeight() : 600,
                        android.graphics.Bitmap.Config.ARGB_8888);
                cardView.setDrawingCacheEnabled(false);

                // رسم watermark
                android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
                android.graphics.Paint bgPaint = new android.graphics.Paint();
                bgPaint.setColor(0xBB1565C0);
                canvas.drawRect(0, bmp.getHeight()-44, bmp.getWidth(), bmp.getHeight(), bgPaint);
                android.graphics.Paint txtPaint = new android.graphics.Paint();
                txtPaint.setColor(0xFFFFFFFF);
                txtPaint.setTextSize(22f);
                txtPaint.setAntiAlias(true);
                txtPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
                canvas.drawText("تم التطابق بواسطة تطبيق سند 🔗",
                    bmp.getWidth() / 2f, bmp.getHeight() - 14, txtPaint);

                // حفظ مؤقت
                java.io.File dir = new java.io.File(ctx.getCacheDir(), "shares");
                dir.mkdirs();
                java.io.File file = new java.io.File(dir, "match_" + System.currentTimeMillis() + ".jpg");
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, fos);
                }
                android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    ctx, ctx.getPackageName() + ".provider", file);
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
                intent.setType("image/jpeg");
                intent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
                intent.putExtra(android.content.Intent.EXTRA_TEXT,
                    "تم تحديد تطابق محتمل بواسطة تطبيق سند لإيجاد المفقودين 🔍");
                intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                ctx.startActivity(android.content.Intent.createChooser(intent, "مشاركة كارد التطابق"));
            } catch (Exception e) {
                android.util.Log.e("AdminActivity", "shareMatchCard error: " + e.getMessage());
            }
        }, 300); // 300ms تضمن اكتمال تحميل الصور
    }

    private static int dpToPx(int dp, android.content.Context ctx) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }


    // ════════════════════════════════════════════════════
    //  Facebook Import Tab
    // ════════════════════════════════════════════════════

    /**
     * تبويب استيراد البلاغات من Facebook
     * يدعم: Gemini API + Grok API + تحليل محلي
     */
    private void loadFbImportTab() {
        if (recyclerView == null) return;
        recyclerView.setAdapter(null);

        // بناء واجهة الاستيراد ديناميكياً
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);
        sv.addView(root);

        // ── بطاقة API Keys ────────────────────────────────────────────
        android.widget.TextView hdrKeys = new android.widget.TextView(this);
        hdrKeys.setText("🔑 مفاتيح الذكاء الاصطناعي (اختياري)");
        hdrKeys.setTextSize(14); hdrKeys.setTypeface(null, android.graphics.Typeface.BOLD);
        hdrKeys.setPadding(0, 0, 0, 10);
        root.addView(hdrKeys);

        android.widget.EditText etGemini = makeInput("Gemini API Key (AIzaSy...)");
        android.widget.EditText etGrok   = makeInput("Grok API Key (xai-...)");
        etGemini.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etGrok.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(etGemini);
        root.addView(etGrok);

        // ── حقل الرابط ───────────────────────────────────────────────
        android.widget.TextView hdrUrl = new android.widget.TextView(this);
        hdrUrl.setText("📘 رابط منشور Facebook");
        hdrUrl.setTextSize(14); hdrUrl.setTypeface(null, android.graphics.Typeface.BOLD);
        hdrUrl.setPadding(0, 16, 0, 8);
        root.addView(hdrUrl);

        android.widget.EditText etUrl = makeInput("https://www.facebook.com/...");
        etUrl.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
            android.text.InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(etUrl);

        // نوع البلاغ
        android.widget.TextView hdrType = new android.widget.TextView(this);
        hdrType.setText("نوع البلاغ");
        hdrType.setTextSize(13); hdrType.setPadding(0, 12, 0, 6);
        root.addView(hdrType);

        android.widget.Spinner spType = new android.widget.Spinner(this);
        spType.setAdapter(new android.widget.ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[]{"مفقود","معثور عليه","مشاهدة","مشرد"}));
        root.addView(spType);

        // المحافظة
        android.widget.EditText etGov = makeInput("المحافظة (اختياري)");
        root.addView(etGov);

        // زر الجلب والتحليل
        android.widget.LinearLayout btns = new android.widget.LinearLayout(this);
        btns.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        btns.setPadding(0, 16, 0, 0);

        com.google.android.material.button.MaterialButton btnFetch = new com.google.android.material.button.MaterialButton(this);
        btnFetch.setText("📥 جلب البيانات");
        btnFetch.setTextSize(13);
        android.widget.LinearLayout.LayoutParams blp = new android.widget.LinearLayout.LayoutParams(0,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        blp.setMarginEnd(8);
        btnFetch.setLayoutParams(blp);
        btns.addView(btnFetch);

        com.google.android.material.button.MaterialButton btnAI = new com.google.android.material.button.MaterialButton(this);
        btnAI.setText("🤖 تحليل AI");
        btnAI.setTextSize(13);
        android.widget.LinearLayout.LayoutParams alp = new android.widget.LinearLayout.LayoutParams(0,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btnAI.setLayoutParams(alp);
        btns.addView(btnAI);
        root.addView(btns);

        // حقل النتائج
        android.widget.TextView tvStatus = new android.widget.TextView(this);
        tvStatus.setTextSize(12); tvStatus.setPadding(0, 12, 0, 0);
        root.addView(tvStatus);

        // بطاقة المعاينة
        android.widget.LinearLayout previewCard = new android.widget.LinearLayout(this);
        previewCard.setOrientation(android.widget.LinearLayout.VERTICAL);
        previewCard.setBackgroundColor(0xFFF5F7FF);
        previewCard.setPadding(14, 14, 14, 14);
        previewCard.setVisibility(android.view.View.GONE);
        android.widget.LinearLayout.LayoutParams pclp = new android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        pclp.topMargin = 12;
        previewCard.setLayoutParams(pclp);
        root.addView(previewCard);

        // حقول قابلة للتعديل في المعاينة
        android.widget.EditText etName = makeInput("اسم الشخص");
        android.widget.EditText etAge  = makeInput("العمر");
        android.widget.EditText etDesc = makeInput("الوصف");
        android.widget.EditText etPhone= makeInput("رقم الهاتف");
        android.widget.EditText etGovP = makeInput("المحافظة");
        android.widget.ImageView ivPreview = new android.widget.ImageView(this);
        ivPreview.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 180));
        ivPreview.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        ivPreview.setVisibility(android.view.View.GONE);
        previewCard.addView(ivPreview);
        previewCard.addView(etName); previewCard.addView(etAge);
        previewCard.addView(etGovP); previewCard.addView(etPhone);
        previewCard.addView(etDesc);

        com.google.android.material.button.MaterialButton btnImport = new com.google.android.material.button.MaterialButton(this);
        btnImport.setText("✅ استيراد كبلاغ");
        btnImport.setTextSize(14);
        previewCard.addView(btnImport);

        // ── منطق الجلب ───────────────────────────────────────────────
        final String[] fetchedImageUrl = {""};

        btnFetch.setOnClickListener(v -> {
            String url = etUrl.getText().toString().trim();
            if (url.isEmpty() || !url.contains("facebook")) {
                tvStatus.setText("❌ أدخل رابط Facebook صحيح"); return;
            }
            tvStatus.setText("⏳ جارٍ جلب البيانات...");
            previewCard.setVisibility(android.view.View.GONE);

            // جلب Open Graph عبر allorigins proxy
            new Thread(() -> {
                try {
                    String proxyUrl = "https://api.allorigins.win/get?url="
                        + java.net.URLEncoder.encode(url, "UTF-8");
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                        new java.net.URL(proxyUrl).openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    String response = sb.toString();

                    // استخراج OG tags بـ regex بسيط
                    String ogTitle = extractMeta(response, "og:title");
                    String ogDesc  = extractMeta(response, "og:description");
                    String ogImg   = extractMeta(response, "og:image");
                    if (ogImg.isEmpty()) ogImg = extractMeta(response, "twitter:image");

                    final String fTitle = ogTitle, fDesc = ogDesc, fImg = ogImg;
                    fetchedImageUrl[0] = ogImg;

                    runOnUiThread(() -> {
                        etName.setText(extractName(fTitle + " " + fDesc));
                        etGovP.setText(etGov.getText().toString().trim());
                        etDesc.setText((fDesc.isEmpty() ? fTitle : fDesc).substring(0, Math.min(300, (fDesc.isEmpty() ? fTitle : fDesc).length())));
                        if (!fImg.isEmpty()) {
                            ivPreview.setVisibility(android.view.View.VISIBLE);
                            CoilImageLoader.load(AdminActivity.this, fImg, ivPreview);
                        }
                        previewCard.setVisibility(android.view.View.VISIBLE);
                        tvStatus.setText("✅ تم جلب البيانات — راجع وأكد");
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        tvStatus.setText("❌ فشل الجلب: " + e.getMessage() + "\nاستخدم الإدخال اليدوي أدناه");
                        etGovP.setText(etGov.getText().toString().trim());
                        previewCard.setVisibility(android.view.View.VISIBLE);
                    });
                }
            }).start();
        });

        // ── تحليل AI ─────────────────────────────────────────────────
        btnAI.setOnClickListener(v -> {
            String geminiKey = etGemini.getText().toString().trim();
            String grokKey   = etGrok.getText().toString().trim();
            String text = etName.getText().toString() + " " + etDesc.getText().toString();
            if (text.trim().isEmpty()) { tvStatus.setText("❌ اجلب المنشور أولاً"); return; }
            if (geminiKey.isEmpty() && grokKey.isEmpty()) {
                // تحليل محلي
                tvStatus.setText("🔍 تحليل محلي...");
                String phone = extractPhone(text);
                if (!phone.isEmpty()) etPhone.setText(phone);
                String gov   = extractGov(text);
                if (!gov.isEmpty()) etGovP.setText(gov);
                tvStatus.setText("✅ تحليل محلي: هاتف=" + phone + " محافظة=" + gov);
                return;
            }
            tvStatus.setText("⏳ تحليل AI...");
            String apiUrl, apiKey;
            boolean isGemini = !geminiKey.isEmpty();
            if (isGemini) {
                apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + geminiKey;
                apiKey = geminiKey;
            } else {
                apiUrl = "https://api.x.ai/v1/chat/completions";
                apiKey = grokKey;
            }
            final String fText = text, fApiUrl = apiUrl, fApiKey = apiKey, fIsGemini = isGemini ? "y" : "n";
            new Thread(() -> {
                try {
                    String prompt = "حلل هذا النص واستخرج JSON فقط: {personName,governorate,phone,age,gender,description,reportType(missing/found/sighting/homeless)}\n\nالنص:\n" + fText.substring(0, Math.min(1000, fText.length()));
                    String body;
                    if ("y".equals(fIsGemini)) {
                        try {
                            org.json.JSONObject geminiBody = new org.json.JSONObject()
                                .put("contents", new org.json.JSONArray().put(
                                    new org.json.JSONObject().put("parts", new org.json.JSONArray().put(
                                        new org.json.JSONObject().put("text", prompt)))));
                            body = geminiBody.toString();
                        } catch (org.json.JSONException je) { body = "{}"; }
                    } else {
                        try {
                            org.json.JSONObject grokBody = new org.json.JSONObject()
                                .put("model", "grok-beta")
                                .put("max_tokens", 400)
                                .put("messages", new org.json.JSONArray().put(
                                    new org.json.JSONObject().put("role","user").put("content", prompt)));
                            body = grokBody.toString();
                        } catch (org.json.JSONException je) { body = "{}"; }
                    }
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(fApiUrl).openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type","application/json");
                    if (!"y".equals(fIsGemini)) conn.setRequestProperty("Authorization","Bearer " + fApiKey);
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(15000);
                    conn.getOutputStream().write(body.getBytes("UTF-8"));
                    java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream(),"UTF-8"));
                    StringBuilder sb2 = new StringBuilder(); String ln;
                    while ((ln = br.readLine()) != null) sb2.append(ln);
                    br.close();
                    String resp = sb2.toString();
                    // استخراج الـ JSON من الرد
                    int js = resp.indexOf("{");
                    int je = resp.lastIndexOf("}") + 1;
                    if (js >= 0 && je > js) {
                        org.json.JSONObject j = new org.json.JSONObject(resp.substring(js, je));
                        final String nm = j.optString("personName","");
                        final String ph = j.optString("phone","");
                        final String gv = j.optString("governorate","");
                        final String dc = j.optString("description","");
                        final String ag = j.optString("age","");
                        runOnUiThread(() -> {
                            if (!nm.isEmpty()) etName.setText(nm);
                            if (!ph.isEmpty()) etPhone.setText(ph);
                            if (!gv.isEmpty()) etGovP.setText(gv);
                            if (!dc.isEmpty()) etDesc.setText(dc);
                            if (!ag.isEmpty()) etAge.setText(ag);
                            tvStatus.setText("✅ AI استخرج: " + nm + " | " + gv + " | " + ph);
                        });
                    } else {
                        runOnUiThread(() -> tvStatus.setText("⚠️ لم يتمكن AI من استخراج بيانات منظمة"));
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> tvStatus.setText("❌ AI فشل: " + e.getMessage()));
                }
            }).start();
        });

        // ── استيراد كبلاغ ────────────────────────────────────────────
        btnImport.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) { tvStatus.setText("❌ أدخل اسم الشخص"); return; }
            String typeStr = new String[]{"missing","found","sighting","homeless"}[spType.getSelectedItemPosition()];
            String reportId = "FB-" + System.currentTimeMillis();
            java.util.HashMap<String, Object> data = new java.util.HashMap<>();
            data.put("reportId",     reportId);
            data.put("personName",   name);
            data.put("personAge",    etAge.getText().toString().trim());
            data.put("governorate",  etGovP.getText().toString().trim());
            data.put("phone",        etPhone.getText().toString().trim());
            data.put("description",  etDesc.getText().toString().trim());
            data.put("reportType",   typeStr);
            data.put("imageUrl",     fetchedImageUrl[0]);
            data.put("imageUrls",    fetchedImageUrl[0].isEmpty()
                ? new java.util.ArrayList<>()
                : java.util.Collections.singletonList(fetchedImageUrl[0]));
            data.put("status",            "pending_review");
            data.put("moderation_status", "pending_review");
            data.put("source",            "facebook_import");
            data.put("sourceUrl",         etUrl.getText().toString().trim());
            data.put("reporterId",        com.google.firebase.auth.FirebaseAuth.getInstance()
                .getCurrentUser() != null ? com.google.firebase.auth.FirebaseAuth.getInstance()
                .getCurrentUser().getUid() : "admin");
            data.put("timestamp",    System.currentTimeMillis());
            data.put("approved",     false);

            com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("reports").child(reportId)
                .setValue(data)
                .addOnSuccessListener(u -> {
                    tvStatus.setText("✅ تم الاستيراد! رقم البلاغ: " + reportId);
                    previewCard.setVisibility(android.view.View.GONE);
                    etUrl.setText(""); etName.setText(""); etDesc.setText("");
                    etPhone.setText(""); etAge.setText("");
                    fetchedImageUrl[0] = "";
                })
                .addOnFailureListener(e ->
                    tvStatus.setText("❌ فشل الاستيراد: " + e.getMessage()));
        });

        // عرض الواجهة
        // عرض الواجهة - استبدال RecyclerView بـ ScrollView
        // نستبدل الـ RecyclerView بـ ScrollView مؤقتاً
        android.view.ViewGroup parent = (android.view.ViewGroup) recyclerView.getParent();
        if (parent != null) {
            int idx = parent.indexOfChild(recyclerView);
            recyclerView.setVisibility(android.view.View.GONE);
            // أضف sv بعد الـ recycler
            if (sv.getParent() == null) parent.addView(sv, idx + 1,
                new android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT));
            else sv.setVisibility(android.view.View.VISIBLE);
        }
    }

    private android.widget.EditText makeInput(String hint) {
        com.google.android.material.textfield.TextInputLayout til =
            new com.google.android.material.textfield.TextInputLayout(this,
                null, com.google.android.material.R.attr.textInputOutlinedStyle);
        til.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        til.setHint(hint);
        com.google.android.material.textfield.TextInputEditText et =
            new com.google.android.material.textfield.TextInputEditText(til.getContext());
        et.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        til.addView(et);
        // return the EditText but parent needs the TIL
        // simplified: just return a plain EditText
        android.widget.EditText plain = new android.widget.EditText(this);
        plain.setHint(hint);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 12;
        plain.setLayoutParams(lp);
        plain.setBackground(androidx.core.content.ContextCompat.getDrawable(
            this, R.drawable.bg_spinner_outlined));
        plain.setPadding(16, 12, 16, 12);
        return plain;
    }

    private static String extractMeta(String html, String prop) {
        String[] patterns = {
            "property=\"" + prop + "\" content=\"([^\"]*)\"",
            "name=\"" + prop + "\" content=\"([^\"]*)\""
        };
        for (String p : patterns) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(p).matcher(html);
            if (m.find()) return m.group(1);
        }
        return "";
    }

    private static String extractName(String text) {
        java.util.regex.Pattern[] pats = {
            java.util.regex.Pattern.compile("(?:مفقود|مفقودة|اسمه|اسمها|يدعى|تدعى)[:\\s]+([^\n،.]{3,30})", java.util.regex.Pattern.CASE_INSENSITIVE),
            java.util.regex.Pattern.compile("(?:باحثين عن|نبحث عن)[:\\s]+([^\n،.]{3,30})", java.util.regex.Pattern.CASE_INSENSITIVE)
        };
        for (java.util.regex.Pattern p : pats) {
            java.util.regex.Matcher m = p.matcher(text);
            if (m.find()) return m.group(1).trim();
        }
        String[] parts = text.split("[،\n]");
        return parts.length > 0 ? parts[0].trim().substring(0, Math.min(30, parts[0].trim().length())) : "غير محدد";
    }

    private static String extractPhone(String text) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("01[0-9]{9}").matcher(text);
        return m.find() ? m.group() : "";
    }

    private static String extractGov(String text) {
        String[] govs = {"القاهرة","الجيزة","الإسكندرية","الشرقية","الدقهلية","الغربية","المنوفية",
            "القليوبية","البحيرة","كفر الشيخ","دمياط","بورسعيد","الإسماعيلية","السويس",
            "أسيوط","سوهاج","قنا","الأقصر","أسوان","المنيا","بني سويف","الفيوم"};
        for (String g : govs) if (text.contains(g)) return g;
        return "";
    }


    private void hideFbScrollView() {
        if (recyclerView == null) return;
        android.view.ViewGroup parent = (android.view.ViewGroup) recyclerView.getParent();
        if (parent != null) {
            for (int i = 0; i < parent.getChildCount(); i++) {
                android.view.View c = parent.getChildAt(i);
                if (c instanceof android.widget.ScrollView) c.setVisibility(android.view.View.GONE);
            }
        }
    }

}
