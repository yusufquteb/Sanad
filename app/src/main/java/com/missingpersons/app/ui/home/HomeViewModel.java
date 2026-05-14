package com.missingpersons.app.ui.home;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.database.*;
import com.missingpersons.app.data.repository.ReportRepository;
import com.missingpersons.app.data.repository.UserRepository;
import com.missingpersons.app.models.ReportEntity;

import java.util.*;

/**
 * HomeViewModel — المصدر الوحيد للحقيقة في NewHomeActivity
 *
 * [مرحلة 1.2] نقل كل منطق Firebase من NewHomeActivity إلى هنا:
 *   ✅ إحصائيات البلاغات (active / found / today)
 *   ✅ badges الإشعارات والمحادثات (real-time)
 *   ✅ أعلى مستخدم في Leaderboard
 *   ✅ أحدث البلاغات (3 nodes: reports / found_persons / sightings)
 *   ✅ Amber Alerts للمحافظة الحالية
 *   ✅ بيانات المستخدم (اسم + صورة + نقاط)
 *
 * NewHomeActivity تُنفّذ فقط:
 *   observe LiveData → تحديث UI
 */
public class HomeViewModel extends ViewModel {

    private static final String TAG = "HomeViewModel";

    private final ReportRepository  reportRepository;
    private final UserRepository    userRepository;

    // ── LiveData ────────────────────────────────────────────
    private final MutableLiveData<Boolean>            isLoading        = new MutableLiveData<>(false);
    private final MutableLiveData<String>             errorMessage     = new MutableLiveData<>();
    private final MutableLiveData<Integer>            unreadNotif      = new MutableLiveData<>(0);
    private final MutableLiveData<Integer>            unreadChats      = new MutableLiveData<>(0);
    private final MutableLiveData<String>             pointsText       = new MutableLiveData<>("");
    private final MutableLiveData<HomeStats>          stats            = new MutableLiveData<>();
    private final MutableLiveData<String>             leaderboardTop   = new MutableLiveData<>("");
    private final MutableLiveData<List<Map<String,Object>>> recentReports = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<AmberAlert>>   amberAlerts      = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<UserInfo>           userInfo         = new MutableLiveData<>();

    // ── Firebase listeners (تُنظَّف في onCleared) ──────────
    private DatabaseReference notifRef, chatRef, statsRef, leaderRef;
    private ValueEventListener notifListener, chatListener, statsListener, leaderListener;
    private final List<Query>                recentRefs      = new ArrayList<>();
    private final List<ValueEventListener>   recentListeners = new ArrayList<>();

    // ── flag لتجنب التحميل المزدوج ──────────────────────────
    private boolean recentLoaded = false;

    public HomeViewModel(ReportRepository reportRepository, UserRepository userRepository) {
        this.reportRepository  = reportRepository;
        this.userRepository    = userRepository;
    }

    // ════════════════════════════════════════════════════════
    //  Expose LiveData
    // ════════════════════════════════════════════════════════

    public LiveData<Boolean>                    isLoading()      { return isLoading;      }
    public LiveData<String>                     errorMessage()   { return errorMessage;   }
    public LiveData<Integer>                    unreadNotif()    { return unreadNotif;    }
    public LiveData<Integer>                    unreadChats()    { return unreadChats;    }
    public LiveData<String>                     pointsText()     { return pointsText;     }
    public LiveData<HomeStats>                  stats()          { return stats;          }
    public LiveData<String>                     leaderboardTop() { return leaderboardTop; }
    public LiveData<List<Map<String,Object>>>   recentReports()  { return recentReports;  }
    public LiveData<List<AmberAlert>>           amberAlerts()    { return amberAlerts;    }
    public LiveData<UserInfo>                   userInfo()       { return userInfo;       }

    // ── قديم — للتوافق مع BrowseActivity ──────────────────
    public LiveData<Integer> unreadCount() { return unreadNotif; }

    public LiveData<List<ReportEntity>> getApprovedReports() {
        return reportRepository.getApprovedReports();
    }

    public LiveData<List<ReportEntity>> getFilteredReports(
            String type, String gov, String q, String status) {
        return reportRepository.getFilteredReports(type, gov, q, status);
    }

