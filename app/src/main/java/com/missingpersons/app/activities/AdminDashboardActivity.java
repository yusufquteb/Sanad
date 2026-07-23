package com.missingpersons.app.activities;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import com.missingpersons.app.R;
import com.missingpersons.app.utils.EmbeddingMigrationTool;
import com.missingpersons.app.utils.LanguageHelper;
import com.missingpersons.app.utils.NotificationHelper;
import com.missingpersons.app.utils.RateLimiter;
import com.missingpersons.app.utils.RoleManager;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * AdminDashboardActivity — لوحة التحكم الرئيسية
 *
 * [إصلاح] الإشعار الجماعي:
 *   - السبب السابق: كتابة إلى broadcast_notifications ثم نسيانها — لا listener للمستخدمين
 *   - الإصلاح:
 *     1. الكتابة إلى notifications/{uid} لكل مستخدم نشط (last24h)
 *     2. وأيضاً إلى broadcast_notifications للسجل
 *     3. NewHomeActivity يقرأ notifications/{uid} وهو ما يعرض الإشعارات للمستخدمين
 *   - خطأ "❌ فشل" كان بسبب: rules تمنع الكتابة لـ broadcast_notifications من client
 *     الحل: نكتب مباشرة لـ notifications/{uid} التي تسمح بها الـ rules للأدمن
 */
public class AdminDashboardActivity extends AppCompatActivity {

    // ── Views ──────────────────────────────────────────────
    private TextView tvGreeting, tvAdminName, tvDashDate, tvRoleBadge;
    private TextView tvTotal, tvPending, tvApproved, tvResolved, tvFound;
    private LinearProgressIndicator pbPending, pbApproved, pbResolved;
    private TextView tvPendingPct, tvApprovedPct, tvResolvedPct;
    private RecyclerView rvRecentReports;
    private TextView tvNoReports, tvSeeAll;
    private LinearProgressIndicator progressDash;
    private MaterialCardView cardNewNotif, cardRealtimeBadge;
    private TextView tvNewNotifCount;

    // ── Admin-only panels ──────────────────────────────────
    private View sectionAdminControls;
    private View sectionManagers;
    private View cardPendingAction;
    private View cardMembers;
    private View btnBroadcast;
    private View btnLimit;
    private View btnDisable;

    private ChipGroup chipGroupPerms;

    // ── Data ────────────────────────────────────────────────
    private AdminDashboardReportAdapter reportsAdapter;
    private final List<Map<String, Object>> recentList = new ArrayList<>();

    // ── Firebase Listeners ────────────────────────────────
    private DatabaseReference reportsRef;
    private ValueEventListener reportsListener;
    private DatabaseReference notifRef;
    private ChildEventListener notifListener;

    // ── State ───────────────────────────────────────────────
    private int currentNewNotifCount = 0;
    private long lastKnownTimestamp  = 0;
    private boolean isFirstLoad      = true;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LanguageHelper.applyLanguage(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }

