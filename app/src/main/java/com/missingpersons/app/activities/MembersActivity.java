package com.missingpersons.app.activities;

import android.os.Bundle;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.missingpersons.app.R;
import com.missingpersons.app.utils.AbuseReportHelper;
import com.missingpersons.app.utils.LanguageHelper;
import com.missingpersons.app.utils.RoleManager;
import java.util.*;

/**
 * MembersActivity — إدارة الأعضاء
 *
 * الصلاحية: الأدمن أو مدير لديه canManageMembers
 * ✅ يعمل offline (يستخدم RoleManager cache)
 * ✅ الموافقة على عضو لنشر بلاغات
 * ✅ حظر / رفع حظر
 * ✅ ترقية لمدير (أدمن فقط)
 * ✅ تخفيض لعضو (أدمن فقط)
 */
public class MembersActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty, tvCount, tvStats;
    private EditText etSearch;
    private MaterialButton btnSearch;

    private DatabaseReference usersRef, reportsRef;
    private MembersAdapter adapter;
    private List<HashMap<String, Object>> allUsers      = new ArrayList<>();
    private List<HashMap<String, Object>> filteredUsers = new ArrayList<>();
    private HashMap<String, Integer> reportCounts       = new HashMap<>();

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LanguageHelper.applyLanguage(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_members_v2);
        // [إصلاح 6 — Edge-to-Edge]
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bot = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), bot);
            return insets;
        });

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("👥 إدارة الأعضاء");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (FirebaseAuth.getInstance().getCurrentUser() == null) { finish(); return; }

        // ← RoleManager يدعم Offline: يحمّل من الـ cache إذا لا اتصال
        RoleManager.get().load(new RoleManager.LoadCallback() {
            @Override
            public void onLoaded(boolean isAdminOrManager) {
                RoleManager role = RoleManager.get();
                if (!role.canManageMembers()) {
                    runOnUiThread(() -> {
                        Toast.makeText(MembersActivity.this,
                            "غير مصرح لك بالدخول", Toast.LENGTH_LONG).show();
                        finish();
                    });
                    return;
                }
                runOnUiThread(() -> {
                    initViews();
                    loadReportCounts();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(MembersActivity.this,
                        "خطأ في التحقق: " + message, Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void initViews() {
        recyclerView = findViewById(R.id.rv_members);
        progressBar  = findViewById(R.id.progress_members);
        tvEmpty      = findViewById(R.id.tv_empty_members);
        tvCount      = findViewById(R.id.tv_members_count);
        tvStats      = findViewById(R.id.tv_members_stats);
        etSearch     = findViewById(R.id.et_search_member);
        btnSearch    = findViewById(R.id.btn_search_member);

        usersRef   = FirebaseDatabase.getInstance().getReference("users");
        reportsRef = FirebaseDatabase.getInstance().getReference("reports");

        if (recyclerView != null)
            recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new MembersAdapter(filteredUsers, this::onMemberAction);
        if (recyclerView != null) recyclerView.setAdapter(adapter);

        if (btnSearch != null)
            btnSearch.setOnClickListener(v ->
                filterUsers(etSearch != null ? etSearch.getText().toString().trim() : ""));
        if (etSearch != null)
            etSearch.setOnEditorActionListener((tv, actionId, event) -> {
                filterUsers(etSearch.getText().toString().trim());
                return true;
            });
    }

    private void loadReportCounts() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        reportsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                reportCounts.clear();
                for (DataSnapshot c : snap.getChildren()) {
                    String rid = c.child("reporterId").getValue(String.class);
                    if (rid != null)
                        reportCounts.put(rid, reportCounts.getOrDefault(rid, 0) + 1);
                }
                loadUsers();
            }
            @Override public void onCancelled(@NonNull DatabaseError e) { loadUsers(); }
        });
    }

    private void loadUsers() {
        usersRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                allUsers.clear();
                String currentUid = FirebaseAuth.getInstance().getCurrentUser() != null
                    ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";

                int totalCount = 0, activeCount = 0, bannedCount = 0;

                for (DataSnapshot c : snap.getChildren()) {
                    if (c.getKey().equals(currentUid)) continue;

                    // لا نعرض الأدمن والمديرين الآخرين
                    String role = c.child("role").getValue(String.class);
                    if (RoleManager.ROLE_ADMIN.equals(role)) continue;

                    totalCount++;
                    boolean banned = Boolean.TRUE.equals(c.child("banned").getValue(Boolean.class));
                    if (banned) bannedCount++;
                    Long lastLogin = c.child("lastLogin").getValue(Long.class);
                    boolean active = lastLogin != null
                        && (System.currentTimeMillis() - lastLogin) < 7 * 24 * 60 * 60 * 1000L;
                    if (active && !banned) activeCount++;

                    Boolean isApproved = c.child("approved").getValue(Boolean.class);

                    HashMap<String, Object> map = new HashMap<>();
                    map.put("uid",       c.getKey());
                    map.put("name",      c.child("name").getValue());
                    map.put("email",     c.child("email").getValue());
                    map.put("role",      role);
                    map.put("banned",    banned);
                    map.put("approved",  Boolean.TRUE.equals(isApproved));
                    map.put("lastLogin", lastLogin);
                    map.put("active",    active);
                    map.put("postCount", reportCounts.getOrDefault(c.getKey(), 0));
                    allUsers.add(map);
                }

                allUsers.sort((a, b) -> {
                    long la = a.get("lastLogin") instanceof Long ? (Long)a.get("lastLogin") : 0L;
                    long lb = b.get("lastLogin") instanceof Long ? (Long)b.get("lastLogin") : 0L;
                    return Long.compare(lb, la);
                });

                if (tvStats != null)
                    tvStats.setText("👥 " + totalCount + " عضو"
                        + "   |   🟢 " + activeCount + " نشط"
                        + "   |   🔴 " + bannedCount + " محظور");

                filterUsers(etSearch != null ? etSearch.getText().toString().trim() : "");
                if (progressBar != null) progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError e) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void filterUsers(String query) {
        filteredUsers.clear();
        for (HashMap<String, Object> u : allUsers) {
            String name  = u.get("name")  instanceof String ? (String)u.get("name")  : "";
            String email = u.get("email") instanceof String ? (String)u.get("email") : "";
            if (query.isEmpty() || name.contains(query) || email.contains(query))
                filteredUsers.add(u);
        }
        if (tvCount != null) tvCount.setText("إجمالي المعروض: " + filteredUsers.size());
        if (tvEmpty != null) tvEmpty.setVisibility(filteredUsers.isEmpty() ? View.VISIBLE : View.GONE);
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void onMemberAction(String action, HashMap<String, Object> user) {
        RoleManager role = RoleManager.get();
        String uid  = (String) user.get("uid");
        String name = user.get("name") instanceof String ? (String)user.get("name") : "المستخدم";
        if (uid == null) return;

        DatabaseReference userRef = usersRef.child(uid);

        switch (action) {

            case "approve":
                // الموافقة على العضو لنشر بلاغات
                if (!role.canManageMembers()) {
                    Toast.makeText(this, "⛔ ليس لديك صلاحية الموافقة", Toast.LENGTH_SHORT).show();
                    return;
                }
                boolean currentApproval = Boolean.TRUE.equals(user.get("approved"));
                new AlertDialog.Builder(this)
                    .setTitle(currentApproval ? "❌ إلغاء الموافقة" : "✅ الموافقة على العضو")
                    .setMessage(currentApproval
                        ? "إلغاء إذن «" + name + "» بنشر البلاغات؟"
                        : "السماح لـ«" + name + "» بنشر البلاغات؟")
                    .setPositiveButton("تأكيد", (d, w) ->
                        RoleManager.approveMember(uid, !currentApproval,
                            () -> Toast.makeText(this,
                                !currentApproval ? "✅ تمت الموافقة على " + name
                                                 : "❌ تم إلغاء الموافقة",
                                Toast.LENGTH_SHORT).show(),
                            err -> Toast.makeText(this, "❌ " + err, Toast.LENGTH_SHORT).show()))
                    .setNegativeButton("إلغاء", null).show();
                break;

            case "ban":
                if (!role.canBanUsers()) {
                    Toast.makeText(this, "⛔ ليس لديك صلاحية الحظر", Toast.LENGTH_SHORT).show();
                    return;
                }
                boolean isBanned = Boolean.TRUE.equals(user.get("banned"));
                new AlertDialog.Builder(this)
                    .setTitle(isBanned ? "✅ رفع الحظر" : "🚫 حظر العضو")
                    .setMessage(isBanned
                        ? "رفع الحظر عن «" + name + "»؟"
                        : "حظر «" + name + "»؟")
                    .setPositiveButton(isBanned ? "رفع الحظر" : "حظر", (d, w) -> {
                        userRef.child("banned").setValue(!isBanned);
                        if (!isBanned) {
                            userRef.child("bannedAt").setValue(System.currentTimeMillis());
                            userRef.child("bannedBy").setValue(
                                FirebaseAuth.getInstance().getCurrentUser() != null
                                    ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "");
                        }
                        Toast.makeText(this,
                            isBanned ? "✅ رُفع الحظر عن " + name : "✅ تم حظر " + name,
                            Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("إلغاء", null).show();
                break;

            case "promote":
                if (!role.isAdmin()) {
                    Toast.makeText(this, "⛔ الترقية للأدمن فقط", Toast.LENGTH_SHORT).show();
                    return;
                }
                showPromoteDialog(name, uid);
                break;

            case "demote":
                if (!role.isAdmin()) {
                    Toast.makeText(this, "⛔ التخفيض للأدمن فقط", Toast.LENGTH_SHORT).show();
                    return;
                }
                new AlertDialog.Builder(this)
                    .setTitle("⬇️ إلغاء ترقية")
                    .setMessage("تحويل «" + name + "» لعضو عادي؟")
                    .setPositiveButton("تأكيد", (d, w) ->
                        RoleManager.demoteToMember(uid,
                            () -> Toast.makeText(this, "✅ تم تحويل " + name + " لعضو",
                                Toast.LENGTH_SHORT).show(),
                            err -> Toast.makeText(this, "❌ " + err, Toast.LENGTH_SHORT).show()))
                    .setNegativeButton("إلغاء", null).show();
                break;

            case "delete":
                if (!role.isAdmin()) {
                    Toast.makeText(this, "⛔ الحذف للأدمن فقط", Toast.LENGTH_SHORT).show();
                    return;
                }
                new AlertDialog.Builder(this)
                    .setTitle("🗑️ حذف نهائي")
                    .setMessage("حذف «" + name + "» نهائياً؟")
                    .setPositiveButton("حذف نهائي", (d, w) -> {
                        userRef.removeValue();
                        Toast.makeText(this, "✅ تم الحذف", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("إلغاء", null).show();
                break;

            case "limit":
                if (!role.isAdmin()) {
                    Toast.makeText(this, "⛔ هذا الإعداد للأدمن فقط", Toast.LENGTH_SHORT).show();
                    return;
                }
                showMemberLimitDialog(name, uid);
                break;

            case "abuse":
                String abuseEmail = user.get("email") instanceof String
                    ? (String)user.get("email") : name;
                AbuseReportHelper.showReportDialog(this,
                    AbuseReportHelper.ReportTarget.MEMBER, uid, "عضو: " + abuseEmail);
                break;
        }
    }

    private void showPromoteDialog(String name, String uid) {
        String[] labels = {
            "✅ الموافقة على البلاغات",
            "🗑️ حذف البلاغات",
            "👥 إدارة الأعضاء",
            "🚫 حظر المستخدمين",
            "📋 عرض جميع البلاغات",
            "✏️ تعديل البلاغات",
            "🔔 إرسال إشعارات"
        };
        String[] keys = {
            RoleManager.PERM_APPROVE_REPORTS,
            RoleManager.PERM_DELETE_REPORTS,
            RoleManager.PERM_MANAGE_MEMBERS,
            RoleManager.PERM_BAN_USERS,
            RoleManager.PERM_VIEW_ALL_REPORTS,
            RoleManager.PERM_EDIT_REPORTS,
            RoleManager.PERM_SEND_NOTIFICATIONS
        };
        boolean[] checked = {true, false, false, false, true, false, false};

        new AlertDialog.Builder(this)
            .setTitle("⭐ ترقية «" + name + "» لمدير")
            .setMultiChoiceItems(labels, checked,
                (d, which, isChecked) -> checked[which] = isChecked)
            .setPositiveButton("ترقية", (d, w) -> {
                Map<String, Object> perms = new HashMap<>();
                for (int i = 0; i < keys.length; i++) perms.put(keys[i], checked[i]);
                RoleManager.promoteToManager(uid, perms,
                    () -> Toast.makeText(this, "✅ تمت ترقية " + name, Toast.LENGTH_SHORT).show(),
                    err -> Toast.makeText(this, "❌ " + err, Toast.LENGTH_SHORT).show());
            })
            .setNegativeButton("إلغاء", null).show();
    }

    private void showMemberLimitDialog(String name, String uid) {
        usersRef.child(uid).child("dailyReportLimit")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    Integer current = snap.getValue(Integer.class);
                    EditText et = new EditText(MembersActivity.this);
                    et.setInputType(InputType.TYPE_CLASS_NUMBER);
                    et.setText(current != null ? String.valueOf(current) : "5");
                    et.setPadding(40, 20, 40, 20);
                    new AlertDialog.Builder(MembersActivity.this)
                        .setTitle("📊 الحد اليومي لـ «" + name + "»")
                        .setMessage("0 = بلا حد")
                        .setView(et)
                        .setPositiveButton("حفظ", (d, w) -> {
                            String v = et.getText().toString().trim();
                            if (!v.isEmpty()) {
                                usersRef.child(uid).child("dailyReportLimit")
                                    .setValue(Integer.parseInt(v));
                                Toast.makeText(MembersActivity.this,
                                    "✅ تم التحديث", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("إلغاء", null).show();
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
    }

    // ════════════════════════════════════════════════════════════
    //  ADAPTER
    // ════════════════════════════════════════════════════════════

    static class MembersAdapter extends RecyclerView.Adapter<MembersAdapter.VH> {
        private final List<HashMap<String, Object>> items;
        private final ActionListener listener;

        interface ActionListener {
            void onAction(String action, HashMap<String, Object> user);
        }

        MembersAdapter(List<HashMap<String, Object>> items, ActionListener listener) {
            this.items    = items;
            this.listener = listener;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = android.view.LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_member_v2, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            HashMap<String, Object> u = items.get(pos);
            RoleManager role = RoleManager.get();

            String name  = u.get("name")  instanceof String ? (String)u.get("name")  : "بدون اسم";
            String email = u.get("email") instanceof String ? (String)u.get("email") : "";
            String uRole = u.get("role")  instanceof String ? (String)u.get("role")  : ROLE_MEMBER;
            boolean banned   = Boolean.TRUE.equals(u.get("banned"));
            boolean approved = Boolean.TRUE.equals(u.get("approved"));
            boolean active   = Boolean.TRUE.equals(u.get("active"));
            int postCount    = u.get("postCount") instanceof Integer ? (Integer)u.get("postCount") : 0;

            h.tvName.setText(name);
            if (h.tvEmail   != null) h.tvEmail.setText(email);
            if (h.tvReports != null) h.tvReports.setText("📋 " + postCount + " بلاغ");

            // بادج الحالة
            if (h.tvRole != null) {
                if (RoleManager.ROLE_MANAGER.equals(uRole)) h.tvRole.setText("⭐ مدير");
                else if (banned)  h.tvRole.setText("🔴 محظور");
                else if (approved) h.tvRole.setText("✅ معتمد");
                else if (active)  h.tvRole.setText("🟢 نشط");
                else              h.tvRole.setText("⚪ غير نشط");
            }

            // زر الحظر/رفع الحظر
            if (h.btnBan != null) {
                h.btnBan.setVisibility(role.canBanUsers() ? View.VISIBLE : View.GONE);
                h.btnBan.setText(banned ? "✅ رفع الحظر" : "🚫 حظر");
                h.btnBan.setOnClickListener(v -> listener.onAction("ban", u));
            }

            // زر الموافقة
            if (h.btnApprove != null) {
                h.btnApprove.setVisibility(role.canManageMembers() ? View.VISIBLE : View.GONE);
                h.btnApprove.setText(approved ? "❌ إلغاء الموافقة" : "✅ موافقة");
                h.btnApprove.setOnClickListener(v -> listener.onAction("approve", u));
            }

            // زر الترقية: الأدمن فقط
            if (h.btnPromote != null) {
                boolean isManagerAlready = RoleManager.ROLE_MANAGER.equals(uRole);
                h.btnPromote.setVisibility(role.isAdmin() ? View.VISIBLE : View.GONE);
                h.btnPromote.setText(isManagerAlready ? "⬇️ تخفيض" : "⭐ ترقية");
                h.btnPromote.setOnClickListener(v ->
                    listener.onAction(isManagerAlready ? "demote" : "promote", u));
            }

            // زر الحد اليومي: الأدمن فقط
            if (h.btnLimit != null) {
                h.btnLimit.setVisibility(role.isAdmin() ? View.VISIBLE : View.GONE);
                h.btnLimit.setOnClickListener(v -> listener.onAction("limit", u));
            }
        }

        private static final String ROLE_MEMBER = RoleManager.ROLE_MEMBER;

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            de.hdodenhof.circleimageview.CircleImageView ivAvatar;
            TextView tvName, tvEmail, tvRole, tvReports;
            TextView btnBan, btnApprove, btnPromote, btnLimit;

            VH(@NonNull View v) {
                super(v);
                ivAvatar   = v.findViewById(R.id.iv_member_avatar);
                tvName     = v.findViewById(R.id.tv_member_name);
                tvEmail    = v.findViewById(R.id.tv_member_email);
                tvRole     = v.findViewById(R.id.tv_member_role);
                tvReports  = v.findViewById(R.id.tv_member_reports);
                btnBan     = v.findViewById(R.id.btn_member_ban);
                btnApprove = v.findViewById(R.id.btn_member_approve);
                btnPromote = v.findViewById(R.id.btn_member_promote);
                btnLimit   = v.findViewById(R.id.btn_member_limit);
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() { onBackPressed(); return true; }
}
