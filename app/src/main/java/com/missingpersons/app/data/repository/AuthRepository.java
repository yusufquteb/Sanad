package com.missingpersons.app.data.repository;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.*;
import com.google.firebase.database.*;
import com.google.firebase.messaging.FirebaseMessaging;

/**
 * AuthRepository — عمليات Firebase Auth
 *
 * [FIX-3] إضافة معالجة خاصة لـ FirebaseNetworkException
 * [FIX-3] حماية من null في رسائل الخطأ
 */
public class AuthRepository {

    private static final String TAG = "AuthRepository";
    private final FirebaseAuth mAuth;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AuthRepository() {
        this.mAuth = FirebaseAuth.getInstance();
    }

    // ════════════════════════════════════════════════════════
    //  Sign In — Google
    // ════════════════════════════════════════════════════════

    public void signInWithGoogle(String idToken, OnSuccessCallback onSuccess,
                                  OnErrorCallback onError) {
        // [FIX-3] idToken مضمون غير null هنا (تم التحقق في LoginActivity)
        // لكن نضيف حماية إضافية دفاعياً
        if (idToken == null || idToken.isEmpty()) {
            mainHandler.post(() -> onError.onError("رمز Google غير صالح — أعد المحاولة"));
            return;
        }

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user != null) {
                        saveUserProfile(user, () -> {
                            mainHandler.post(() -> onSuccess.onSuccess(user.getUid()));
                        });
                    } else {
                        mainHandler.post(() -> onError.onError("فشل تسجيل الدخول — لم تُسترجع بيانات المستخدم"));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "signInWithGoogle failed: " + e.getMessage());
                    // [FIX-3] رسائل خطأ واضحة حسب نوع الاستثناء
                    String errorMessage = resolveFirebaseError(e);
                    mainHandler.post(() -> onError.onError(errorMessage));
                });
    }

    // ════════════════════════════════════════════════════════
    //  Sign In — Anonymous (Guest)
    // ════════════════════════════════════════════════════════

    public void signInAnonymously(OnSuccessCallback onSuccess, OnErrorCallback onError) {
        mAuth.signInAnonymously()
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    String uid = user != null ? user.getUid() : "";
                    mainHandler.post(() -> onSuccess.onSuccess(uid));
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "signInAnonymously failed: " + e.getMessage());
                    String errorMessage = resolveFirebaseError(e);
                    mainHandler.post(() -> onError.onError(errorMessage));
                });
    }

    // ════════════════════════════════════════════════════════
    //  Sign Out
    // ════════════════════════════════════════════════════════

    public void signOut() {
        mAuth.signOut();
    }

    // ════════════════════════════════════════════════════════
    //  State
    // ════════════════════════════════════════════════════════

    public boolean isLoggedIn() {
        FirebaseUser u = mAuth.getCurrentUser();
        return u != null && !u.isAnonymous();
    }

    public boolean isGuest() {
        FirebaseUser u = mAuth.getCurrentUser();
        return u != null && u.isAnonymous();
    }

    public FirebaseUser getCurrentUser() {
        return mAuth.getCurrentUser();
    }

    public String getCurrentUid() {
        FirebaseUser u = mAuth.getCurrentUser();
        return u != null ? u.getUid() : null;
    }

    // ════════════════════════════════════════════════════════
    //  Check if banned
    // ════════════════════════════════════════════════════════

    public void checkBanned(String uid, OnBanCheckCallback callback) {
        FirebaseDatabase.getInstance()
                .getReference("users")
                .child(uid)
                .child("banned")
                .get()
                .addOnSuccessListener(snap -> {
                    boolean banned = Boolean.TRUE.equals(snap.getValue(Boolean.class));
                    mainHandler.post(() -> callback.onResult(banned));
                })
                .addOnFailureListener(e -> mainHandler.post(() -> callback.onResult(false)));
    }

    // ════════════════════════════════════════════════════════
    //  Save User Profile to Firebase DB
    // ════════════════════════════════════════════════════════

    private void saveUserProfile(FirebaseUser user, Runnable onDone) {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    String token = task.isSuccessful() ? task.getResult() : "";
                    DatabaseReference ref = FirebaseDatabase.getInstance()
                            .getReference("users")
                            .child(user.getUid());

                    ref.child("name").setValue(user.getDisplayName());
                    ref.child("email").setValue(user.getEmail());
                    ref.child("fcmToken").setValue(token);
                    ref.child("lastLogin").setValue(System.currentTimeMillis());

                    ref.child("role").get().addOnSuccessListener(snap -> {
                        String existingRole = snap.getValue(String.class);
                        if (existingRole == null || existingRole.isEmpty()) {
                            ref.child("role").setValue("member");
                        }
                        if (onDone != null) onDone.run();
                    }).addOnFailureListener(e -> { if (onDone != null) onDone.run(); });
                });
    }

    // ════════════════════════════════════════════════════════
    //  [FIX-3] Error Resolution — رسائل خطأ واضحة باللغة العربية
    // ════════════════════════════════════════════════════════

    private String resolveFirebaseError(Exception e) {
        if (e instanceof FirebaseNetworkException) {
            return "لا يوجد اتصال بالإنترنت — تحقق من الاتصال وأعد المحاولة";
        } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
            return "بيانات Google غير صالحة — أعد تسجيل الدخول";
        } else if (e instanceof FirebaseAuthUserCollisionException) {
            return "هذا الحساب مسجل بطريقة أخرى";
        } else if (e instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) e).getErrorCode();
            return "خطأ في المصادقة [" + code + "] — أعد المحاولة";
        } else {
            // [FIX-3] حماية من null في getMessage()
            String msg = e.getMessage();
            return (msg != null && !msg.isEmpty()) ? msg : "خطأ غير معروف — أعد المحاولة";
        }
    }

    // ════════════════════════════════════════════════════════
    //  Callbacks
    // ════════════════════════════════════════════════════════

    public interface OnSuccessCallback {
        void onSuccess(String uid);
    }

    public interface OnErrorCallback {
        void onError(String message);
    }

    public interface OnBanCheckCallback {
        void onResult(boolean isBanned);
    }
}
