package com.missingpersons.app.ui.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.missingpersons.app.data.repository.AuthRepository;

/**
 * AuthViewModel — يدير منطق تسجيل الدخول
 *
 * ينقل من LoginActivity:
 * - التحقق من الحالة الأولية (هل مسجل مسبقاً؟)
 * - تسجيل الدخول بـ Google (Firebase Auth جزء)
 * - الدخول كزائر
 * - التعامل مع البان
 *
 * ملاحظة:
 * - الـ GoogleSignInClient يبقى في الـ Activity لأنه يحتاج Context
 * - الـ ViewModel يتسلم idToken من Activity ويكمل الباقي
 */
public class AuthViewModel extends ViewModel {

    public enum AuthState {
        IDLE,       // الحالة الابتدائية
        LOADING,    // جاري التحميل
        SUCCESS,    // نجح تسجيل الدخول
        BANNED,     // الحساب محظور
        ERROR       // فشل
    }

    private final AuthRepository authRepository;

    // ── State LiveData ──────────────────────────────────────
    private final MutableLiveData<AuthState> authState   = new MutableLiveData<>(AuthState.IDLE);
    private final MutableLiveData<String>    errorMsg    = new MutableLiveData<>();
    private final MutableLiveData<String>    currentUid  = new MutableLiveData<>();
    private final MutableLiveData<Boolean>   isGuest     = new MutableLiveData<>(false);

    public AuthViewModel(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    // ════════════════════════════════════════════════════════
    //  Expose LiveData
    // ════════════════════════════════════════════════════════

    public LiveData<AuthState> getAuthState()  { return authState;  }
    public LiveData<String>    getErrorMsg()   { return errorMsg;   }
    public LiveData<String>    getCurrentUid() { return currentUid; }
    public LiveData<Boolean>   getIsGuest()    { return isGuest;    }

    // ════════════════════════════════════════════════════════
    //  Check initial state (الحالة الأولية عند فتح الشاشة)
    // ════════════════════════════════════════════════════════

    public void checkInitialState() {
        if (authRepository.isLoggedIn()) {
            currentUid.setValue(authRepository.getCurrentUid());
            authState.setValue(AuthState.SUCCESS);
        } else if (authRepository.isGuest()) {
            isGuest.setValue(true);
            authState.setValue(AuthState.SUCCESS);
        }
    }

    // ════════════════════════════════════════════════════════
    //  Sign In — Google
    //  Activity يمرر idToken بعد Google Sign-In
    // ════════════════════════════════════════════════════════

    public void signInWithGoogle(String idToken) {
        authState.setValue(AuthState.LOADING);
        authRepository.signInWithGoogle(idToken,
            uid -> {
                // التحقق من البان بعد تسجيل الدخول
                authRepository.checkBanned(uid, isBanned -> {
                    if (isBanned) {
                        authRepository.signOut();
                        authState.setValue(AuthState.BANNED);
                    } else {
                        currentUid.setValue(uid);
                        isGuest.setValue(false);
                        authState.setValue(AuthState.SUCCESS);
                    }
                });
            },
            error -> {
                errorMsg.setValue(error);
                authState.setValue(AuthState.ERROR);
            }
        );
    }

    // ════════════════════════════════════════════════════════
    //  Sign In — Guest
    // ════════════════════════════════════════════════════════

    public void signInAsGuest() {
        authState.setValue(AuthState.LOADING);
        authRepository.signInAnonymously(
            uid -> {
                currentUid.setValue(uid);
                isGuest.setValue(true);
                authState.setValue(AuthState.SUCCESS);
            },
            error -> {
                errorMsg.setValue(error);
                authState.setValue(AuthState.ERROR);
            }
        );
    }

    // ════════════════════════════════════════════════════════
    //  Sign Out
    // ════════════════════════════════════════════════════════

    public void signOut() {
        authRepository.signOut();
        authState.setValue(AuthState.IDLE);
        currentUid.setValue(null);
        isGuest.setValue(false);
    }

    // ════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════

    public boolean isAlreadyLoggedIn() {
        return authRepository.isLoggedIn();
    }

    /** رسائل خطأ Google Sign-In codes → عربي */
    public String resolveGoogleError(int statusCode) {
        switch (statusCode) {
            case 10:    return "❌ خطأ — أضف SHA-1 في Firebase";
            case 12501: return "تم الإلغاء";
            default:    return "❌ خطأ في تسجيل الدخول: كود " + statusCode;
        }
    }
}
