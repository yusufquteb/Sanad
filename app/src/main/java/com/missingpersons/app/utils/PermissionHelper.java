package com.missingpersons.app.utils;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class PermissionHelper {

    public static final int REQUEST_ALL_PERMISSIONS = 1001;
    public static final int REQUEST_CAMERA = 1002;
    public static final int REQUEST_LOCATION = 1003;
    public static final int REQUEST_STORAGE = 1004;
    public static final int REQUEST_NOTIFICATIONS = 1005;

    // ════════════════════════════════════════════════════════
    //  Biometric / Face-data Prominent Disclosure & Consent
    //
    //  متطلبات Google Play (User Data policy — Prominent Disclosure
    //  and Consent): أي وصول لبيانات حساسة (كاميرا/صور تُستخدم لاستخراج
    //  بصمة وجه) يجب أن يُسبَق بإفصاح داخل التطبيق نفسه — وليس فقط في
    //  سياسة الخصوصية أو وصف المتجر — يشرح البيانات المُجمَّعة وسبب
    //  جمعها، ويطلب موافقة صريحة (Affirmative Action) قبل طلب صلاحية
    //  الكاميرا/المعرض مباشرةً. بدون هذا الإفصاح يكون التطبيق عرضة
    //  للرفض عند المراجعة بسبب معالجة بيانات بيومترية (ملامح الوجه)
    //  دون إفصاح واضح ومسبق.
    // ════════════════════════════════════════════════════════

    private static final String PREF_CONSENT           = "sanad_consent";
    private static final String KEY_BIOMETRIC_CONSENT   = "biometric_disclosure_accepted";

    public interface ConsentCallback {
        void onDecision(boolean accepted);
    }

    /** هل سبق أن وافق المستخدم على إفصاح معالجة بيانات الوجه؟ */
    public static boolean hasBiometricConsent(Activity activity) {
        return activity.getSharedPreferences(PREF_CONSENT, Activity.MODE_PRIVATE)
                .getBoolean(KEY_BIOMETRIC_CONSENT, false);
    }

    /**
     * يعرض إفصاحاً واضحاً قبل أول استخدام لأي ميزة تلتقط/تختار صورة وجه
     * (تصوير بلاغ، بحث بالوجه، إلخ) ويطلب موافقة صريحة قبل المتابعة إلى
     * طلب صلاحية الكاميرا/المعرض الفعلي. يُحفظ القرار محلياً بحيث لا
     * يُعرض إلا مرة واحدة لكل مستخدم.
     */
    public static void ensureBiometricConsent(Activity activity, ConsentCallback callback) {
        if (hasBiometricConsent(activity)) {
            callback.onDecision(true);
            return;
        }
        new AlertDialog.Builder(activity)
            .setTitle("🔒 معالجة بيانات الوجه بالذكاء الاصطناعي")
            .setMessage(
                "لتفعيل ميزة التقاط/اختيار الصور، يحلّل التطبيق صورة الوجه "
                + "لاستخراج سمات رقمية (بصمة وجه) ومقارنتها ببلاغات المفقودين "
                + "والمعثور عليهم المخزّنة في قاعدة بياناتنا، بهدف اكتشاف أي "
                + "تطابق محتمل فقط.\n\n"
                + "• لا تُشارك هذه البيانات مع أي طرف ثالث لأغراض غير متعلقة "
                + "بالبحث عن المفقودين.\n"
                + "• يمكنك طلب حذف بياناتك (صورك وبصمة وجهك) في أي وقت.\n"
                + "• التفاصيل الكاملة في سياسة الخصوصية.")
            .setPositiveButton("أوافق ومتابعة", (d, w) -> {
                activity.getSharedPreferences(PREF_CONSENT, Activity.MODE_PRIVATE)
                    .edit().putBoolean(KEY_BIOMETRIC_CONSENT, true).apply();
                callback.onDecision(true);
            })
            .setNegativeButton("إلغاء", (d, w) -> callback.onDecision(false))
            .setCancelable(false)
            .show();
    }

    public static String[] getAllRequiredPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.CAMERA);
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            // Android 12 and below
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
        return permissions.toArray(new String[0]);
    }

    public static boolean hasAllPermissions(Activity activity) {
        for (String permission : getAllRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(activity, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasCameraPermission(Activity activity) {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasLocationPermission(Activity activity) {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasStoragePermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    public static void requestAllPermissions(Activity activity) {
        ActivityCompat.requestPermissions(activity, getAllRequiredPermissions(), REQUEST_ALL_PERMISSIONS);
    }

    public static void requestCameraPermission(Activity activity) {
        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
    }

    public static void requestLocationPermission(Activity activity) {
        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
    }

    public static void requestStoragePermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.READ_MEDIA_IMAGES}, REQUEST_STORAGE);
        } else {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE);
        }
    }

    public static boolean areAllGranted(int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }
}
