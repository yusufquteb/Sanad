package com.missingpersons.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import androidx.annotation.NonNull;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.util.HashMap;
import java.util.Map;

/**
 * RoleManager — مدير الصلاحيات المركزي مع دعم كامل للـ Offline
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  استراتيجية الـ Offline:                                        │
 * │  1. أول تحميل ناجح → يُخزَّن الدور والصلاحيات في               │
 * │     SharedPreferences                                           │
 * │  2. Offline → يُستخدَم الـ cache فوراً بدون انتظار             │
 * │  3. عودة الإنترنت → يُحدَّث الـ cache تلقائياً عبر             │
 * │     مستمع مباشر (LiveListener)                                 │
 * │  4. التطبيق لا يُغلَق أبداً بسبب عدم الاتصال                  │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * الاستخدام:
 *   // في MyApplication.onCreate():
 *   RoleManager.init(this);
 *
 *   // في أي Activity:
 *   RoleManager.get().load(callback);
 *   RoleManager.get().isAdmin();
 *   RoleManager.get().hasPerm(RoleManager.PERM_APPROVE_REPORTS);
 */
public class RoleManager {

    // ── صلاحيات المديرين ─────────────────────────────────────────
    public static final String PERM_APPROVE_REPORTS    = "canApproveReports";
    public static final String PERM_DELETE_REPORTS     = "canDeleteReports";
    public static final String PERM_MANAGE_MEMBERS     = "canManageMembers";
    public static final String PERM_BAN_USERS          = "canBanUsers";
    public static final String PERM_VIEW_ALL_REPORTS   = "canViewAllReports";
    public static final String PERM_EDIT_REPORTS       = "canEditReports";
    public static final String PERM_SEND_NOTIFICATIONS = "canSendNotifications";

    public static final String ROLE_ADMIN   = "admin";
    public static final String ROLE_MANAGER = "manager";
    public static final String ROLE_MEMBER  = "member";

    private static final String PREFS_NAME      = "sanad_role_cache";
    private static final String KEY_ROLE_PREFIX = "role_";
    private static final String KEY_PERM_PREFIX = "perm_";
    private static final String KEY_APPROVED    = "approved_";
    private static final String KEY_CACHE_UID   = "cache_uid";
    private static final String KEY_PERM_KEYS   = "_keys";

    // ── Singleton ────────────────────────────────────────────────
    private static volatile RoleManager sInstance;
    private static Context sAppContext;

    private String  role      = ROLE_MEMBER;
    private boolean approved  = false;
    private final Map<String, Boolean> permissions = new HashMap<>();

    private boolean loaded    = false;
    private boolean fromCache = false;

    private ValueEventListener liveListener;
    private DatabaseReference  liveRef;

    private RoleManager() {}

    /** استدعه في MyApplication.onCreate() مرة واحدة */
    public static void init(Context ctx) {
        sAppContext = ctx.getApplicationContext();
    }

    public static RoleManager get() {
        if (sInstance == null) {
            synchronized (RoleManager.class) {
                if (sInstance == null) sInstance = new RoleManager();
            }
        }
        return sInstance;
    }

    /** أعد التهيئة عند تسجيل الخروج */
    public static void reset() {
        synchronized (RoleManager.class) {
            if (sInstance != null) sInstance.stopLiveListener();
            sInstance = null;
        }
    }

    // ════════════════════════════════════════════════════════════
    //  واجهة الـ Callback
    // ════════════════════════════════════════════════════════════

    public interface LoadCallback {
        /** يُستدعى دائماً — حتى لو offline — بالدور المخزَّن أو الافتراضي */
        void onLoaded(boolean isAdminOrManager);
        /** خطأ حرج فقط (مستخدم غير مسجل) */
        void onError(String message);
    }

    // ════════════════════════════════════════════════════════════
    //  load() — نقطة الدخول الوحيدة
    // ════════════════════════════════════════════════════════════

    /**
     * يحمّل الدور بأسرع طريقة ممكنة:
     *   RAM cache → SharedPreferences cache → Firebase → fallback member
     *
     * الـ callback يُنادى دائماً في كل الأحوال.
     * التطبيق لا يُوقَف مطلقاً.
     */
    public void load(@NonNull LoadCallback callback) {
        String uid = getCurrentUid();
        if (uid == null) {
            callback.onError("لم يتم تسجيل الدخول");
            return;
        }

        // ① RAM cache جاهز
        if (loaded) {
            callback.onLoaded(isAdminOrManager());
            silentRefresh(uid);
            return;
        }

        // ② SharedPreferences cache
        if (loadFromPrefs(uid)) {
            loaded    = true;
            fromCache = true;
            callback.onLoaded(isAdminOrManager()); // ← فوري 🚀
            silentRefresh(uid);                     // ← تحديث خلفي
            return;
        }

        // ③ لا cache → Firebase (مع timeout fallback)
        fetchFromFirebase(uid, callback);
    }

