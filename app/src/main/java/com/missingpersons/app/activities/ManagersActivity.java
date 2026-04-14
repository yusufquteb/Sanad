package com.missingpersons.app.activities;

import android.os.Bundle;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.missingpersons.app.R;
import com.missingpersons.app.utils.LanguageHelper;
import com.missingpersons.app.utils.RoleManager;
import java.util.*;

/**
 * ManagersActivity — إدارة المديرين وصلاحياتهم
 * الصلاحية: الأدمن فقط (يتحقق من Firebase DB)
 *
 * ✅ عرض جميع المديرين
 * ✅ تعديل صلاحيات كل مدير بشكل مستقل
 * ✅ حظر / رفع حظر المدير
 * ✅ تخفيض المدير لعضو عادي
 */
public class ManagersActivity extends AppCompatActivity {

    public static final String[] PERM_KEYS = {
        RoleManager.PERM_APPROVE_REPORTS,
        RoleManager.PERM_DELETE_REPORTS,
        RoleManager.PERM_MANAGE_MEMBERS,
        RoleManager.PERM_BAN_USERS,
        RoleManager.PERM_VIEW_ALL_REPORTS,
        RoleManager.PERM_EDIT_REPORTS,
        RoleManager.PERM_SEND_NOTIFICATIONS
    };

    public static final String[] PERM_LABELS = {
        "✅ الموافقة على البلاغات",
        "🗑️ حذف البلاغات",
        "👥 إدارة الأعضاء",
        "🚫 حظر المستخدمين",
        "📋 عرض جميع البلاغات",
        "✏️ تعديل البلاغات",
        "🔔 إرسال الإشعارات"
    };

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty, tvCount;

