package com.missingpersons.app.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import java.util.Locale;

/**
 * LanguageHelper — تبديل اللغة [إصلاح Phase 5]
 *
 * مشاكل النسخة السابقة:
 *  1. setApplicationLocales قد لا يُعيد تشغيل Activity على بعض الأجهزة
 *  2. attachBaseContext يتعارض مع LocaleListCompat على API < 33
 *  3. اللغة لا تنطبق على Activity المفتوحة حالياً
 *
 * الحل: طبقة مزدوجة:
 *  - SharedPreferences للحفظ الدائم
 *  - Intent.FLAG_ACTIVITY_CLEAR_TASK لإعادة تشغيل كل الـ Activities
 */
public class LanguageHelper {

    private static final String PREF_NAME = "language_prefs";
    private static final String KEY_LANG  = "app_language";

    public static final String ARABIC  = "ar";
    public static final String ENGLISH = "en";

    /**
     * تطبيق اللغة على الـ Context — يُستدعى في attachBaseContext() لكل Activity
     */
    public static Context applyLanguage(Context context) {
        String lang = getLanguage(context);
        return updateResources(context, lang);
    }

    /**
     * تغيير اللغة — يعمل على جميع إصدارات Android
     *
     * [إصلاح] الاستراتيجية:
     *  1. حفظ في SharedPreferences
     *  2. تطبيق عبر AppCompatDelegate (API 33+)
     *  3. إعادة تشغيل Activity عبر Intent.FLAG_ACTIVITY_CLEAR_TASK
     *     (يضمن أن كل الـ stack يُعاد تشغيله بالـ locale الجديد)
     */
    public static void setLanguage(Activity activity, String lang) {
        // 1. حفظ في SharedPreferences — يُقرأ في كل attachBaseContext
        SharedPreferences prefs =
            activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANG, lang).apply();

        // 2. AppCompatDelegate — للأجهزة التي تدعمه
        try {
            LocaleListCompat locales = LocaleListCompat.forLanguageTags(lang);
            AppCompatDelegate.setApplicationLocales(locales);
        } catch (Exception ignored) {}

        // 3. إعادة تشغيل التطبيق من الشاشة الرئيسية (Launcher Activity)
        //    نستخدم getLaunchIntentForPackage لتجنب الاستيراد الدائري
        Intent restartIntent = activity.getPackageManager()
            .getLaunchIntentForPackage(activity.getPackageName());
        if (restartIntent == null) {
            // fallback: أعد تشغيل الـ Activity الحالية
            restartIntent = new Intent(activity, activity.getClass());
        }
        restartIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK  |
            Intent.FLAG_ACTIVITY_CLEAR_TASK
        );
        activity.startActivity(restartIntent);
        activity.finish();
        activity.overridePendingTransition(0, 0);
    }

    /**
     * قراءة اللغة المحفوظة
     */
    public static String getLanguage(Context context) {
        // أولاً: AppCompatDelegate (المصدر الأدق)
        try {
            LocaleListCompat appLocales = AppCompatDelegate.getApplicationLocales();
            if (!appLocales.isEmpty()) {
                Locale loc = appLocales.get(0);
                if (loc != null) {
                    String lang = loc.getLanguage();
                    if (ARABIC.equals(lang) || ENGLISH.equals(lang)) return lang;
                }
            }
        } catch (Exception ignored) {}
        // ثانياً: SharedPreferences
        SharedPreferences prefs =
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANG, ARABIC); // العربية افتراضية
    }

    /** هل اللغة الحالية عربية؟ */
    public static boolean isArabic(Context context) {
        return ARABIC.equals(getLanguage(context));
    }

    /** اسم اللغة للعرض */
    public static String getLanguageDisplayName(Context context) {
        return isArabic(context) ? "العربية" : "English";
    }

    /**
     * تطبيق اللغة على الـ Context (للأجهزة القديمة API < 33)
     * يُستدعى فقط من attachBaseContext
     */
    private static Context updateResources(Context context, String lang) {
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        config.setLocale(locale);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLayoutDirection(locale);
        }
        return context.createConfigurationContext(config);
    }
}
