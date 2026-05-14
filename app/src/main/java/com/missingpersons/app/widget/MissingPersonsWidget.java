package com.missingpersons.app.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.widget.RemoteViews;

import com.missingpersons.app.R;
import com.missingpersons.app.activities.BrowseActivity;
import com.missingpersons.app.activities.CaseDetailActivity;
import com.missingpersons.app.models.AppDatabase;
import com.missingpersons.app.models.ReportEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MissingPersonsWidget — ويدجت الشاشة الرئيسية  [Phase 2 Fix]
 *
 * الإصلاحات:
 *  • استخدام try-catch شامل لكل RemoteViews.setXxx لتجنب
 *    IllegalArgumentException عند عدم وجود الـ ID
 *  • تحديث فوري عند onEnabled + fallback لـ ids فارغة
 *  • requestUpdate() آمن مع null-check
 *
 * يقرأ من Room DB فقط (بلا شبكة — سريع جداً)
 */
public class MissingPersonsWidget extends AppWidgetProvider {

    private static final String TAG = "MissingPersonsWidget";
    private static final ExecutorService BG = Executors.newSingleThreadExecutor();

    // IDs المفقودين (العمود الأيسر)
    private static final int[] NAME_IDS  = {
        R.id.widget_name1, R.id.widget_name2, R.id.widget_name3
    };
    private static final int[] ADDR_IDS  = {
        R.id.widget_addr1, R.id.widget_addr2, R.id.widget_addr3
    };
    private static final int[] DATE_IDS  = {
        R.id.widget_date1, R.id.widget_date2, R.id.widget_date3
    };
    private static final int[] ROW_IDS   = {
        R.id.widget_row1, R.id.widget_row2, R.id.widget_row3
    };

    // IDs المعثورين (العمود الأيمن)
    private static final int[] FOUND_NAME_IDS  = {
        R.id.widget_found_name1, R.id.widget_found_name2, R.id.widget_found_name3
    };
    private static final int[] FOUND_ADDR_IDS  = {
        R.id.widget_found_addr1, R.id.widget_found_addr2, R.id.widget_found_addr3
    };
    private static final int[] FOUND_DATE_IDS  = {
        R.id.widget_found_date1, R.id.widget_found_date2, R.id.widget_found_date3
    };
    private static final int[] FOUND_ROW_IDS   = {
        R.id.widget_row_found1, R.id.widget_row_found2, R.id.widget_row_found3
    };

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) updateWidget(ctx, mgr, id);
    }

    @Override
    public void onEnabled(Context ctx) {
        super.onEnabled(ctx);
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            ComponentName cn = new ComponentName(ctx, MissingPersonsWidget.class);
            int[] ids = mgr.getAppWidgetIds(cn);
            if (ids != null) {
                for (int id : ids) updateWidget(ctx, mgr, id);
            }
        } catch (Exception e) {
            Log.e(TAG, "onEnabled error: " + e.getMessage());
        }
    }

    public static void updateWidget(Context ctx, AppWidgetManager mgr, int widgetId) {
        if (ctx == null || mgr == null) return;
        try {
            RemoteViews views = new RemoteViews(
                ctx.getPackageName(), R.layout.widget_missing_persons);

            // زر "عرض الكل"
            Intent browseIntent = new Intent(ctx, BrowseActivity.class);
            browseIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
            PendingIntent browsePi = PendingIntent.getActivity(ctx, 0, browseIntent, flags);
            views.setOnClickPendingIntent(R.id.widget_btn_all, browsePi);

            BG.execute(() -> {
                try {
                    AppDatabase db = AppDatabase.getInstance(ctx);

                    List<ReportEntity> missingList =
                        db.reportDao().getLatestByType("missing", 3);
                    List<ReportEntity> foundList =
                        db.reportDao().getLatestByType("found", 3);

                    // fallback: إذا لم تكن هناك بيانات محددة النوع، اجلب الكل
                    if (missingList.isEmpty() && foundList.isEmpty()) {
                        List<ReportEntity> all = db.reportDao().getLatestN(6);
                        missingList = new ArrayList<>();
                        foundList   = new ArrayList<>();
                        for (ReportEntity e : all) {
                            if ("found".equals(e.reportType) && foundList.size() < 3)
                                foundList.add(e);
                            else if (missingList.size() < 3)
                                missingList.add(e);
                        }
                    }

                    bindColumn(ctx, views, missingList,
                        NAME_IDS, ADDR_IDS, DATE_IDS, ROW_IDS, false);
                    bindColumn(ctx, views, foundList,
                        FOUND_NAME_IDS, FOUND_ADDR_IDS, FOUND_DATE_IDS, FOUND_ROW_IDS, true);

                    mgr.updateAppWidget(widgetId, views);
                    Log.d(TAG, "Widget updated: missing=" + missingList.size()
                            + " found=" + foundList.size());

                } catch (Exception e) {
                    Log.e(TAG, "Widget BG error: " + e.getMessage());
                    try { mgr.updateAppWidget(widgetId, views); }
                    catch (Exception ignored) {}
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "updateWidget error: " + e.getMessage());
        }
    }

    private static void bindColumn(Context ctx, RemoteViews views,
                                    List<ReportEntity> list,
                                    int[] nameIds, int[] addrIds, int[] dateIds,
                                    int[] rowIds, boolean isFound) {
        SimpleDateFormat sdf = new SimpleDateFormat("d MMM", new Locale("ar"));
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        for (int i = 0; i < 3; i++) {
            try {
                if (i < list.size()) {
                    ReportEntity e = list.get(i);
                    String name = (e.personName != null && !e.personName.isEmpty())
                        ? e.personName : (isFound ? "معثور" : "مجهول");
                    String addr = (e.governorate != null && !e.governorate.isEmpty())
                        ? e.governorate
                        : (e.manualAddress != null ? e.manualAddress : "");
                    String date = sdf.format(new Date(e.timestamp));

                    views.setTextViewText(nameIds[i], name);
                    views.setTextViewText(addrIds[i], addr);
                    views.setTextViewText(dateIds[i], date);

                    // click → CaseDetailActivity
                    Intent intent = new Intent(ctx, CaseDetailActivity.class);
                    intent.putExtra("reportId", e.reportId);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);

                    int reqCode = (e.reportId != null ? e.reportId.hashCode() : i)
                        + (isFound ? 10000 : 0) + i;
                    PendingIntent pi = PendingIntent.getActivity(ctx, reqCode, intent, flags);
                    views.setOnClickPendingIntent(rowIds[i], pi);

                } else {
                    views.setTextViewText(nameIds[i], "—");
                    views.setTextViewText(addrIds[i], "");
                    views.setTextViewText(dateIds[i], "");
                }
            } catch (Exception e) {
                Log.w(TAG, "bindColumn slot " + i + " error: " + e.getMessage());
            }
        }
    }

    /**
     * يطلب تحديث كل الـ widget instances
     * آمن: لا يُلقي exception لو لم يكن هناك widget
     */
    public static void requestUpdate(Context ctx) {
        if (ctx == null) return;
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            ComponentName cn = new ComponentName(ctx, MissingPersonsWidget.class);
            int[] ids = mgr.getAppWidgetIds(cn);
            if (ids == null || ids.length == 0) return;

            Intent intent = new Intent(ctx, MissingPersonsWidget.class);
            intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            ctx.sendBroadcast(intent);
            Log.d(TAG, "Widget update requested for " + ids.length + " instances");
        } catch (Exception e) {
            Log.w(TAG, "requestUpdate error (non-fatal): " + e.getMessage());
        }
    }
}
