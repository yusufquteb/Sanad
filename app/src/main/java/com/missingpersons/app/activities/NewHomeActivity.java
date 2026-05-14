package com.missingpersons.app.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.location.*;
import com.google.firebase.auth.*;
import com.google.firebase.database.*;
import com.missingpersons.app.R;
import com.missingpersons.app.utils.*;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import de.hdodenhof.circleimageview.CircleImageView;
import java.text.SimpleDateFormat;
import java.util.*;
import androidx.lifecycle.ViewModelProvider;
import com.missingpersons.app.ui.common.AppViewModelFactory;
import com.missingpersons.app.ui.home.HomeViewModel;
import com.missingpersons.app.utils.InAppUpdateManager;

public class NewHomeActivity extends AppCompatActivity {

    // Views
    private TextView tvGreeting, tvLocation;
    private TextView badgeNotifications, badgeChats;
    private CircleImageView ivUserAvatar;
    private TextView tvStatActive, tvStatFound, tvStatToday;
    private RecyclerView rvRecentReports;

    // Firebase
    private DatabaseReference db;
    private ValueEventListener notifListener, chatListener;
    // Realtime listeners للبلاغات الأخيرة — تُزال في onDestroy
    private final List<ValueEventListener> recentListeners = new ArrayList<>();
    private final List<com.google.firebase.database.Query> recentRefs = new ArrayList<>();

    // Location
    private FusedLocationProviderClient locationClient;
    private Location myLocation;

    // Adapter
    private final List<Map<String, Object>> recentReports = new ArrayList<>();
    private RecentAdapter recentAdapter;
    private com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton fabAddReport;

    // [مرحلة 1.2] ViewModel — المصدر الوحيد للحقيقة
    private HomeViewModel viewModel;

    // [7.4] InAppUpdateManager
    private InAppUpdateManager updateManager;

    @Override
    protected void attachBaseContext(android.content.Context ctx) {
        super.attachBaseContext(LanguageHelper.applyLanguage(ctx));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_home);

        db = FirebaseDatabase.getInstance().getReference();
        locationClient = LocationServices.getFusedLocationProviderClient(this);

        // Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;

            View header = findViewById(R.id.header_container);
            if (header != null) {
                header.setPadding(0, statusBarHeight, 0, 0);
            }

            View scrollView = findViewById(R.id.scroll_content);
            int navDp64 = Math.round(64 * getResources().getDisplayMetrics().density);
            if (scrollView != null)
                scrollView.setPadding(0, 0, 0, navDp64 + navBarHeight);

