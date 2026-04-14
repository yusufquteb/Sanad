package com.missingpersons.app.ui.profile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.missingpersons.app.data.repository.UserRepository;

/**
 * ProfileViewModel — يدير منطق شاشة الملف الشخصي
 *
 * ══════════════════════════════════════════════════════
 * [إصلاح 2.5] إحصائيات إضافية:
 *   • totalReports  → عدد البلاغات الكلي
 *   • matchedReports → عدد التطابقات المؤكدة
 *   • resolvedReports → عدد الحالات المغلقة
 *   • rankLabel → رتبة المستخدم بالنقاط
 * ══════════════════════════════════════════════════════
 */
public class ProfileViewModel extends ViewModel {

    private final UserRepository userRepository;

    // ── State LiveData ──────────────────────────────────────
    private final MutableLiveData<ProfileData> profileData = new MutableLiveData<>();
    private final MutableLiveData<Boolean>     isLoading   = new MutableLiveData<>(false);
    private final MutableLiveData<String>      errorMsg    = new MutableLiveData<>();

    public ProfileViewModel(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // ════════════════════════════════════════════════════════
    //  Expose LiveData
    // ════════════════════════════════════════════════════════

    public LiveData<ProfileData> getProfileData() { return profileData; }
    public LiveData<Boolean>     isLoading()      { return isLoading;   }
    public LiveData<String>      getErrorMsg()    { return errorMsg;    }

    // ════════════════════════════════════════════════════════
    //  Load Data
    // ════════════════════════════════════════════════════════

    /** تحميل كل بيانات الملف الشخصي دفعة واحدة */
    public void loadProfile(String uid) {
        if (uid == null || uid.isEmpty()) {
            errorMsg.setValue("معرّف المستخدم غير صالح");
            return;
        }
        isLoading.setValue(true);

        userRepository.getUserData(uid,
            data -> {
                ProfileData profile = new ProfileData();
                profile.uid        = uid;
                profile.name       = data.name;
                profile.email      = data.email;
                profile.photoUrl   = data.photoUrl;
                profile.role       = mapRoleToArabic(data.role);
                profile.rawRole    = data.role;
                profile.points     = data.points;
                profile.joinDate   = data.joinDate;
                profile.rankLabel  = getRankLabel(data.points);

                // تحميل إحصائيات البلاغات
                userRepository.getReportsCount(uid, count -> {
                    profile.totalReports = count;

                    // [إصلاح 2.5] تحميل التطابقات والمغلقة
                    userRepository.getMatchedReportsCount(uid, matchedCount -> {
                        profile.matchedReports = matchedCount;

                        userRepository.getResolvedReportsCount(uid, resolvedCount -> {
                            profile.resolvedReports = resolvedCount;
                            profileData.setValue(profile);
                            isLoading.setValue(false);
                        });
                    });
                });
            },
            error -> {
                errorMsg.setValue(error);
                isLoading.setValue(false);
            }
        );
    }

    // ════════════════════════════════════════════════════════
    //  Profile Data Model
    // ════════════════════════════════════════════════════════

    public static class ProfileData {
        public String  uid             = "";
        public String  name            = "";
        public String  email           = "";
        public String  photoUrl        = "";
        public String  role            = "عضو";
        public String  rawRole         = "member";
        public int     points          = 0;
        public int     totalReports    = 0;
        // [إصلاح 2.5]
        public int     matchedReports  = 0;
        public int     resolvedReports = 0;
        public long    joinDate        = 0;
        public String  rankLabel       = "🟢 عضو";

        public boolean isAdmin()   { return "admin".equals(rawRole);   }
        public boolean isManager() { return "manager".equals(rawRole); }
        public boolean isMember()  { return "member".equals(rawRole);  }
    }

    // ════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════

    private String mapRoleToArabic(String role) {
        if (role == null) return "عضو";
        switch (role) {
            case "admin":   return "مدير النظام";
            case "manager": return "مدير";
            default:        return "عضو";
        }
    }

    private String getRankLabel(int points) {
        if (points >= 5000) return "🏆 بطل";
        if (points >= 2000) return "⭐ متميز";
        if (points >= 500)  return "🔵 نشط";
        return "🟢 عضو";
    }
}
