package com.missingpersons.app.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import java.util.Locale;

/**
 * LanguageHelper — تبديل اللغة (عربي ↔ إنجليزي)
 *
 * يحفظ اختيار المستخدم في SharedPreferences
 * ويُطبَّق في كل Activity عبر attachBaseContext
 */
public class LanguageHelper {

    private static final String PREF_NAME = "language_prefs";
    private static final String KEY_LANG = "app_language";

    public static final String ARABIC  = "ar";
    public static final String ENGLISH = "en";

    /**
     * يُستدعى في كل Activity.attachBaseContext()
     */
    public static Context applyLanguage(Context context) {
        String lang = getLanguage(context);
        return updateResources(context, lang);
    }

    /**
     * تغيير اللغة وإعادة تشغيل Activity
     *
     * ملاحظة: activity.recreate() لا يُعيد تطبيق الـ locale على Android 13+
     * بسبب تغييرات في نظام Configuration — نستخدم Intent صريح بدلاً منه.
     */
    public static void setLanguage(Activity activity, String lang) {
        SharedPreferences prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANG, lang).apply();

        // إعادة تشغيل صحيحة على Android 13+ (API 33)
        android.content.Intent intent = new android.content.Intent(
                activity, activity.getClass());
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
        activity.finish();
    }

    /**
     * اللغة الحالية
     */
    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANG, ARABIC); // العربية افتراضياً
    }

    /**
     * هل اللغة الحالية عربية؟
     */
    public static boolean isArabic(Context context) {
        return ARABIC.equals(getLanguage(context));
    }

    /**
     * اسم اللغة للعرض
     */
    public static String getLanguageDisplayName(Context context) {
        return isArabic(context) ? "العربية" : "English";
    }

    private static Context updateResources(Context context, String lang) {
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);

        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        config.setLocale(locale);
        config.setLayoutDirection(locale);

        return context.createConfigurationContext(config);
    }
}