            return insets;
        });

        initViews();
        setupClickListeners();
        applyExpressiveAnimations();

        // [مرحلة 1.2] تهيئة ViewModel وربط الـ observers
        viewModel = new ViewModelProvider(this, new AppViewModelFactory(this))
                .get(HomeViewModel.class);
        observeViewModel();

        // بدء التحميل
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String uid = (currentUser != null) ? currentUser.getUid() : "";
        viewModel.init(uid, null); // governorate سيُحدَّث بعد الـ location

        getLocation();

        // [7.4] فحص التحديثات
        updateManager = new InAppUpdateManager(this);
        updateManager.checkForUpdate(false);
    }

    private void initViews() {
        tvGreeting          = findViewById(R.id.tv_greeting);
        tvLocation          = findViewById(R.id.tv_location);
        badgeNotifications  = findViewById(R.id.badge_notifications);
        badgeChats          = findViewById(R.id.badge_chats);
        ivUserAvatar        = findViewById(R.id.iv_user_avatar);
        tvStatActive        = findViewById(R.id.tv_stat_active);
        tvStatFound         = findViewById(R.id.tv_stat_found);
        tvStatToday         = findViewById(R.id.tv_stat_today);
        rvRecentReports     = findViewById(R.id.rv_recent_reports);

        recentAdapter = new RecentAdapter();
        fabAddReport   = findViewById(R.id.fab_add_report);
        rvRecentReports.setLayoutManager(
            new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        rvRecentReports.setAdapter(recentAdapter);
    }

    private void setupClickListeners() {
        // Header icons
        click(R.id.btn_notifications, () ->
            startActivity(new Intent(this, NotificationsActivity.class)));
        // Chat button - now visible next to notifications
        click(R.id.btn_chats, () ->
            startActivity(new Intent(this, ChatsListActivity.class)));
        click(R.id.iv_user_avatar, () -> openProfile());
        click(R.id.search_bar, () ->
            startActivity(new Intent(this, SearchFilterActivity.class)));
        click(R.id.stats_section, () ->
            startActivity(new Intent(this, StatisticsActivity.class)));
        click(R.id.card_leaderboard, () ->
        startActivity(new Intent(this, LeaderboardActivity.class)));
    click(R.id.card_success_stories, () ->
        startActivity(new Intent(this, SuccessStoriesActivity.class)));


        // See all
        click(R.id.btn_see_all, () -> startActivity(new Intent(this, BrowseActivity.class)));

        // Extended FAB
        if (fabAddReport != null) {
            // النقر القصير: بلاغ مفقود
            fabAddReport.setOnClickListener(v ->
                startActivity(new Intent(this, ReportActivity.class)));
            // النقر الطويل: يظهر/يخفي الأزرار السريعة
            fabAddReport.setOnLongClickListener(v -> {
                toggleQuickFabs();
                return true;
            });
        }

        // [إصلاح FoundPersonActivity] زر سريع — وجدت شخصاً
        android.widget.ImageButton fabFoundPerson = findViewById(R.id.fab_found_person);
        // FloatingActionButton cast
        com.google.android.material.floatingactionbutton.FloatingActionButton fabFP =
            findViewById(R.id.fab_found_person);
        if (fabFP != null) {
            fabFP.setOnClickListener(v -> {
                Intent i = new Intent(this, ReportActivity.class);
                i.putExtra("reportType", com.missingpersons.app.activities.ReportActivity.TYPE_FOUND);
                startActivity(i);
                fabFP.setVisibility(android.view.View.GONE);
                com.google.android.material.floatingactionbutton.FloatingActionButton fabS =
                    findViewById(R.id.fab_sighting);
                if (fabS != null) fabS.setVisibility(android.view.View.GONE);
            });
        }

        // [إصلاح FoundSightingActivity] زر سريع — رأيت شخصاً
        com.google.android.material.floatingactionbutton.FloatingActionButton fabSighting =
            findViewById(R.id.fab_sighting);
        if (fabSighting != null) {
            fabSighting.setOnClickListener(v -> {
                Intent i = new Intent(this, ReportActivity.class);
                i.putExtra("reportType", com.missingpersons.app.activities.ReportActivity.TYPE_SIGHTING);
                startActivity(i);
                fabSighting.setVisibility(android.view.View.GONE);
                if (fabFP != null) fabFP.setVisibility(android.view.View.GONE);
            });
        }
    }

    private void click(int id, Runnable action) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(__ -> action.run());
    }

    private void openProfile() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("تسجيل الدخول مطلوب")
                .setMessage("سجّل دخولك بحساب Google للوصول إلى ملفك الشخصي ومتابعة نقاطك وبلاغاتك.")
                .setPositiveButton("تسجيل الدخول", (d, w) -> {
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                })
                .setNegativeButton("لاحقاً", null)
                .show();
        } else {
            startActivity(new Intent(this, ProfileActivity.class));
        }
    }

    // ════════════════════════════════════════════════
    //  [مرحلة 1.2] Observe ViewModel → تحديث UI فقط
    // ════════════════════════════════════════════════

    private void observeViewModel() {

        // بيانات المستخدم
        viewModel.userInfo().observe(this, info -> {
            if (info == null) return;
            if (tvGreeting != null) {
                String first = info.name.isEmpty() ? "" : info.name.split(" ")[0];
                if (!first.isEmpty()) tvGreeting.setText("مرحباً، " + first + " 👋");
            }
            if (ivUserAvatar != null && !info.photoUrl.isEmpty())
                CoilImageLoader.load(this, info.photoUrl, ivUserAvatar);

            // تحديث نقاط المستخدم في الـ leaderboard badge
            TextView tvPts = findViewById(R.id.tv_user_points);
            if (tvPts != null) tvPts.setText(info.points + " نقطة");
        });

        // badges الإشعارات
        viewModel.unreadNotif().observe(this, count ->
            updateBadge(badgeNotifications, count != null ? count : 0));

        // badges المحادثات
        viewModel.unreadChats().observe(this, count ->
            updateBadge(badgeChats, count != null ? count : 0));

        // إحصائيات
        viewModel.stats().observe(this, s -> {
            if (s == null) return;
            if (tvStatActive != null) tvStatActive.setText(String.valueOf(s.active));
            if (tvStatFound  != null) tvStatFound.setText(String.valueOf(s.resolved));
            // today = الفرق بين approved اليوم (نحسبه من recent)
        });

        // Leaderboard top
        viewModel.leaderboardTop().observe(this, label -> {
            if (label == null || label.isEmpty()) return;
            TextView badge = findViewById(R.id.tv_leaderboard_badge);
            if (badge != null) badge.setText(label);
        });

        // أحدث البلاغات
        viewModel.recentReports().observe(this, reports -> {
            if (reports == null) return;
            recentReports.clear();
            recentReports.addAll(reports);
            recentAdapter.notifyDataSetChanged();
        });

        // Amber Alerts
        viewModel.amberAlerts().observe(this, alerts -> {
            if (alerts == null || alerts.isEmpty()) return;
            // أعِد تشغيل AmberAlertManager للـ governorate
            for (HomeViewModel.AmberAlert a : alerts) {
                if (!a.governorate.isEmpty()) {
                    AmberAlertManager.listenForAlerts(this, a.governorate);
                    break;
                }
            }
        });
    }

    private void loadUserInfo() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String name = user.getDisplayName();
        if (name != null && !name.isEmpty()) {
            String firstName = name.split(" ")[0];
            if (tvGreeting != null)
                tvGreeting.setText("مرحباً، " + firstName + " 👋");
        }

        // Load avatar with proper fallback
        String photoUrl = user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : null;
        if (photoUrl != null && !photoUrl.isEmpty() && ivUserAvatar != null) {
            CoilImageLoader.loadCircle(this, photoUrl, ivUserAvatar, R.drawable.ic_person);
        } else {
            // Try to get from database
            db.child("users").child(user.getUid()).child("photoUrl")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snap) {
                        String dbUrl = snap.getValue(String.class);
                        if (dbUrl != null && !dbUrl.isEmpty() && ivUserAvatar != null)
                            CoilImageLoader.loadCircle(NewHomeActivity.this, dbUrl,
                                ivUserAvatar, R.drawable.ic_person);
                        else
                            ivUserAvatar.setImageResource(R.drawable.ic_person);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {
                        ivUserAvatar.setImageResource(R.drawable.ic_person);
                    }
                });
        }
    }

    // FIX: Get real top leader from database
    private void loadLeaderboardTop() {
        db.child("users").orderByChild("points").limitToLast(1)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snap) {
                    DataSnapshot topUser = null;
                    for (DataSnapshot u : snap.getChildren()) topUser = u;
                    if (topUser == null) return;
                    
                    String fullName = topUser.child("name").getValue(String.class);
                    if (fullName == null || fullName.isEmpty())
                        fullName = topUser.child("displayName").getValue(String.class);
                    if (fullName == null || fullName.isEmpty()) return;
                    
                    String first = fullName.split(" ")[0];
                    if (first.length() > 10) first = first.substring(0, 10);
                    final String label = "#1 " + first;
                    runOnUiThread(() -> {
                        TextView badge = findViewById(R.id.tv_leaderboard_badge);
                        if (badge != null) badge.setText(label);
                    });
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });

        // Success stories count
        db.child("stats").child("resolved")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snap) {
                    Object v = snap.getValue();
                    long count = 0;
                    if (v instanceof Long) count = (Long) v;
                    else if (v instanceof Integer) count = ((Integer) v).longValue();
                    final String label = count + " حالة محلولة";
                    runOnUiThread(() -> {
                        TextView badge = findViewById(R.id.tv_success_stories_badge);
                        if (badge != null) badge.setText(label);
                    });
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
    }

    /**
     * [إصلاح خطأ-08] loadStats
     *
     * المشكلة السابقة: كانت تقرأ من stats/ فقط — وهو cache لا يُحدَّث
     *                  إلا عند فتح StatisticsActivity أو تدخل الأدمن.
     *
     * الحل:
     *  1. نقرأ أولاً من stats/ لعرض أرقام فورية (لا latency)
     *  2. ثم نحسب مباشرة من reports/ و found_persons/
     *     للحصول على الأرقام الصحيحة الحقيقية
     *  3. Cloud Function (functions/index.js) تُحدِّث stats/
     *     تلقائياً عند كل تغيير → في المرة القادمة تكون stats/ محدَّثة
     */
    private void loadStats() {
        // ① عرض stats/ cache فوراً (لا تأخير مرئي)
        db.child("stats").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                long active   = longVal(snap, "approved");
                long resolved = longVal(snap, "resolved");
                long today    = longVal(snap, "resolvedMonth");
                if (active > 0 || resolved > 0)
                    runOnUiThread(() -> updateStatViews(active, resolved, today));
                // ② بعد عرض cache، احسب الأرقام الحقيقية من الـ nodes مباشرة
                loadStatsFromNodes();
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                loadStatsFromNodes(); // fallback عند فشل قراءة cache
            }
        });
    }

    /**
     * يحسب الإحصائيات مباشرة من reports/ و found_persons/
     * بدون الاعتماد على stats/ cache.
     *
     * [إصلاح تعارض الحالات النشطة]
     * تعريف "نشط" موحّد الآن مع StatisticsActivity:
     *   نشط = status == "approved"
     * (الـ boolean flag "approved" هو نفسه ما يُعيّن status="approved"
     *  لذلك الشرطان متكافئان — نستخدم status فقط للبساطة)
     */
    private void loadStatsFromNodes() {
        final long[] active   = {0};
        final long[] resolved = {0};
        final long[] found    = {0};
        final int[] done = {0};

        ValueEventListener reportsListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                long a = 0, r = 0;
                for (DataSnapshot c : snap.getChildren()) {
                    String status = c.child("status").getValue(String.class);
                    // [إصلاح] نفس تعريف StatisticsActivity — status=="approved"
                    if ("approved".equals(status)) a++;
                    if ("resolved".equals(status)) r++;
                }
                active[0]   = a;
                resolved[0] = r;
                done[0]++;
                if (done[0] == 2) mergeAndDisplay(active[0], resolved[0], found[0]);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                done[0]++;
                if (done[0] == 2) mergeAndDisplay(active[0], resolved[0], found[0]);
            }
        };

        ValueEventListener foundListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                found[0] = snap.getChildrenCount();
                done[0]++;
                if (done[0] == 2) mergeAndDisplay(active[0], resolved[0], found[0]);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                done[0]++;
                if (done[0] == 2) mergeAndDisplay(active[0], resolved[0], found[0]);
            }
        };

        db.child("reports").addListenerForSingleValueEvent(reportsListener);
        db.child("found_persons").addListenerForSingleValueEvent(foundListener);
    }

    private void mergeAndDisplay(long active, long resolved, long found) {
        // active = بلاغات مفقودين نشطة
        // resolved + found = إجمالي المُعثور عليهم
        long totalFound = resolved + found;
        runOnUiThread(() -> updateStatViews(active, totalFound, resolved));

        // تحديث stats/ cache لتقليل Reads في المرة القادمة
        db.child("stats").child("approved").setValue(active);
        db.child("stats").child("resolved").setValue(resolved);
        db.child("stats").child("lastUpdated").setValue(System.currentTimeMillis());
    }

    private static long longVal(DataSnapshot s, String key) {
        Object v = s.child(key).getValue();
        if (v instanceof Long)    return (Long) v;
        if (v instanceof Integer) return ((Integer) v).longValue();
        return 0L;
    }

    private void updateStatViews(long active, long resolved, long today) {
        if (tvStatActive != null) tvStatActive.setText(String.valueOf(active));
        if (tvStatFound  != null) tvStatFound.setText(String.valueOf(resolved));
        if (tvStatToday  != null) tvStatToday.setText(String.valueOf(today));
    }

    // ══════════════════════════════════════════════════════════════
    //  RECENT REPORTS — Realtime: يتحدث فوراً عند موافقة الأدمن
    //  يستخدم addValueEventListener بدل addListenerForSingleValueEvent
    //  حتى تظهر البلاغات الجديدة بدون إعادة تشغيل الصفحة
    // ══════════════════════════════════════════════════════════════

    private void loadRecentReports() {
        // نظّف أي listeners قديمة قبل إعادة التسجيل
        for (int i = 0; i < recentListeners.size(); i++)
            recentRefs.get(i).removeEventListener(recentListeners.get(i));
        recentListeners.clear();
        recentRefs.clear();

        String[] nodes = {"reports", "found_persons", "sightings"};
        // snapshot مشترك بين الـ 3 nodes — key=reportId لمنع التكرار
        Map<String, Map<String, Object>> combined = new java.util.concurrent.ConcurrentHashMap<>();

        for (String node : nodes) {
            com.google.firebase.database.Query ref = db.child(node)
                .orderByChild("timestamp").limitToLast(10);

            ValueEventListener listener = new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    // احذف البيانات القديمة لهذا الـ node ثم أعد بناءها
                    for (String key : new ArrayList<>(combined.keySet())) {
                        Map<String, Object> m = combined.get(key);
                        if (m != null && node.equals(m.get("_node"))) combined.remove(key);
                    }

                    for (DataSnapshot c : snap.getChildren()) {
                        // فلتر: approved فقط (لا resolved, pending, إلخ)
                        String status = c.child("status").getValue(String.class);
                        if (!"approved".equals(status)) continue;

                        Map<String, Object> m = new HashMap<>();
                        for (DataSnapshot f : c.getChildren()) m.put(f.getKey(), f.getValue());
                        m.put("reportId", c.getKey());
                        m.put("_node", node);  // لتمييز مصدر البيانات عند التنظيف
                        if (!m.containsKey("reportType")) {
                            if ("found_persons".equals(node)) m.put("reportType", "found");
                            else if ("sightings".equals(node))  m.put("reportType", "sighting");
                            else                                  m.put("reportType", "missing");
                        }
                        combined.put(c.getKey(), m);
                    }

                    // رتّب من الأحدث واعرض أول 5
                    List<Map<String, Object>> sorted = new ArrayList<>(combined.values());
                    sorted.sort((a, b) -> {
                        long ta = a.get("timestamp") instanceof Long ? (Long) a.get("timestamp") : 0L;
                        long tb = b.get("timestamp") instanceof Long ? (Long) b.get("timestamp") : 0L;
                        return Long.compare(tb, ta);
                    });

                    final List<Map<String, Object>> top5 =
                        sorted.subList(0, Math.min(sorted.size(), 5));

                    runOnUiThread(() -> {
                        recentReports.clear();
                        recentReports.addAll(top5);
                        recentAdapter.notifyDataSetChanged();
                    });
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            };

            ref.addValueEventListener(listener);
            recentListeners.add(listener);
            recentRefs.add(ref);
        }
    }

    private void listenBadges() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) return;
        String uid = user.getUid();

        notifListener = db.child("notifications").child(uid)
            .orderByChild("read").equalTo(false)
            .addValueEventListener(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    updateBadge(badgeNotifications, (int) snap.getChildrenCount());
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });

        chatListener = db.child("chats")
            .addValueEventListener(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    int unread = 0;
                    for (DataSnapshot chat : snap.getChildren()) {
                        Object p1 = chat.child("user1").getValue();
                        Object p2 = chat.child("user2").getValue();
                        if (uid.equals(String.valueOf(p1)) || uid.equals(String.valueOf(p2))) {
                            Object count = chat.child("unread_" + uid).getValue();
                            if (count instanceof Long && (Long)count > 0) unread++;
                        }
                    }
                    final int f = unread;
                    runOnUiThread(() -> updateBadge(badgeChats, f));
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });

        // [إصلاح] استقبال الإشعارات الجماعية من الأدمن
        // AdminDashboardActivity يكتب لـ notifications/{uid} مباشرة
        // هذا الـ listener يعرضها كـ local notification فوراً للمستخدمين المتصلين
        listenBroadcastNotifications(uid);
    }

    /**
     * يستمع للإشعارات الجماعية من الأدمن في notifications/{uid}
     * ويعرضها كـ local notification
     */
    private long lastBroadcastTs = 0;

    private void listenBroadcastNotifications(String uid) {
        db.child("notifications").child(uid)
            .orderByChild("type").equalTo("broadcast")
            .addChildEventListener(new com.google.firebase.database.ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot snap, String prev) {
                    Boolean isRead = snap.child("read").getValue(Boolean.class);
                    if (Boolean.TRUE.equals(isRead)) return;

                    Long ts = snap.child("timestamp").getValue(Long.class);
                    if (ts == null || ts <= lastBroadcastTs) return;
                    lastBroadcastTs = ts;

                    String title = snap.child("title").getValue(String.class);
                    String msg   = snap.child("message").getValue(String.class);
                    if (title == null) title = "📢 إشعار من الإدارة";
                    if (msg == null || msg.isEmpty()) return;

                    final String fTitle = title;
                    final String fMsg   = msg;

                    runOnUiThread(() -> {
                        com.missingpersons.app.utils.NotificationHelper
                            .showGeneralNotification(NewHomeActivity.this, fTitle, fMsg);
                        // تحديث badge الإشعارات
                        if (badgeNotifications != null)
                            badgeNotifications.setVisibility(View.VISIBLE);
                    });

                    // عَلِّم كمقروء
                    snap.getRef().child("read").setValue(true);
                }
                @Override public void onChildChanged(@NonNull DataSnapshot s, String p) {}
                @Override public void onChildRemoved(@NonNull DataSnapshot s) {}
                @Override public void onChildMoved(@NonNull DataSnapshot s, String p) {}
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
    }

    private void updateBadge(TextView badge, int count) {
        if (badge == null) return;
        if (count <= 0) {
            badge.setVisibility(View.GONE);
        } else {
            badge.setVisibility(View.VISIBLE);
            badge.setText(count > 99 ? "99+" : String.valueOf(count));
        }
    }

    private void getLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
            return;
        }
        locationClient.getLastLocation().addOnSuccessListener(loc -> {
            if (loc != null) {
                myLocation = loc;
                reverseGeocode(loc);
            }
        });
    }

    private void reverseGeocode(Location loc) {
        new Thread(() -> {
            try {
                android.location.Geocoder gc = new android.location.Geocoder(
                    this, new Locale("ar"));
                List<android.location.Address> addrs = gc.getFromLocation(
                    loc.getLatitude(), loc.getLongitude(), 1);
                if (addrs != null && !addrs.isEmpty()) {
                    String city = addrs.get(0).getLocality();
                    if (city == null) city = addrs.get(0).getAdminArea();
                    final String finalCity = city != null ? city : "موقعك";
                    runOnUiThread(() -> {
                        if (tvLocation != null)
                            tvLocation.setText("📍 " + finalCity);
                        // [مرحلة 1.2] أبلِغ ViewModel بالمحافظة لتحميل Amber Alerts
                        if (viewModel != null)
                            viewModel.loadAmberAlerts(finalCity);
                    });
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private String safeStr(DataSnapshot c, String key, String def) {
        Object v = c.child(key).getValue();
        return v instanceof String ? (String)v : def;
    }

    private String getTimeAgo(long ts) {
        long diff = System.currentTimeMillis() - ts;
        long days = diff / 86400000L;
        if (days == 0) return "اليوم";
        if (days == 1) return "منذ يوم";
        if (days < 7)  return "منذ " + days + " أيام";
        return new SimpleDateFormat("dd/MM", new Locale("ar")).format(new Date(ts));
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        if (req == 101 && grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED)
            getLocation();
        loadAmberAlerts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (updateManager != null) updateManager.onResume();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == InAppUpdateManager.UPDATE_REQUEST_CODE
                && resultCode != RESULT_OK
                && updateManager != null) {
            updateManager.onUpdateCancelled();
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (updateManager != null) updateManager.unregister();
        // [مرحلة 1.2] ViewModel يتولى تنظيف الـ listeners في onCleared()
        // نبقّي الـ local cleanup للـ fallback القديم
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u != null && db != null) {
            if (notifListener != null)
                db.child("notifications").child(u.getUid()).removeEventListener(notifListener);
            if (chatListener != null)
                db.child("chats").removeEventListener(chatListener);
        }
        for (int i = 0; i < recentListeners.size() && i < recentRefs.size(); i++)
            recentRefs.get(i).removeEventListener(recentListeners.get(i));
        recentListeners.clear();
        recentRefs.clear();
    }

    // Recent Reports Adapter
    class RecentAdapter extends RecyclerView.Adapter<RecentAdapter.VH> {

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(buildRowView(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Map<String, Object> r = recentReports.get(pos);
            String name = r.get("personName") instanceof String ? (String)r.get("personName") : "مجهول";
            String gov = r.get("governorate") instanceof String ? (String)r.get("governorate") : "";
            String rid = r.get("reportId") instanceof String ? (String)r.get("reportId") : "";
            boolean resolved = "resolved".equals(r.get("status"));

            Object tsObj = r.get("timestamp");
            String timeAgo = "";
            if (tsObj instanceof Long) timeAgo = getTimeAgo((Long)tsObj);

            h.tvName.setText(name);
            String sub = (gov.isEmpty() ? "" : gov) +
                         (!gov.isEmpty() && !timeAgo.isEmpty() ? " • " : "") +
                         timeAgo;
            h.tvSub.setText(sub.isEmpty() ? "—" : sub);

            if (resolved) {
                h.tvBadge.setText("تم إيجاده");
                h.tvBadge.setTextColor(0xFF1B5E20);
                h.tvBadge.setBackgroundResource(R.drawable.bg_badge_green_light);
            } else {
                Object tsRaw = r.get("timestamp");
                long days = 0;
                if (tsRaw instanceof Long)
                    days = (System.currentTimeMillis() - (Long)tsRaw) / 86400000L;
                if (days <= 1) {
                    h.tvBadge.setText("جديد");
                    h.tvBadge.setTextColor(0xFF1B5E20);
                    h.tvBadge.setBackgroundResource(R.drawable.bg_badge_green_light);
                } else if (days <= 3) {
                    h.tvBadge.setText("عاجل");
                    h.tvBadge.setTextColor(0xFFC62828);
                    h.tvBadge.setBackgroundResource(R.drawable.bg_badge_red_light);
                } else {
                    h.tvBadge.setText("مفقود");
                    h.tvBadge.setTextColor(0xFF1565C0);
                    h.tvBadge.setBackgroundResource(R.drawable.bg_badge_blue_light);
                }
            }

            Object urlsObj = r.get("imageUrls");
            String imgUrl = null;
            if (urlsObj instanceof List && !((List<?>)urlsObj).isEmpty())
                imgUrl = ((List<?>)urlsObj).get(0).toString();
            if (imgUrl == null) imgUrl = r.get("imageUrl") instanceof String
                ? (String)r.get("imageUrl") : "";

            if (!imgUrl.isEmpty())
                CoilImageLoader.loadRounded(h.ivPhoto.getContext(), imgUrl,
                    h.ivPhoto, R.drawable.ic_face_placeholder, 12f);
            else
                h.ivPhoto.setImageResource(R.drawable.ic_face_placeholder);

            final String fRid = rid;
            h.itemView.setOnClickListener(v ->
                startActivity(new Intent(NewHomeActivity.this, CaseDetailActivity.class)
                    .putExtra("reportId", fRid)));

            h.divider.setVisibility(pos < recentReports.size() - 1 ? View.VISIBLE : View.GONE);
        }

        @Override public int getItemCount() { return recentReports.size(); }

        private android.widget.FrameLayout buildRowView(android.content.Context ctx) {
            int dp4  = dpToPx(ctx, 4);
            int dp8  = dpToPx(ctx, 8);
            int dp10 = dpToPx(ctx, 10);
            int dp12 = dpToPx(ctx, 12);
            int dp14 = dpToPx(ctx, 14);
            int dp44 = dpToPx(ctx, 44);
            int dp1  = dpToPx(ctx, 1);

            android.widget.FrameLayout wrapper = new android.widget.FrameLayout(ctx);
            wrapper.setLayoutParams(new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT));
            wrapper.setClickable(true);
            wrapper.setFocusable(true);
            wrapper.setForeground(ctx.obtainStyledAttributes(
                new int[]{android.R.attr.selectableItemBackground}).getDrawable(0));

            android.widget.LinearLayout row = new android.widget.LinearLayout(ctx);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp14, dp12, dp14, dp12);
            row.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));

            android.widget.FrameLayout photoWrapper = new android.widget.FrameLayout(ctx);
            android.widget.LinearLayout.LayoutParams pwLp =
                new android.widget.LinearLayout.LayoutParams(dp44, dp44);
            pwLp.setMarginEnd(dp10);
            photoWrapper.setLayoutParams(pwLp);
            photoWrapper.setBackgroundColor(0xFFE8F0FE);

            ImageView ivPhoto = new ImageView(ctx);
            ivPhoto.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
            ivPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photoWrapper.addView(ivPhoto);
            row.addView(photoWrapper);

            android.widget.LinearLayout info = new android.widget.LinearLayout(ctx);
            info.setOrientation(android.widget.LinearLayout.VERTICAL);
            android.widget.LinearLayout.LayoutParams infoLp =
                new android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            info.setLayoutParams(infoLp);

            TextView tvName = new TextView(ctx);
            tvName.setTextSize(13f);
            tvName.setTextColor(0xFF1B2A6B);
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);
            tvName.setMaxLines(1);
            tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);

            TextView tvSub = new TextView(ctx);
            tvSub.setTextSize(11f);
            tvSub.setTextColor(0xFF888888);
            tvSub.setMaxLines(1);
            android.widget.LinearLayout.LayoutParams subLp =
                new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            subLp.topMargin = dpToPx(ctx, 2);
            tvSub.setLayoutParams(subLp);

            info.addView(tvName);
            info.addView(tvSub);
            row.addView(info);

            TextView tvBadge = new TextView(ctx);
            tvBadge.setTextSize(10f);
            tvBadge.setTypeface(null, android.graphics.Typeface.BOLD);
            tvBadge.setBackgroundResource(R.drawable.bg_badge_red_light);
            android.widget.LinearLayout.LayoutParams bdgLp =
                new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            bdgLp.setMarginStart(dp8);
            tvBadge.setLayoutParams(bdgLp);
            tvBadge.setPadding(dp8, dp4, dp8, dp4);
            row.addView(tvBadge);

            wrapper.addView(row);

            View divider = new View(ctx);
            android.widget.FrameLayout.LayoutParams divLp =
                new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT, dp1);
            divLp.gravity = android.view.Gravity.BOTTOM;
            divLp.leftMargin  = dpToPx(ctx, 68);
            divLp.rightMargin = dp14;
            divider.setLayoutParams(divLp);
            divider.setBackgroundColor(0xFFF5F5F5);
            wrapper.addView(divider);

            return wrapper;
        }

        private int dpToPx(android.content.Context ctx, int dp) {
            return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView ivPhoto;
            android.widget.FrameLayout ivPhotoWrapper;
            TextView tvName, tvSub, tvBadge;
            View divider;

            VH(@NonNull View v) {
                super(v);
                android.widget.FrameLayout wrapper = (android.widget.FrameLayout) v;
                android.widget.LinearLayout row    = (android.widget.LinearLayout) wrapper.getChildAt(0);
                ivPhotoWrapper = (android.widget.FrameLayout) row.getChildAt(0);
                ivPhoto        = (ImageView) ivPhotoWrapper.getChildAt(0);
                android.widget.LinearLayout info   = (android.widget.LinearLayout) row.getChildAt(1);
                tvName  = (TextView) info.getChildAt(0);
                tvSub   = (TextView) info.getChildAt(1);
                tvBadge = (TextView) row.getChildAt(2);
                divider = wrapper.getChildAt(1);

                int r12 = Math.round(12 * v.getContext().getResources().getDisplayMetrics().density);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    ivPhotoWrapper.setClipToOutline(true);
                    ivPhotoWrapper.setOutlineProvider(new android.view.ViewOutlineProvider() {
                        @Override
                        public void getOutline(android.view.View view, android.graphics.Outline outline) {
                            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r12);
                        }
                    });
                }
            }
        }
    }

    private void applyExpressiveAnimations() {
        if (fabAddReport != null) applySpringPress(fabAddReport);
        View cardLb = findViewById(R.id.card_leaderboard);
        View cardSs = findViewById(R.id.card_success_stories);
        if (cardLb != null) applySpringPress(cardLb);
        if (cardSs != null) applySpringPress(cardSs);

        animateEntrance(R.id.tv_stat_active, 0);
        animateEntrance(R.id.tv_stat_found,  80);
        animateEntrance(R.id.tv_stat_today,  160);
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private void applySpringPress(View v) {
        v.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.94f).scaleY(0.94f)
                        .setDuration(120)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f)
                        .setDuration(200)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2.5f))
                        .start();
                    if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                        view.performClick();
                    }
                    break;
            }
            return true;
        });
    }

    private void animateEntrance(int viewId, long delay) {
        View v = findViewById(viewId);
        if (v == null) return;
        v.setAlpha(0f);
        v.setTranslationY(20f);
        v.animate()
            .alpha(1f).translationY(0f)
            .setDuration(350)
            .setStartDelay(delay)
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .start();
    }

    private void loadAmberAlerts() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null || u.isAnonymous()) return;
        FirebaseDatabase.getInstance().getReference("users")
            .child(u.getUid()).child("governorate")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    String gov = snap.getValue(String.class);
                    if (gov != null && !gov.isEmpty())
                        AmberAlertManager.listenForAlerts(NewHomeActivity.this, gov);
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
    }
    /** يظهر/يخفي أزرار الإجراء السريع (وجدت شخصاً / رأيت شخصاً) */
    private boolean quickFabsVisible = false;
    private void toggleQuickFabs() {
        com.google.android.material.floatingactionbutton.FloatingActionButton fabFP =
            findViewById(R.id.fab_found_person);
        com.google.android.material.floatingactionbutton.FloatingActionButton fabS =
            findViewById(R.id.fab_sighting);
        quickFabsVisible = !quickFabsVisible;
        int vis = quickFabsVisible ? android.view.View.VISIBLE : android.view.View.GONE;
        if (fabFP != null) fabFP.setVisibility(vis);
        if (fabS  != null) fabS.setVisibility(vis);
    }


}