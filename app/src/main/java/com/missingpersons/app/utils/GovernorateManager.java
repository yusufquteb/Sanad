package com.missingpersons.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * GovernorateManager — إدارة اختيار المحافظة للمستخدم.
 *
 * يحفظ المحافظات المختارة في SharedPreferences.
 * يُستخدم في LoginActivity (إعداد أولي) وProfileActivity (تعديل).
 */
public final class GovernorateManager {

    private static final String PREFS_NAME = "gov_prefs";
    private static final String KEY_SELECTED = "selected_govs";
    private static final String KEY_SETUP_DONE = "setup_done";

    private GovernorateManager() {}

    // ── Public API ────────────────────────────────────────────────────────

    /** هل أتم المستخدم إعداد المحافظة مسبقاً؟ */
    public static boolean isSetupDone(Context ctx) {
        return prefs(ctx).getBoolean(KEY_SETUP_DONE, false);
    }

    /** عرض حوار الإعداد الأولي لاختيار المحافظة */
    public static void showSetupDialog(Context ctx, Runnable onDone) {
        showDialog(ctx, "اختر محافظتك", onDone);
    }

    /** عرض حوار تعديل المحافظة */
    public static void showEditDialog(Context ctx, Runnable onDone) {
        showDialog(ctx, "تعديل المحافظة", onDone);
    }

    /** المحافظات المختارة للمستخدم الحالي */
    public static Set<String> getSelectedGovs(Context ctx) {
        return prefs(ctx).getStringSet(KEY_SELECTED, new HashSet<>());
    }

    /**
     * المحافظة الأساسية للمستخدم (أول عنصر من المجموعة).
     * تُستخدم في ReportActivity لتعبئة حقل المحافظة مسبقاً.
     *
     * @return اسم المحافظة، أو "" إذا لم يُختر شيء بعد
     */
    public static String getPrimaryGov(Context ctx) {
        Set<String> govs = getSelectedGovs(ctx);
        return govs.isEmpty() ? "" : govs.iterator().next();
    }

    /** حفظ مجموعة محافظات مباشرةً */
    public static void saveSelectedGovs(Context ctx, Set<String> govs) {
        prefs(ctx).edit()
            .putStringSet(KEY_SELECTED, govs)
            .putBoolean(KEY_SETUP_DONE, !govs.isEmpty())
            .apply();
    }

    // ── Internal ──────────────────────────────────────────────────────────

    private static void showDialog(Context ctx, String title, Runnable onDone) {
        List<String> govList = EgyptAddressHelper.getGovernorates();
        String[] govs = govList.toArray(new String[0]);

        Set<String> current = getSelectedGovs(ctx);
        boolean[] checked = new boolean[govs.length];
        for (int i = 0; i < govs.length; i++) {
            checked[i] = current.contains(govs[i]);
        }

        final Set<String> selected = new HashSet<>(current);

        new android.app.AlertDialog.Builder(ctx)
            .setTitle(title)
            .setMultiChoiceItems(govs, checked, (dialog, which, isChecked) -> {
                if (isChecked) selected.add(govs[which]);
                else           selected.remove(govs[which]);
            })
            .setPositiveButton("حفظ", (dialog, which) -> {
                saveSelectedGovs(ctx, selected);
                if (onDone != null) onDone.run();
            })
            .setNegativeButton("تخطي", (dialog, which) -> {
                prefs(ctx).edit().putBoolean(KEY_SETUP_DONE, true).apply();
                if (onDone != null) onDone.run();
            })
            .setCancelable(false)
            .show();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