    private DatabaseReference usersRef;
    private ManagersAdapter adapter;
    private List<HashMap<String, Object>> managers = new ArrayList<>();

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LanguageHelper.applyLanguage(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_managers);
        // [إصلاح 6 — Edge-to-Edge]
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bot = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), bot);
            return insets;
        });

        if (FirebaseAuth.getInstance().getCurrentUser() == null) { finish(); return; }

        // ← التحقق من Firebase DB بدل البريد الإلكتروني
        RoleManager.get().load(new RoleManager.LoadCallback() {
            @Override
            public void onLoaded(boolean isAdminOrManager) {
                if (!RoleManager.get().isAdmin()) {
                    Toast.makeText(ManagersActivity.this,
                        "هذه الصفحة للأدمن فقط", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                runOnUiThread(() -> {
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle("⭐ إدارة المديرين");
                        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                    }
                    initViews();
                    loadManagers();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(ManagersActivity.this,
                        "خطأ: " + message, Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void initViews() {
        recyclerView = findViewById(R.id.rv_managers);
        progressBar  = findViewById(R.id.progress_managers);
        tvEmpty      = findViewById(R.id.tv_empty_managers);
        tvCount      = findViewById(R.id.tv_managers_count);

        usersRef = FirebaseDatabase.getInstance().getReference("users");
        if (recyclerView != null) recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ManagersAdapter(managers, this::onManagerAction, this::onEditPermissions);
        if (recyclerView != null) recyclerView.setAdapter(adapter);
    }

    private void loadManagers() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        usersRef.orderByChild("role").equalTo("manager")
            .addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snap) {
                    managers.clear();
                    for (DataSnapshot child : snap.getChildren()) {
                        HashMap<String, Object> manager = new HashMap<>();
                        manager.put("uid", child.getKey());
                        String name = child.child("name").getValue(String.class);
                        String email = child.child("email").getValue(String.class);
                        Boolean banned = child.child("banned").getValue(Boolean.class);
                        manager.put("name",   name  != null ? name  : "مجهول");
                        manager.put("email",  email != null ? email : "");
                        manager.put("banned", Boolean.TRUE.equals(banned));

                        // جمع الصلاحيات
                        HashMap<String, Boolean> perms = new HashMap<>();
                        DataSnapshot permsSnap = child.child("permissions");
                        for (String key : PERM_KEYS) {
                            Boolean val = permsSnap.child(key).getValue(Boolean.class);
                            perms.put(key, Boolean.TRUE.equals(val));
                        }
                        manager.put("permissions", perms);
                        managers.add(manager);
                    }
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    if (tvCount != null) tvCount.setText("عدد المديرين: " + managers.size());
                    boolean empty = managers.isEmpty();
                    if (tvEmpty != null) tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                    if (recyclerView != null) recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
                    if (adapter != null) adapter.notifyDataSetChanged();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError e) {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    Toast.makeText(ManagersActivity.this,
                        "خطأ في التحميل: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
    }

    @SuppressWarnings("unchecked")
    private void onManagerAction(HashMap<String, Object> manager, String action) {
        String uid  = (String) manager.get("uid");
        String name = (String) manager.get("name");
        if (uid == null) return;

        switch (action) {
            case "ban": {
                Boolean banned = (Boolean) manager.get("banned");
                boolean newBanned = !Boolean.TRUE.equals(banned);
                usersRef.child(uid).child("banned").setValue(newBanned)
                    .addOnSuccessListener(v -> Toast.makeText(this,
                        newBanned ? "🚫 تم حظر " + name : "✅ تم رفع الحظر عن " + name,
                        Toast.LENGTH_SHORT).show());
                break;
            }
            case "demote": {
                new AlertDialog.Builder(this)
                    .setTitle("تخفيض المدير")
                    .setMessage("هل تريد تحويل " + name + " لعضو عادي؟")
                    .setPositiveButton("نعم", (d, w) ->
                        RoleManager.demoteToMember(uid,
                            () -> Toast.makeText(this, "✅ تم التخفيض", Toast.LENGTH_SHORT).show(),
                            err -> Toast.makeText(this, "❌ " + err, Toast.LENGTH_SHORT).show()))
                    .setNegativeButton("لا", null).show();
                break;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void onEditPermissions(HashMap<String, Object> manager) {
        String uid  = (String) manager.get("uid");
        String name = (String) manager.get("name");
        if (uid == null) return;

        HashMap<String, Boolean> current =
            (HashMap<String, Boolean>) manager.getOrDefault("permissions", new HashMap<>());
        boolean[] checked = new boolean[PERM_KEYS.length];
        for (int i = 0; i < PERM_KEYS.length; i++) {
            checked[i] = Boolean.TRUE.equals(current.get(PERM_KEYS[i]));
        }

        new AlertDialog.Builder(this)
            .setTitle("🔑 صلاحيات " + name)
            .setMultiChoiceItems(PERM_LABELS, checked,
                (dialog, which, isChecked) -> checked[which] = isChecked)
            .setPositiveButton("حفظ", (d, w) -> {
                Map<String, Object> perms = new HashMap<>();
                for (int i = 0; i < PERM_KEYS.length; i++) {
                    perms.put(PERM_KEYS[i], checked[i]);
                }
                RoleManager.promoteToManager(uid, perms,
                    () -> Toast.makeText(this, "✅ تم حفظ الصلاحيات", Toast.LENGTH_SHORT).show(),
                    err -> Toast.makeText(this, "❌ " + err, Toast.LENGTH_SHORT).show());
            })
            .setNegativeButton("إلغاء", null).show();
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }

    // ═══════════════════════════════════════════════════════
    // Inner Adapter
    // ═══════════════════════════════════════════════════════
    interface ManagerActionListener  { void onAction(HashMap<String, Object> m, String action); }
    interface ManagerPermsListener   { void onEdit(HashMap<String, Object> m); }

    static class ManagersAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<ManagersAdapter.VH> {
        private static final String[] PERM_SHORT = {"موافقة","حذف","أعضاء","حظر","عرض","تعديل","إشعار"};

        private final List<HashMap<String, Object>> items;
        private final ManagerActionListener actionListener;
        private final ManagerPermsListener  permsListener;

        ManagersAdapter(List<HashMap<String, Object>> items,
                        ManagerActionListener action, ManagerPermsListener perms) {
            this.items          = items;
            this.actionListener = action;
            this.permsListener  = perms;
        }

        @Override
        public VH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            android.view.View v = android.view.LayoutInflater.from(parent.getContext())
                .inflate(com.missingpersons.app.R.layout.item_manager, parent, false);
            return new VH(v);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void onBindViewHolder(VH h, int pos) {
            HashMap<String, Object> mgr = items.get(pos);
            android.content.Context ctx = h.itemView.getContext();

            Boolean banned = (Boolean) mgr.get("banned");
            String name    = s(mgr, "name", "مجهول");
            h.tvName.setText(Boolean.TRUE.equals(banned) ? name + " 🚫" : name);
            h.tvEmail.setText(s(mgr, "email", ""));

            String photoUrl = s(mgr, "photoUrl", "");
            if (!photoUrl.isEmpty()) {
                coil.Coil.imageLoader(ctx).enqueue(new coil.request.ImageRequest.Builder(ctx)
                    .data(photoUrl).target(h.ivAvatar)
                    .placeholder(com.missingpersons.app.R.drawable.ic_person)
                    .error(com.missingpersons.app.R.drawable.ic_person).build());
            } else {
                h.ivAvatar.setImageResource(com.missingpersons.app.R.drawable.ic_person);
            }

            // Chips صلاحيات
            h.chipGroup.removeAllViews();
            Object permsObj = mgr.get("permissions");
            HashMap<String, Boolean> perms =
                (permsObj instanceof HashMap) ? (HashMap<String, Boolean>) permsObj : new HashMap<>();
            boolean anyPerm = false;
            for (int i = 0; i < PERM_KEYS.length; i++) {
                if (Boolean.TRUE.equals(perms.get(PERM_KEYS[i]))) {
                    com.google.android.material.chip.Chip chip =
                        new com.google.android.material.chip.Chip(ctx);
                    chip.setText(PERM_SHORT[i]);
                    chip.setClickable(false);
                    chip.setCheckable(false);
                    h.chipGroup.addView(chip);
                    anyPerm = true;
                }
            }
            if (!anyPerm) {
                com.google.android.material.chip.Chip chip =
                    new com.google.android.material.chip.Chip(ctx);
                chip.setText("لا صلاحيات");
                chip.setClickable(false);
                h.chipGroup.addView(chip);
            }

            h.btnPerms.setOnClickListener(v  -> permsListener.onEdit(mgr));
            h.btnRemove.setOnClickListener(v -> actionListener.onAction(mgr, "demote"));
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            de.hdodenhof.circleimageview.CircleImageView ivAvatar;
            android.widget.TextView tvName, tvEmail, btnPerms, btnRemove;
            com.google.android.material.chip.ChipGroup chipGroup;
            VH(android.view.View v) {
                super(v);
                ivAvatar  = v.findViewById(com.missingpersons.app.R.id.iv_manager_avatar);
                tvName    = v.findViewById(com.missingpersons.app.R.id.tv_manager_name);
                tvEmail   = v.findViewById(com.missingpersons.app.R.id.tv_manager_email);
                chipGroup = v.findViewById(com.missingpersons.app.R.id.chip_group_permissions);
                btnPerms  = v.findViewById(com.missingpersons.app.R.id.btn_manager_perms);
                btnRemove = v.findViewById(com.missingpersons.app.R.id.btn_manager_remove);
            }
        }

        private static String s(HashMap<String, Object> m, String k, String def) {
            Object v = m.get(k);
            return (v instanceof String && !((String) v).isEmpty()) ? (String) v : def;
        }
    }
}