    // ════════════════════════════════════════════════════════
    //  Init — يُستدعى مرة واحدة من onCreate
    // ════════════════════════════════════════════════════════

    public void init(String uid, String governorate) {
        loadUserInfo(uid);
        startBadgeListeners(uid);
        loadStats();
        loadLeaderboardTop();
        loadRecentReports();
        if (governorate != null && !governorate.isEmpty())
            loadAmberAlerts(governorate);
    }

    // ════════════════════════════════════════════════════════
    //  1. بيانات المستخدم
    // ════════════════════════════════════════════════════════

    public void loadUserInfo(String uid) {
        if (uid == null || uid.isEmpty()) return;
        userRepository.getUserData(uid, data -> {
            UserInfo info  = new UserInfo();
            info.uid       = uid;
            info.name      = data.name;
            info.photoUrl  = data.photoUrl;
            info.points    = data.points;
            info.rankLabel = getRankLabel(data.points);
            userInfo.setValue(info);
            pointsText.setValue(info.rankLabel + "  •  " + data.points + " نقطة");
        }, err -> Log.w(TAG, "loadUserInfo: " + err));
    }

    // ════════════════════════════════════════════════════════
    //  2. Badges — real-time listeners
    // ════════════════════════════════════════════════════════

    public void startBadgeListeners(String uid) {
        if (uid == null || uid.isEmpty()) return;
        stopBadgeListeners();

        DatabaseReference db = FirebaseDatabase.getInstance().getReference();

        // ── إشعارات غير مقروءة ──────────────────────────────
        notifRef = db.child("notifications").child(uid);
        notifListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                int c = 0;
                for (DataSnapshot n : snap.getChildren()) {
                    Boolean read = n.child("read").getValue(Boolean.class);
                    if (!Boolean.TRUE.equals(read)) c++;
                }
                unreadNotif.setValue(c);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                unreadNotif.setValue(0);
            }
        };
        notifRef.addValueEventListener(notifListener);

        // ── محادثات غير مقروءة ──────────────────────────────
        chatRef = db.child("chats");
        chatListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                int unread = 0;
                for (DataSnapshot chat : snap.getChildren()) {
                    Object p1 = chat.child("user1").getValue();
                    Object p2 = chat.child("user2").getValue();
                    if (uid.equals(String.valueOf(p1)) || uid.equals(String.valueOf(p2))) {
                        Object cnt = chat.child("unread_" + uid).getValue();
                        if (cnt instanceof Long && (Long) cnt > 0) unread++;
                    }
                }
                unreadChats.setValue(unread);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                unreadChats.setValue(0);
            }
        };
        chatRef.addValueEventListener(chatListener);
    }

    public void stopBadgeListeners() {
        if (notifRef != null && notifListener != null) {
            notifRef.removeEventListener(notifListener);
            notifRef = null; notifListener = null;
        }
        if (chatRef != null && chatListener != null) {
            chatRef.removeEventListener(chatListener);
            chatRef = null; chatListener = null;
        }
    }

    // ════════════════════════════════════════════════════════
    //  3. الإحصائيات
    // ════════════════════════════════════════════════════════

    public void loadStats() {
        DatabaseReference db = FirebaseDatabase.getInstance().getReference();

        // أولاً: من cache سريع
        db.child("stats").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                Long active   = snap.child("approved").getValue(Long.class);
                Long resolved = snap.child("resolved").getValue(Long.class);
                if (active != null && active > 0) {
                    HomeStats s = new HomeStats();
                    s.active   = active.intValue();
                    s.resolved = resolved != null ? resolved.intValue() : 0;
                    stats.setValue(s);
                }
                // دائماً أعد الحساب من الـ nodes الفعلية
                loadStatsFromNodes();
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                loadStatsFromNodes();
            }
        });
    }

    private void loadStatsFromNodes() {
        DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        final int[] done = {0};
        final int[] active = {0}, resolved = {0}, found = {0};

        long todayStart = getTodayStart();

        ValueEventListener reportsL = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                int a = 0, r = 0, t = 0;
                for (DataSnapshot c : snap.getChildren()) {
                    String s = c.child("status").getValue(String.class);
                    if ("approved".equals(s)) a++;
                    if ("resolved".equals(s) || "matched".equals(s)) r++;
                    Long ts = c.child("timestamp").getValue(Long.class);
                    if (ts != null && ts >= todayStart) t++;
                }
                active[0] = a; resolved[0] = r;
                if (++done[0] == 2) mergeStats(active[0], resolved[0], found[0]);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                if (++done[0] == 2) mergeStats(active[0], resolved[0], found[0]);
            }
        };
        ValueEventListener foundL = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                found[0] = (int) snap.getChildrenCount();
                if (++done[0] == 2) mergeStats(active[0], resolved[0], found[0]);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                if (++done[0] == 2) mergeStats(active[0], resolved[0], found[0]);
            }
        };

        db.child("reports").addListenerForSingleValueEvent(reportsL);
        db.child("found_persons").addListenerForSingleValueEvent(foundL);
    }

    private void mergeStats(int active, int resolved, int found) {
        HomeStats s = new HomeStats();
        s.active   = active;
        s.resolved = resolved;
        s.found    = found;
        stats.setValue(s);
        // حدّث الـ cache
        DatabaseReference db = FirebaseDatabase.getInstance().getReference("stats");
        db.child("approved").setValue(active);
        db.child("resolved").setValue(resolved);
        db.child("lastUpdated").setValue(System.currentTimeMillis());
        Log.d(TAG, "stats: active=" + active + " resolved=" + resolved + " found=" + found);
    }

    // ════════════════════════════════════════════════════════
    //  4. Leaderboard Top
    // ════════════════════════════════════════════════════════

    public void loadLeaderboardTop() {
        DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        db.child("users").orderByChild("points").limitToLast(1)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    for (DataSnapshot u : snap.getChildren()) {
                        String name = u.child("name").getValue(String.class);
                        if (name == null || name.isEmpty())
                            name = u.child("displayName").getValue(String.class);
                        if (name == null || name.isEmpty()) continue;
                        String first = name.split(" ")[0];
                        if (first.length() > 10) first = first.substring(0, 10);
                        leaderboardTop.setValue("#1 " + first);
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
    }

    // ════════════════════════════════════════════════════════
    //  5. أحدث البلاغات (3 nodes)
    // ════════════════════════════════════════════════════════

    public void loadRecentReports() {
        if (recentLoaded) return;
        recentLoaded = true;

        String[] nodes = {"reports", "found_persons", "sightings"};
        Map<String, Map<String, Object>> combined =
            new java.util.concurrent.ConcurrentHashMap<>();

        DatabaseReference db = FirebaseDatabase.getInstance().getReference();

        for (String node : nodes) {
            Query ref = db.child(node).orderByChild("timestamp").limitToLast(10);
            ValueEventListener listener = new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    // احذف البيانات القديمة لهذا الـ node
                    for (String key : new ArrayList<>(combined.keySet())) {
                        Map<String, Object> m = combined.get(key);
                        if (m != null && node.equals(m.get("_node"))) combined.remove(key);
                    }
                    for (DataSnapshot c : snap.getChildren()) {
                        String status = c.child("status").getValue(String.class);
                        if (!"approved".equals(status)) continue;
                        Map<String, Object> m = new HashMap<>();
                        for (DataSnapshot f : c.getChildren()) m.put(f.getKey(), f.getValue());
                        m.put("reportId", c.getKey());
                        m.put("_node", node);
                        combined.put(c.getKey() != null ? c.getKey() : UUID.randomUUID().toString(), m);
                    }
                    publishRecent(combined);
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {
                    Log.w(TAG, "recent " + node + " cancelled: " + e.getMessage());
                }
            };
            recentRefs.add(ref);
            ref.addValueEventListener(listener);
            recentListeners.add(listener);
        }
    }

    private void publishRecent(Map<String, Map<String, Object>> combined) {
        List<Map<String, Object>> list = new ArrayList<>(combined.values());
        list.sort((a, b) -> {
            Object ta = a.get("timestamp"), tb = b.get("timestamp");
            long la = ta instanceof Long ? (Long) ta : 0L;
            long lb = tb instanceof Long ? (Long) tb : 0L;
            return Long.compare(lb, la);
        });
        recentReports.postValue(list.size() > 8 ? list.subList(0, 8) : list);
    }

    public void stopRecentListeners() {
        for (int i = 0; i < recentRefs.size() && i < recentListeners.size(); i++) {
            recentRefs.get(i).removeEventListener(recentListeners.get(i));
        }
        recentRefs.clear();
        recentListeners.clear();
        recentLoaded = false;
    }

    // ════════════════════════════════════════════════════════
    //  6. Amber Alerts
    // ════════════════════════════════════════════════════════

    public void loadAmberAlerts(String governorate) {
        if (governorate == null || governorate.isEmpty()) return;
        DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        long cutoff = System.currentTimeMillis() - (48L * 60 * 60 * 1000); // 48 ساعة

        db.child("amber_alerts")
          .orderByChild("timestamp").startAt(cutoff)
          .addListenerForSingleValueEvent(new ValueEventListener() {
              @Override public void onDataChange(@NonNull DataSnapshot snap) {
                  List<AmberAlert> alerts = new ArrayList<>();
                  for (DataSnapshot c : snap.getChildren()) {
                      String gov = c.child("governorate").getValue(String.class);
                      if (!governorate.equals(gov)) continue;
                      Boolean active = c.child("active").getValue(Boolean.class);
                      if (Boolean.FALSE.equals(active)) continue;
                      AmberAlert a = new AmberAlert();
                      a.id         = c.getKey();
                      a.name       = safeStr(c, "personName");
                      a.age        = safeStr(c, "age");
                      a.photoUrl   = safeStr(c, "photoUrl");
                      a.reportId   = safeStr(c, "reportId");
                      a.governorate = gov;
                      alerts.add(a);
                  }
                  amberAlerts.setValue(alerts);
                  Log.d(TAG, "amber alerts: " + alerts.size() + " for " + governorate);
              }
              @Override public void onCancelled(@NonNull DatabaseError e) {}
          });
    }

    // ════════════════════════════════════════════════════════
    //  sync للـ Browse
    // ════════════════════════════════════════════════════════

    public void syncReports(String type, String gov, String status) {
        isLoading.setValue(true);
        reportRepository.syncInitial(type, gov, status, () -> {
            isLoading.setValue(false);
            loadStats();
        });
    }

    public void loadMore(String type, String gov, String status, long cursor,
                          ReportRepository.OnLoadMoreCallback callback) {
        reportRepository.loadMore(type, gov, status, cursor, callback);
    }

    /** للتوافق القديم */
    public void startNotificationListener(String uid) { startBadgeListeners(uid); }
    public void stopNotificationListener()             { stopBadgeListeners(); }
    public void loadPoints(String uid)                 { loadUserInfo(uid); }

    // ════════════════════════════════════════════════════════
    //  Data Models
    // ════════════════════════════════════════════════════════

    public static class HomeStats {
        public int active   = 0;
        public int resolved = 0;
        public int found    = 0;
        // للتوافق
        public int total   = 0;
        public int missing = 0;
    }

    public static class UserInfo {
        public String uid       = "";
        public String name      = "";
        public String photoUrl  = "";
        public int    points    = 0;
        public String rankLabel = "🟢 عضو";
    }

    public static class AmberAlert {
        public String id          = "";
        public String name        = "";
        public String age         = "";
        public String photoUrl    = "";
        public String reportId    = "";
        public String governorate = "";
    }

    // ════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════

    private String getRankLabel(int points) {
        if (points >= 5000) return "🏆 بطل";
        if (points >= 2000) return "⭐ متميز";
        if (points >= 500)  return "🔵 نشط";
        return "🟢 عضو";
    }

    private long getTodayStart() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private String safeStr(DataSnapshot s, String key) {
        Object v = s.child(key).getValue();
        return v != null ? v.toString() : "";
    }

    // ════════════════════════════════════════════════════════
    //  Cleanup
    // ════════════════════════════════════════════════════════

    @Override
    protected void onCleared() {
        super.onCleared();
        stopBadgeListeners();
        stopRecentListeners();
    }
}