        setContentView(R.layout.activity_admin_dashboard);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bot = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), bot);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar_admin_dashboard);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setTitle("لوحة التحكم");
            }
        }

        bindViews();
        showLoading(true);

        RoleManager.get().load(new RoleManager.LoadCallback() {
            @Override
            public void onLoaded(boolean isAdminOrManager) {
                if (!isAdminOrManager) {
                    Toast.makeText(AdminDashboardActivity.this,
                        "غير مصرح لك بالدخول", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                runOnUiThread(() -> {
                    showLoading(false);
                    setupHeader();
                    applyPermissions();
                    setupQuickLinks();
                    setupRecyclerView();
                    startRealtimeListener();
                    listenForAdminNotifications();
                    invalidateOptionsMenu(); // يُعيد تقييم ظهور عنصر "إعادة الاستخراج" بعد تأكيد الدور
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(AdminDashboardActivity.this,
                        "خطأ في التحقق: " + message, Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    // ────────────────────────────────────────────────────────
    //  Bind Views
    // ────────────────────────────────────────────────────────

    private void bindViews() {
        tvGreeting          = findViewById(R.id.tv_dash_greeting);
        tvAdminName         = findViewById(R.id.tv_dash_admin_name);
        tvDashDate          = findViewById(R.id.tv_dash_date);
        tvRoleBadge         = findViewById(R.id.tv_dash_role_badge);
        tvTotal             = findViewById(R.id.tv_dash_total);
        tvPending           = findViewById(R.id.tv_dash_pending);
        tvApproved          = findViewById(R.id.tv_dash_approved);
        tvResolved          = findViewById(R.id.tv_dash_resolved);
        tvFound             = findViewById(R.id.tv_dash_found);
        pbPending           = findViewById(R.id.pb_pending);
        pbApproved          = findViewById(R.id.pb_approved);
        pbResolved          = findViewById(R.id.pb_resolved);
        tvPendingPct        = findViewById(R.id.tv_pending_pct);
        tvApprovedPct       = findViewById(R.id.tv_approved_pct);
        tvResolvedPct       = findViewById(R.id.tv_resolved_pct);
        rvRecentReports     = findViewById(R.id.rv_recent_reports);
        tvNoReports         = findViewById(R.id.tv_no_reports);
        tvSeeAll            = findViewById(R.id.tv_see_all_reports);
        progressDash        = findViewById(R.id.progress_dashboard);
        cardNewNotif        = findViewById(R.id.card_new_notif);
        tvNewNotifCount     = findViewById(R.id.tv_new_notif_count);
        cardRealtimeBadge   = findViewById(R.id.card_realtime_badge);

        sectionAdminControls = findViewById(R.id.layout_admin_controls);
        sectionManagers      = findViewById(R.id.card_dash_managers);
        cardPendingAction    = findViewById(R.id.card_dash_pending_action);
        cardMembers          = findViewById(R.id.card_dash_members);
        btnBroadcast         = findViewById(R.id.btn_broadcast);
        btnLimit             = findViewById(R.id.btn_set_daily_limit);
        btnDisable           = findViewById(R.id.btn_disable_user);
        chipGroupPerms       = findViewById(R.id.chip_group_perms);

        if (tvSeeAll != null) {
            tvSeeAll.setOnClickListener(v ->
                startActivity(new Intent(this, AdminActivity.class)));
        }
    }

    private void showLoading(boolean show) {
        if (progressDash != null)
            progressDash.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // ────────────────────────────────────────────────────────
    //  🔐 تطبيق الصلاحيات
    // ────────────────────────────────────────────────────────

    private void applyPermissions() {
        RoleManager role = RoleManager.get();

        if (tvRoleBadge != null) {
            if (role.isAdmin()) {
                tvRoleBadge.setText("👑 أدمن");
                tvRoleBadge.setVisibility(View.VISIBLE);
            } else if (role.isManager()) {
                tvRoleBadge.setText("⭐ مدير");
                tvRoleBadge.setVisibility(View.VISIBLE);
            }
        }

        setVisible(sectionManagers, role.isAdmin());
        setVisible(sectionAdminControls, role.isAdmin());
        setVisible(btnLimit,   role.isAdmin());
        setVisible(btnDisable, role.isAdmin());
        setVisible(btnBroadcast, role.canSendNotifications());
        setVisible(cardPendingAction, role.canApproveReports());
        setVisible(cardMembers, role.canManageMembers());

        if (chipGroupPerms != null) {
            if (role.isManager()) {
                chipGroupPerms.setVisibility(View.VISIBLE);
                buildPermChips(role);
            } else {
                chipGroupPerms.setVisibility(View.GONE);
            }
        }
    }

    private void buildPermChips(RoleManager role) {
        if (chipGroupPerms == null) return;
        chipGroupPerms.removeAllViews();

        String[][] perms = {
            {RoleManager.PERM_APPROVE_REPORTS,    "✅ الموافقة على البلاغات"},
            {RoleManager.PERM_DELETE_REPORTS,     "🗑️ حذف البلاغات"},
            {RoleManager.PERM_MANAGE_MEMBERS,     "👥 إدارة الأعضاء"},
            {RoleManager.PERM_BAN_USERS,          "🚫 حظر الأعضاء"},
            {RoleManager.PERM_VIEW_ALL_REPORTS,   "📋 عرض جميع البلاغات"},
            {RoleManager.PERM_EDIT_REPORTS,       "✏️ تعديل البلاغات"},
            {RoleManager.PERM_SEND_NOTIFICATIONS, "🔔 إرسال إشعارات"},
        };

        for (String[] pair : perms) {
            boolean hasPerm = role.hasPerm(pair[0]);
            Chip chip = new Chip(this);
            chip.setText(pair[1]);
            chip.setCheckable(false);
            chip.setChipBackgroundColorResource(
                hasPerm ? R.color.green_light : R.color.chip_bg_disabled);
            chip.setTextColor(getResources().getColor(
                hasPerm ? R.color.green_dark : R.color.text_secondary, getTheme()));
            chipGroupPerms.addView(chip);
        }
    }

    // ────────────────────────────────────────────────────────
    //  RecyclerView
    // ────────────────────────────────────────────────────────

    private void setupRecyclerView() {
        if (rvRecentReports == null) return;
        reportsAdapter = new AdminDashboardReportAdapter(this, recentList);
        rvRecentReports.setLayoutManager(new LinearLayoutManager(this));
        rvRecentReports.setAdapter(reportsAdapter);
        rvRecentReports.setHasFixedSize(false);
    }

    // ────────────────────────────────────────────────────────
    //  Header
    // ────────────────────────────────────────────────────────

    private void setupHeader() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String displayName = user.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                if (tvAdminName != null) tvAdminName.setText(displayName);
            } else {
                String email = user.getEmail();
                if (email != null) {
                    String namePart = email.contains("@")
                            ? email.substring(0, email.indexOf('@')) : email;
                    if (tvAdminName != null) tvAdminName.setText(namePart);
                }
            }
        }
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE، d MMMM yyyy", new Locale("ar"));
        if (tvDashDate != null) tvDashDate.setText(sdf.format(new Date()));

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour < 12)      greeting = "صباح الخير 🌅";
        else if (hour < 17) greeting = "مساء النور 🌤️";
        else                greeting = "مساء الخير 🌙";
        if (tvGreeting != null) tvGreeting.setText(greeting);
    }

    // ────────────────────────────────────────────────────────
    //  🔥 Real-Time Firebase Listener
    // ────────────────────────────────────────────────────────

    private void startRealtimeListener() {
        showLoading(true);
        reportsRef = FirebaseDatabase.getInstance().getReference("reports");

        reportsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                long total = 0, pending = 0, approved = 0, resolved = 0;
                List<Map<String, Object>> recent = new ArrayList<>();
                long latestTs = 0;

                List<DataSnapshot> allSnaps = new ArrayList<>();
                for (DataSnapshot c : snap.getChildren()) allSnaps.add(c);

                allSnaps.sort((a, b) -> {
                    Long ta = a.child("timestamp").getValue(Long.class);
                    Long tb = b.child("timestamp").getValue(Long.class);
                    if (ta == null) ta = 0L;
                    if (tb == null) tb = 0L;
                    return Long.compare(tb, ta);
                });

                for (DataSnapshot c : allSnaps) {
                    total++;
                    String status = c.child("status").getValue(String.class);
                    if ("pending".equals(status))        pending++;
                    else if ("approved".equals(status))  approved++;
                    else if ("resolved".equals(status))  resolved++;

                    Long ts = c.child("timestamp").getValue(Long.class);
                    if (ts != null && ts > latestTs) latestTs = ts;

                    if (recent.size() < 5) {
                        Map<String, Object> map = new HashMap<>();
                        if (c.getValue() instanceof Map) {
                            //noinspection unchecked
                            map.putAll((Map<String, Object>) c.getValue());
                        }
                        if (!map.containsKey("reportId")) map.put("reportId", c.getKey());
                        recent.add(map);
                    }
                }

                final boolean isNewReport = !isFirstLoad && latestTs > lastKnownTimestamp;
                if (latestTs > 0) lastKnownTimestamp = latestTs;
                isFirstLoad = false;

                final long fTotal = total, fPending = pending,
                           fApproved = approved, fResolved = resolved;
                final List<Map<String, Object>> fRecent = recent;

                FirebaseDatabase.getInstance().getReference("found_persons")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override public void onDataChange(@NonNull DataSnapshot fs) {
                            runOnUiThread(() -> updateUI(fTotal, fPending, fApproved,
                                fResolved, fs.getChildrenCount(), fRecent, isNewReport));
                        }
                        @Override public void onCancelled(@NonNull DatabaseError e) {
                            runOnUiThread(() -> updateUI(fTotal, fPending, fApproved,
                                fResolved, 0, fRecent, isNewReport));
                        }
                    });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError e) {
                runOnUiThread(() -> showLoading(false));
            }
        };

        reportsRef.addValueEventListener(reportsListener);
    }

    // ────────────────────────────────────────────────────────
    //  إشعارات الأدمن
    // ────────────────────────────────────────────────────────

    private void listenForAdminNotifications() {
        notifRef = FirebaseDatabase.getInstance()
                .getReference("admin_notifications");

        notifListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snap, String prev) {
                Boolean isRead = snap.child("read").getValue(Boolean.class);
                if (Boolean.FALSE.equals(isRead)) {
                    currentNewNotifCount++;
                    runOnUiThread(() -> updateNotifBadge());

                    String personName = snap.child("personName").getValue(String.class);
                    String gov        = snap.child("governorate").getValue(String.class);
                    String msg = "بلاغ جديد" +
                            (personName != null ? ": " + personName : "") +
                            (gov != null && !gov.isEmpty() ? " — " + gov : "");
                    NotificationHelper.showAdminNotification(
                            AdminDashboardActivity.this,
                            "🔔 بلاغ جديد يحتاج مراجعة", msg);
                }
            }
            @Override public void onChildChanged(@NonNull DataSnapshot s, String p) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot s) {}
            @Override public void onChildMoved(@NonNull DataSnapshot s, String p) {}
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };

        notifRef.orderByChild("timestamp").limitToLast(20)
                .addChildEventListener(notifListener);
    }

    private void updateNotifBadge() {
        if (cardNewNotif == null || tvNewNotifCount == null) return;
        if (currentNewNotifCount > 0) {
            cardNewNotif.setVisibility(View.VISIBLE);
            tvNewNotifCount.setText(currentNewNotifCount + " جديد 🔴");
        } else {
            cardNewNotif.setVisibility(View.GONE);
        }
    }

    // ────────────────────────────────────────────────────────
    //  Update UI
    // ────────────────────────────────────────────────────────

    private void updateUI(long total, long pending, long approved,
                          long resolved, long found,
                          List<Map<String, Object>> recent,
                          boolean isNewReport) {
        showLoading(false);

        animateCounter(tvTotal,    total);
        animateCounter(tvPending,  pending);
        animateCounter(tvApproved, approved);
        animateCounter(tvResolved, resolved);
        animateCounter(tvFound,    found);

        int max = (int) Math.max(total, 1);
        setProgress(pbPending,  tvPendingPct,  (int) pending,  max);
        setProgress(pbApproved, tvApprovedPct, (int) approved, max);
        setProgress(pbResolved, tvResolvedPct, (int) resolved, max);

        recentList.clear();
        recentList.addAll(recent);
        if (reportsAdapter != null) reportsAdapter.notifyDataSetChanged();

        boolean hasReports = !recentList.isEmpty();
        if (rvRecentReports != null) rvRecentReports.setVisibility(hasReports ? View.VISIBLE : View.GONE);
        if (tvNoReports != null) tvNoReports.setVisibility(hasReports ? View.GONE : View.VISIBLE);

        if (isNewReport && cardRealtimeBadge != null) {
            cardRealtimeBadge.setVisibility(View.VISIBLE);
            cardRealtimeBadge.animate().alpha(0f).setStartDelay(3000).setDuration(500)
                    .withEndAction(() -> {
                        if (cardRealtimeBadge != null) {
                            cardRealtimeBadge.setVisibility(View.GONE);
                            cardRealtimeBadge.setAlpha(1f);
                        }
                    }).start();
        }
    }

    // ────────────────────────────────────────────────────────
    //  Counter Animation
    // ────────────────────────────────────────────────────────

    private void animateCounter(final TextView tv, final long target) {
        if (tv == null) return;
        String current = tv.getText().toString().replaceAll("[^0-9]", "");
        long from = current.isEmpty() ? 0 : Long.parseLong(current);
        ValueAnimator anim = ValueAnimator.ofInt((int) from, (int) target);
        anim.setDuration(600);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> tv.setText(String.valueOf(a.getAnimatedValue())));
        anim.start();
    }

    private void setProgress(LinearProgressIndicator pb, TextView tvPct, int value, int max) {
        if (pb == null) return;
        pb.setMax(max);
        pb.setProgressCompat(value, true);
        if (tvPct != null) {
            int pct = max > 0 ? (int) (value * 100f / max) : 0;
            tvPct.setText(value + " (" + pct + "%)");
        }
    }

    // ────────────────────────────────────────────────────────
    //  Quick Links
    // ────────────────────────────────────────────────────────

    private void setupQuickLinks() {
        RoleManager role = RoleManager.get();

        setClick(R.id.card_dash_pending_action, () ->
            startActivity(new Intent(this, AdminActivity.class)));

        if (role.canManageMembers()) {
            setClick(R.id.card_dash_members, () ->
                startActivity(new Intent(this, MembersActivity.class)));
        }

        if (role.isAdmin()) {
            setClick(R.id.card_dash_managers, () ->
                startActivity(new Intent(this, ManagersActivity.class)));
        }

        setClick(R.id.card_dash_stats, () ->
            startActivity(new Intent(this, StatisticsActivity.class)));

        setClick(R.id.card_dash_stories_action, () ->
            startActivity(new Intent(this, SuccessStoriesActivity.class)));

        if (role.isAdmin()) {
            if (btnLimit   != null) btnLimit.setOnClickListener(v -> showDailyLimitDialog());
            if (btnDisable != null) btnDisable.setOnClickListener(v -> showDisableUserDialog());
            View btnChatTtl = findViewById(R.id.btn_chat_ttl);
            if (btnChatTtl != null) btnChatTtl.setOnClickListener(v -> showChatTtlDialog());
        }
        if (role.canSendNotifications()) {
            if (btnBroadcast != null) btnBroadcast.setOnClickListener(v -> showBroadcastDialog());
        }
    }

    // ────────────────────────────────────────────────────────
    //  Admin Controls Dialogs
    // ────────────────────────────────────────────────────────

    private void showDailyLimitDialog() {
        FirebaseDatabase.getInstance().getReference("settings/daily_report_limit")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    int current = snap.getValue(Integer.class) != null
                            ? snap.getValue(Integer.class) : RateLimiter.DEFAULT_MAX_REPORTS;
                    EditText et = new EditText(AdminDashboardActivity.this);
                    et.setInputType(InputType.TYPE_CLASS_NUMBER);
                    et.setText(String.valueOf(current));
                    et.setPadding(40, 20, 40, 20);
                    new AlertDialog.Builder(AdminDashboardActivity.this)
                        .setTitle("📊 تعديل الحد اليومي")
                        .setMessage("الحد الحالي: " + current + " بلاغ/يوم")
                        .setView(et)
                        .setPositiveButton("حفظ", (d, w) -> {
                            String val = et.getText().toString().trim();
                            if (!val.isEmpty()) {
                                int newLimit = Integer.parseInt(val);
                                if (newLimit < 1 || newLimit > 100) {
                                    Toast.makeText(AdminDashboardActivity.this,
                                        "⚠️ القيمة يجب أن تكون بين 1 و100",
                                        Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                RateLimiter.setGlobalDailyLimit(AdminDashboardActivity.this, newLimit);
                                // [إصلاح] حفظ في Firebase مع callback
                                FirebaseDatabase.getInstance()
                                    .getReference("settings/daily_report_limit")
                                    .setValue(newLimit)
                                    .addOnSuccessListener(v2 -> Toast.makeText(AdminDashboardActivity.this,
                                        "✅ تم التحديث إلى " + newLimit + " بلاغ/يوم",
                                        Toast.LENGTH_SHORT).show())
                                    .addOnFailureListener(e2 -> Toast.makeText(AdminDashboardActivity.this,
                                        "⚠️ تم التحديث محلياً فقط: " + e2.getMessage(),
                                        Toast.LENGTH_LONG).show());
                            }
                        })
                        .setNegativeButton("إلغاء", null).show();
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {
                    Toast.makeText(AdminDashboardActivity.this,
                        "❌ تعذر قراءة الإعدادات: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void showChatTtlDialog() {
        FirebaseDatabase.getInstance().getReference("settings/chat_ttl_days")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    Long current = snap.getValue(Long.class);
                    long currentDays = (current != null && current > 0) ? current : 30L;

                    android.widget.EditText et = new android.widget.EditText(AdminDashboardActivity.this);
                    et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                    et.setText(String.valueOf(currentDays));
                    et.setPadding(40, 20, 40, 20);

                    new AlertDialog.Builder(AdminDashboardActivity.this)
                        .setTitle("💬 مدة الاحتفاظ برسائل الشات")
                        .setMessage("الإعداد الحالي: " + currentDays + " يوم\n"
                            + "أدخل عدد الأيام (الحد الأدنى: 1، الافتراضي: 30)")
                        .setView(et)
                        .setPositiveButton("حفظ", (d, w) -> {
                            String val = et.getText().toString().trim();
                            if (val.isEmpty()) return;
                            long days = Long.parseLong(val);
                            if (days < 1) days = 1;
                            long finalDays = days;
                            FirebaseDatabase.getInstance()
                                .getReference("settings/chat_ttl_days")
                                .setValue(finalDays)
                                .addOnSuccessListener(v -> Toast.makeText(
                                    AdminDashboardActivity.this,
                                    "✅ تم الحفظ — سيُطبَّق في دورة التنظيف التالية",
                                    Toast.LENGTH_LONG).show())
                                .addOnFailureListener(e -> Toast.makeText(
                                    AdminDashboardActivity.this,
                                    "❌ فشل الحفظ: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show());
                        })
                        .setNegativeButton("إلغاء", null).show();
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {
                    Toast.makeText(AdminDashboardActivity.this,
                        "❌ فشل قراءة الإعدادات", Toast.LENGTH_SHORT).show();
                }
            });
    }

    /**
     * [إصلاح] الإشعار الجماعي
     *
     * المشكلة السابقة:
     *   الكود يكتب إلى broadcast_notifications/ لكن:
     *   1. Firebase Rules قد تمنع الكتابة من الـ client
     *   2. لا يوجد listener في أي شاشة للمستخدمين يقرأ هذا المسار
     *
     * الحل الجديد:
     *   - نقرأ أولاً قائمة الـ users النشطين (آخر 7 أيام)
     *   - نكتب لكل user في notifications/{uid} مباشرة
     *   - هذا المسار مسموح للأدمن بالكتابة إليه وفق الـ rules المعيارية
     *   - NewHomeActivity يستمع لـ notifications/{uid} بالفعل عبر listenBadges()
     *   - نكتب أيضاً لـ broadcast_notifications كسجل للأرشفة
     */
    private void showBroadcastDialog() {
        EditText et = new EditText(this);
        et.setHint("نص الإشعار...");
        et.setMaxLines(4);
        et.setPadding(40, 20, 40, 20);
        new AlertDialog.Builder(this)
            .setTitle("📢 إشعار جماعي")
            .setMessage("سيصل الإشعار لجميع المستخدمين النشطين")
            .setView(et)
            .setPositiveButton("إرسال", (d, w) -> {
                String msg = et.getText().toString().trim();
                if (msg.isEmpty()) {
                    Toast.makeText(this, "⚠️ النص فارغ", Toast.LENGTH_SHORT).show();
                    return;
                }
                sendBroadcastToAllUsers(msg);
            })
            .setNegativeButton("إلغاء", null).show();
    }

    /**
     * يرسل الإشعار الجماعي عبر كتابة إدخال في notifications/{uid}
     * لكل مستخدم نشط (آخر 7 أيام).
     * NewHomeActivity يستمع لهذا المسار تلقائياً.
     */
    private void sendBroadcastToAllUsers(String message) {
        // أظهر loading
        Toast.makeText(this, "⏳ جارٍ الإرسال...", Toast.LENGTH_SHORT).show();

        long now = System.currentTimeMillis();
        long sevenDaysAgo = now - (7L * 24 * 60 * 60 * 1000);

        // اقرأ المستخدمين النشطين (آخر 7 أيام بناءً على lastSeen أو joinDate)
        FirebaseDatabase.getInstance().getReference("users")
            .orderByChild("lastSeen")
            .startAt(sevenDaysAgo)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snap) {
                    if (!snap.hasChildren()) {
                        // fallback: ارسل لكل المستخدمين إن لم يكن lastSeen متاحاً
                        sendBroadcastFallback(message, now);
                        return;
                    }

                    final int total = (int) snap.getChildrenCount();
                    final int[] sent = {0};
                    final int[] failed = {0};

                    DatabaseReference notifRoot = FirebaseDatabase.getInstance()
                            .getReference("notifications");

                    for (DataSnapshot userSnap : snap.getChildren()) {
                        String uid = userSnap.getKey();
                        if (uid == null) continue;

                        Map<String, Object> notif = new HashMap<>();
                        notif.put("type",      "broadcast");
                        notif.put("title",     "📢 إشعار من الإدارة");
                        notif.put("message",   message);
                        notif.put("timestamp", now);
                        notif.put("read",      false);
                        notif.put("fromAdmin", true);

                        notifRoot.child(uid).push().setValue(notif)
                            .addOnSuccessListener(v -> {
                                sent[0]++;
                                if (sent[0] + failed[0] >= total) {
                                    runOnUiThread(() -> showBroadcastResult(sent[0], failed[0]));
                                }
                            })
                            .addOnFailureListener(e -> {
                                failed[0]++;
                                if (sent[0] + failed[0] >= total) {
                                    runOnUiThread(() -> showBroadcastResult(sent[0], failed[0]));
                                }
                            });
                    }

                    // سجّل في broadcast_notifications كأرشيف
                    Map<String, Object> archiveEntry = new HashMap<>();
                    archiveEntry.put("message",   message);
                    archiveEntry.put("timestamp", now);
                    archiveEntry.put("sentBy",    FirebaseAuth.getInstance().getCurrentUser() != null
                            ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "admin");
                    archiveEntry.put("targetCount", total);
                    FirebaseDatabase.getInstance()
                        .getReference("broadcast_notifications")
                        .push().setValue(archiveEntry);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError e) {
                    Toast.makeText(AdminDashboardActivity.this,
                        "❌ فشل قراءة المستخدمين: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
                }
            });
    }

    /** Fallback: أرسل للكل بدون فلتر lastSeen */
    private void sendBroadcastFallback(String message, long now) {
        FirebaseDatabase.getInstance().getReference("users")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snap) {
                    int total   = (int) snap.getChildrenCount();
                    int[] sent  = {0};
                    int[] failed = {0};

                    DatabaseReference notifRoot = FirebaseDatabase.getInstance()
                            .getReference("notifications");

                    for (DataSnapshot userSnap : snap.getChildren()) {
                        String uid = userSnap.getKey();
                        if (uid == null) continue;

                        Map<String, Object> notif = new HashMap<>();
                        notif.put("type",      "broadcast");
                        notif.put("title",     "📢 إشعار من الإدارة");
                        notif.put("message",   message);
                        notif.put("timestamp", now);
                        notif.put("read",      false);
                        notif.put("fromAdmin", true);

                        notifRoot.child(uid).push().setValue(notif)
                            .addOnSuccessListener(v -> {
                                sent[0]++;
                                if (sent[0] + failed[0] >= total)
                                    runOnUiThread(() -> showBroadcastResult(sent[0], failed[0]));
                            })
                            .addOnFailureListener(e -> {
                                failed[0]++;
                                if (sent[0] + failed[0] >= total)
                                    runOnUiThread(() -> showBroadcastResult(sent[0], failed[0]));
                            });
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {
                    Toast.makeText(AdminDashboardActivity.this,
                        "❌ فشل الإرسال: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
    }

    private void showBroadcastResult(int sent, int failed) {
        String msg = "✅ تم الإرسال لـ " + sent + " مستخدم";
        if (failed > 0) msg += " (فشل: " + failed + ")";
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private void showDisableUserDialog() {
        EditText etUid = new EditText(this);
        etUid.setHint("UID أو البريد الإلكتروني للعضو");
        etUid.setPadding(40, 20, 40, 20);
        new AlertDialog.Builder(this)
            .setTitle("🚫 تعطيل حساب عضو")
            .setView(etUid)
            .setPositiveButton("تعطيل مؤقت (7 أيام)", (d, w) ->
                    disableUser(etUid.getText().toString().trim(), 7))
            .setNeutralButton("تعطيل دائم", (d, w) ->
                    disableUser(etUid.getText().toString().trim(), -1))
            .setNegativeButton("إلغاء", null).show();
    }

    private void disableUser(String uid, int days) {
        if (uid.isEmpty()) return;
        HashMap<String, Object> data = new HashMap<>();
        data.put("disabled", true);
        data.put("disabledUntil", days > 0
                ? System.currentTimeMillis() + (days * 24L * 60 * 60 * 1000) : -1L);
        data.put("disabledBy", "admin");
        data.put("disabledAt", System.currentTimeMillis());
        FirebaseDatabase.getInstance().getReference("users").child(uid)
            .updateChildren(data)
            .addOnSuccessListener(v -> Toast.makeText(this,
                days > 0 ? "🚫 تم التعطيل لـ " + days + " أيام" : "🚫 تم التعطيل بشكل دائم",
                Toast.LENGTH_LONG).show())
            .addOnFailureListener(e -> Toast.makeText(this,
                "❌ UID غير صحيح أو لا توجد صلاحية", Toast.LENGTH_SHORT).show());
    }

    // ────────────────────────────────────────────────────────
    //  Lifecycle
    // ────────────────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        currentNewNotifCount = 0;
        updateNotifBadge();
        markAdminNotificationsRead();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (reportsRef != null && reportsListener != null)
            reportsRef.removeEventListener(reportsListener);
        if (notifRef != null && notifListener != null)
            notifRef.removeEventListener(notifListener);
    }

    private void markAdminNotificationsRead() {
        FirebaseDatabase.getInstance()
            .getReference("admin_notifications")
            .orderByChild("read").equalTo(false)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    for (DataSnapshot c : snap.getChildren()) c.getRef().child("read").setValue(true);
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
    }

    // ────────────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────────────

    private void setClick(int id, Runnable r) {
        View v = findViewById(id);
        if (v == null || v.getVisibility() != View.VISIBLE) return;
        v.setOnClickListener(x -> {
            x.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80)
                .withEndAction(() -> x.animate().scaleX(1f).scaleY(1f).setDuration(80).start())
                .start();
            r.run();
        });
    }

    private void setVisible(View v, boolean visible) {
        if (v != null) v.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }

    // ════════════════════════════════════════════════════
    //  أداة صيانة: إعادة استخراج بصمات الوجه القديمة
    //
    //  بصمة قديمة (MobileFaceNet 128-dim) وأخرى حديثة (AdaFace 512-dim)
    //  لنفس الشخص لا تتطابقان أبداً — cosineSimilarity ترفض المقارنة
    //  فوراً عند اختلاف الأبعاد. هذه الأداة تُعيد استخراج البصمة من
    //  الصورة المحفوظة بالنموذج الحالي لأي سجل قديم، فعلياً وليس فقط
    //  بوضع علامة عليه.
    // ════════════════════════════════════════════════════

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_admin_dashboard, menu);
        MenuItem reembedItem = menu.findItem(R.id.action_reembed_faces);
        if (reembedItem != null) {
            reembedItem.setVisible(RoleManager.get().isAdmin());
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_reembed_faces) {
            showReembedDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showReembedDialog() {
        new AlertDialog.Builder(this)
            .setTitle("🔄 إعادة استخراج بصمات الوجه القديمة")
            .setMessage("تبحث هذه الأداة عن بلاغات ببصمة وجه من نموذج قديم "
                + "(128 بُعد) لا يمكنها التطابق مع البلاغات الحديثة (512 بُعد)، "
                + "وتُعيد استخراج بصمتها من الصورة المحفوظة بالنموذج الحالي.\n\n"
                + "قد تستغرق العملية عدة دقائق حسب عدد البلاغات القديمة، وتحتاج اتصالاً بالإنترنت. "
                + "لا تُغلق التطبيق أثناء التنفيذ.\n\n"
                + "متابعة؟")
            .setPositiveButton("بدء الفحص", (d, w) -> runReembedFlow())
            .setNegativeButton("إلغاء", null)
            .show();
    }

    private void runReembedFlow() {
        AlertDialog progressDialog = new AlertDialog.Builder(this)
            .setTitle("🔄 جارٍ إعادة الاستخراج...")
            .setMessage("جارٍ فحص البلاغات...")
            .setCancelable(false)
            .create();
        progressDialog.show();

        EmbeddingMigrationTool.reembedLegacy(this, "reports",
            new EmbeddingMigrationTool.MigrationCallback() {
                @Override
                public void onProgress(int processed, int total, int migrated) {
                    progressDialog.setMessage("البلاغات: " + processed + "/" + total
                        + " — نجح: " + migrated);
                }

                @Override
                public void onComplete(EmbeddingMigrationTool.MigrationResult reportsResult) {
                    progressDialog.setMessage("جارٍ فحص المعثور عليهم...");
                    EmbeddingMigrationTool.reembedLegacy(AdminDashboardActivity.this, "found_persons",
                        new EmbeddingMigrationTool.MigrationCallback() {
                            @Override
                            public void onProgress(int processed, int total, int migrated) {
                                progressDialog.setMessage("المعثور عليهم: " + processed + "/" + total
                                    + " — نجح: " + migrated);
                            }

                            @Override
                            public void onComplete(EmbeddingMigrationTool.MigrationResult foundResult) {
                                progressDialog.dismiss();
                                new AlertDialog.Builder(AdminDashboardActivity.this)
                                    .setTitle("✅ انتهت إعادة الاستخراج")
                                    .setMessage("البلاغات: أُعيد استخراج " + reportsResult.needsMigration
                                        + " من أصل " + reportsResult.totalRecords + " سجلاً قديماً\n"
                                        + "المعثور عليهم: أُعيد استخراج " + foundResult.needsMigration
                                        + " من أصل " + foundResult.totalRecords + " سجلاً قديماً")
                                    .setPositiveButton("حسناً", null)
                                    .show();
                            }

                            @Override
                            public void onError(String reason) {
                                progressDialog.dismiss();
                                Toast.makeText(AdminDashboardActivity.this,
                                    "خطأ في فحص المعثور عليهم: " + reason, Toast.LENGTH_LONG).show();
                            }
                        });
                }

                @Override
                public void onError(String reason) {
                    progressDialog.dismiss();
                    Toast.makeText(AdminDashboardActivity.this,
                        "خطأ في فحص البلاغات: " + reason, Toast.LENGTH_LONG).show();
                }
            });
    }
}