    // ════════════════════════════════════════════════════════════
    //  Firebase fetch
    // ════════════════════════════════════════════════════════════

    private void fetchFromFirebase(String uid, @NonNull LoadCallback callback) {
        final boolean[] answered = {false};

        // Timeout: إذا لم يرد Firebase خلال 5 ثوانٍ → fallback
        android.os.Handler handler = new android.os.Handler(
            android.os.Looper.getMainLooper());
        handler.postDelayed(() -> {
            if (!answered[0]) {
                answered[0] = true;
                role = ROLE_MEMBER;
                loaded = true;
                fromCache = false;
                callback.onLoaded(false); // ← لا نُغلِق التطبيق
            }
        }, 5000);

        FirebaseDatabase.getInstance()
            .getReference("users").child(uid)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snap) {
                    handler.removeCallbacksAndMessages(null);
                    if (answered[0]) return;
                    answered[0] = true;

                    parseSnapshot(snap);
                    loaded    = true;
                    fromCache = false;
                    saveToPrefs(uid);
                    callback.onLoaded(isAdminOrManager());
                    startLiveListener(uid);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError e) {
                    handler.removeCallbacksAndMessages(null);
                    if (answered[0]) return;
                    answered[0] = true;

                    // Firebase مرفوض (قواعد أو offline)
                    // نكمل كعضو عادي بدون إغلاق
                    role      = ROLE_MEMBER;
                    loaded    = true;
                    fromCache = false;
                    callback.onLoaded(false);
                }
            });
    }

    /** تحديث صامت في الخلفية — لا يُظهِر loading */
    private void silentRefresh(String uid) {
        if (!isOnline()) return;
        FirebaseDatabase.getInstance()
            .getReference("users").child(uid)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snap) {
                    parseSnapshot(snap);
                    fromCache = false;
                    saveToPrefs(uid);
                    startLiveListener(uid);
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
    }

    /**
     * مستمع مباشر: يُحدِّث الـ cache تلقائياً عند تغيير أي صلاحية
     * من Firebase Console حتى لو كان التطبيق مفتوحاً.
     */
    private void startLiveListener(String uid) {
        stopLiveListener();
        liveRef = FirebaseDatabase.getInstance()
            .getReference("users").child(uid);
        liveListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                parseSnapshot(snap);
                saveToPrefs(uid);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        liveRef.addValueEventListener(liveListener);
    }

    private void stopLiveListener() {
        if (liveRef != null && liveListener != null)
            liveRef.removeEventListener(liveListener);
        liveRef = null; liveListener = null;
    }

    // ════════════════════════════════════════════════════════════
    //  تحليل DataSnapshot
    // ════════════════════════════════════════════════════════════

    private void parseSnapshot(DataSnapshot snap) {
        String r = snap.child("role").getValue(String.class);
        role     = r != null ? r : ROLE_MEMBER;

        Boolean app = snap.child("approved").getValue(Boolean.class);
        approved = Boolean.TRUE.equals(app);

        permissions.clear();
        DataSnapshot ps = snap.child("permissions");
        if (ps.exists()) {
            for (DataSnapshot p : ps.getChildren()) {
                permissions.put(p.getKey(),
                    Boolean.TRUE.equals(p.getValue(Boolean.class)));
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    //  SharedPreferences
    // ════════════════════════════════════════════════════════════

    private SharedPreferences prefs() {
        if (sAppContext == null) return null;
        return sAppContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private void saveToPrefs(String uid) {
        SharedPreferences p = prefs();
        if (p == null) return;
        SharedPreferences.Editor ed = p.edit();
        ed.putString(KEY_CACHE_UID, uid);
        ed.putString(KEY_ROLE_PREFIX + uid, role);
        ed.putBoolean(KEY_APPROVED + uid, approved);
        StringBuilder keys = new StringBuilder();
        for (Map.Entry<String, Boolean> e : permissions.entrySet()) {
            ed.putBoolean(KEY_PERM_PREFIX + uid + "_" + e.getKey(), e.getValue());
            if (keys.length() > 0) keys.append(",");
            keys.append(e.getKey());
        }
        ed.putString(KEY_PERM_PREFIX + uid + KEY_PERM_KEYS, keys.toString());
        ed.apply();
    }

    private boolean loadFromPrefs(String uid) {
        SharedPreferences p = prefs();
        if (p == null) return false;
        String cachedUid = p.getString(KEY_CACHE_UID, "");
        if (!uid.equals(cachedUid)) return false;
        String r = p.getString(KEY_ROLE_PREFIX + uid, null);
        if (r == null) return false;

        role     = r;
        approved = p.getBoolean(KEY_APPROVED + uid, false);
        permissions.clear();
        String keysStr = p.getString(KEY_PERM_PREFIX + uid + KEY_PERM_KEYS, "");
        if (!keysStr.isEmpty()) {
            for (String k : keysStr.split(",")) {
                if (!k.isEmpty())
                    permissions.put(k, p.getBoolean(
                        KEY_PERM_PREFIX + uid + "_" + k, false));
            }
        }
        return true;
    }

    /** امسح الـ cache عند تسجيل الخروج */
    public static void clearCache() {
        if (sAppContext == null) return;
        sAppContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply();
    }

    // ════════════════════════════════════════════════════════════
    //  Getters
    // ════════════════════════════════════════════════════════════

    public boolean isAdmin()          { return ROLE_ADMIN.equals(role); }
    public boolean isManager()        { return ROLE_MANAGER.equals(role); }
    public boolean isAdminOrManager() { return isAdmin() || isManager(); }
    public boolean isMember()         { return ROLE_MEMBER.equals(role); }
    public boolean isApproved()       { return approved || isAdminOrManager(); }
    public String  getRole()          { return role; }

    /** هل الدور مأخوذ من الـ cache (offline)؟ */
    public boolean isFromCache()      { return fromCache; }

    /**
     * التحقق من صلاحية.
     * الأدمن لديه كل الصلاحيات تلقائياً.
     */
    public boolean hasPerm(String permKey) {
        if (isAdmin())    return true;
        if (!isManager()) return false;
        return Boolean.TRUE.equals(permissions.get(permKey));
    }

    public boolean canApproveReports()    { return hasPerm(PERM_APPROVE_REPORTS); }
    public boolean canDeleteReports()     { return hasPerm(PERM_DELETE_REPORTS); }
    public boolean canManageMembers()     { return hasPerm(PERM_MANAGE_MEMBERS); }
    public boolean canBanUsers()          { return hasPerm(PERM_BAN_USERS); }
    public boolean canViewAllReports()    { return hasPerm(PERM_VIEW_ALL_REPORTS); }
    public boolean canEditReports()       { return hasPerm(PERM_EDIT_REPORTS); }
    public boolean canSendNotifications() { return hasPerm(PERM_SEND_NOTIFICATIONS); }

    // ════════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════════

    private boolean isOnline() {
        if (sAppContext == null) return true;
        ConnectivityManager cm = (ConnectivityManager)
            sAppContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    private String getCurrentUid() {
        var user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    // ════════════════════════════════════════════════════════════
    //  عمليات Firebase (ترقية / تخفيض / موافقة)
    // ════════════════════════════════════════════════════════════

    public static void promoteToManager(
            String targetUid, Map<String, Object> perms,
            Runnable onSuccess, java.util.function.Consumer<String> onError) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("role", ROLE_MANAGER);
        updates.put("promotedAt", System.currentTimeMillis());
        for (Map.Entry<String, Object> e : perms.entrySet())
            updates.put("permissions/" + e.getKey(), e.getValue());
        FirebaseDatabase.getInstance().getReference("users").child(targetUid)
            .updateChildren(updates)
            .addOnSuccessListener(v -> onSuccess.run())
            .addOnFailureListener(ex -> onError.accept(ex.getMessage()));
    }

    public static void demoteToMember(
            String targetUid, Runnable onSuccess,
            java.util.function.Consumer<String> onError) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("role", ROLE_MEMBER);
        updates.put("permissions", null);
        FirebaseDatabase.getInstance().getReference("users").child(targetUid)
            .updateChildren(updates)
            .addOnSuccessListener(v -> onSuccess.run())
            .addOnFailureListener(ex -> onError.accept(ex.getMessage()));
    }

    public static void approveMember(
            String targetUid, boolean isApproved,
            Runnable onSuccess, java.util.function.Consumer<String> onError) {
        FirebaseDatabase.getInstance()
            .getReference("users").child(targetUid).child("approved")
            .setValue(isApproved)
            .addOnSuccessListener(v -> onSuccess.run())
            .addOnFailureListener(ex -> onError.accept(ex.getMessage()));
    }
}
