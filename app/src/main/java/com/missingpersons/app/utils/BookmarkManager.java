package com.missingpersons.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

/**
 * BookmarkManager — حفظ البلاغات المفضلة للوصول السريع
 * يحفظ محلياً في SharedPreferences
 */
public class BookmarkManager {

    private static final String PREF_NAME = "bookmarks";
    private static final String KEY_IDS = "bookmarked_ids";

    /**
     * حفظ/إلغاء حفظ بلاغ
     */
    public static boolean toggle(Context context, String reportId) {
        Set<String> ids = getBookmarkedIds(context);
        boolean wasBookmarked = ids.contains(reportId);

        if (wasBookmarked) {
            ids.remove(reportId);
        } else {
            ids.add(reportId);
        }

        save(context, ids);
        return !wasBookmarked; // true = تم الحفظ, false = تم الإلغاء
    }

    /**
     * هل البلاغ محفوظ؟
     */
    public static boolean isBookmarked(Context context, String reportId) {
        return getBookmarkedIds(context).contains(reportId);
    }

    /**
     * كل الـ IDs المحفوظة
     */
    public static Set<String> getBookmarkedIds(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return new HashSet<>(prefs.getStringSet(KEY_IDS, new HashSet<>()));
    }

    /**
     * عدد المحفوظات
     */
    public static int getCount(Context context) {
        return getBookmarkedIds(context).size();
    }

    /**
     * مسح كل المحفوظات
     */
    public static void clearAll(Context context) {
        save(context, new HashSet<>());
    }

    private static void save(Context context, Set<String> ids) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_IDS, ids)
            .apply();
    }
}
