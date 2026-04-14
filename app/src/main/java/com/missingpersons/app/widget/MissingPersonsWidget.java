package com.missingpersons.app.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.missingpersons.app.R;
import com.missingpersons.app.activities.BrowseActivity;
import com.missingpersons.app.activities.CaseDetailActivity;
import com.missingpersons.app.models.AppDatabase;
import com.missingpersons.app.models.ReportEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MissingPersonsWidget — ويدجت الشاشة الرئيسية (مُصلَّح)
 *
 * إصلاحات هذا الإصدار:
 * ─────────────────────
 * [إصلاح 1] IDs في widget_missing_persons.xml هي:
 *   widget_name1, widget_name2, widget_name3    (بدون underscore قبل الرقم)
 *   widget_addr1, widget_addr2, widget_addr3
 *   widget_date1, widget_date2, widget_date3
 *   widget_row1, widget_row2, widget_row3        ← للـ click (وليس widget_case_N)
 *
 * [إصلاح 2] ReportEntity لا تحتوي على حقل "location"
 *   الحل: نستخدم governorate (المحافظة) كعنوان مختصر
 *
 * [إصلاح 3] ReportDao ليس فيه getLatestApproved(int)
 *   الحل: نستخدم getLatestN(int n) الموجودة بالفعل
 */
public class MissingPersonsWidget extends AppWidgetProvider {

    private static final ExecutorService BG = Executors.newSingleThreadExecutor();

    // IDs الحقيقية من widget_missing_persons.xml
    private static final int[] NAME_IDS  = {
        R.id.widget_name1, R.id.widget_name2, R.id.widget_name3
    };
    private static final int[] ADDR_IDS  = {
        R.id.widget_addr1, R.id.widget_addr2, R.id.widget_addr3
    };
    private static final int[] DATE_IDS  = {
        R.id.widget_date1, R.id.widget_date2, R.id.widget_date3
    };
    // الـ click على الصفوف كاملة (widget_row1/2/3)
    private static final int[] ROW_IDS   = {
        R.id.widget_row1, R.id.widget_row2, R.id.widget_row3
    };

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) updateWidget(ctx, mgr, id);
    }

    // ════════════════════════════════════════════════════════
    //  updateWidget — يقرأ من Room (بلا شبكة)
    // ════════════════════════════════════════════════════════

    public static void updateWidget(Context ctx, AppWidgetManager mgr, int widgetId) {
        RemoteViews views = new RemoteViews(
            ctx.getPackageName(), R.layout.widget_missing_persons);

        // زر "عرض الكل"
        Intent browseIntent = new Intent(ctx, BrowseActivity.class);
        PendingIntent browsePi = PendingIntent.getActivity(
            ctx, 0, browseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_btn_all, browsePi);

        // قراءة من Room في background thread
        BG.execute(() -> {
            try {
                // [إصلاح 3] getLatestN موجودة في ReportDao
                List<ReportEntity> list =
                    AppDatabase.getInstance(ctx).reportDao().getLatestN(3);

                bindCases(ctx, views, list);
                mgr.updateAppWidget(widgetId, views);

            } catch (Exception e) {
                android.util.Log.e("Widget", "Room read failed: " + e.getMessage());
                try { mgr.updateAppWidget(widgetId, views); }
                catch (Exception ignored) {}
            }
        });
    }

    // ════════════════════════════════════════════════════════
    //  bindCases — ربط البيانات بالـ views
    // ════════════════════════════════════════════════════════

    private static void bindCases(Context ctx, RemoteViews views,
                                   List<ReportEntity> list) {
        SimpleDateFormat sdf = new SimpleDateFormat("d MMM", new Locale("ar"));

        for (int i = 0; i < 3; i++) {
            if (i < list.size()) {
                ReportEntity e = list.get(i);

                // الاسم
                String name = (e.personName != null && !e.personName.isEmpty())
                    ? e.personName : "مجهول";

                // [إصلاح 2] لا يوجد حقل location في ReportEntity
                // نستخدم governorate كعنوان مختصر
                String addr = (e.governorate != null && !e.governorate.isEmpty())
                    ? e.governorate
                    : (e.manualAddress != null ? e.manualAddress : "");

                String date = sdf.format(new Date(e.timestamp));

                views.setTextViewText(NAME_IDS[i], name);
                views.setTextViewText(ADDR_IDS[i], addr);
                views.setTextViewText(DATE_IDS[i], date);

                // [إصلاح 1] نضغط على widget_row1/2/3 وليس widget_case_1/2/3
                Intent intent = new Intent(ctx, CaseDetailActivity.class);
                intent.putExtra("reportId", e.reportId);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);

                PendingIntent pi = PendingIntent.getActivity(
                    ctx,
                    (e.reportId != null ? e.reportId.hashCode() : i),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                views.setOnClickPendingIntent(ROW_IDS[i], pi);

            } else {
                // Slot فارغ
                views.setTextViewText(NAME_IDS[i], "—");
                views.setTextViewText(ADDR_IDS[i], "");
                views.setTextViewText(DATE_IDS[i], "");
            }
        }
    }

    // ════════════════════════════════════════════════════════
    //  requestUpdate — استدعِ بعد أي sync ناجح
    // ════════════════════════════════════════════════════════

    public static void requestUpdate(Context ctx) {
        if (ctx == null) return;
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        ComponentName cn  = new ComponentName(ctx, MissingPersonsWidget.class);
        int[] ids = mgr.getAppWidgetIds(cn);
        if (ids != null && ids.length > 0) {
            Intent intent = new Intent(ctx, MissingPersonsWidget.class);
            intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            ctx.sendBroadcast(intent);
        }
    }

    @Override
    public void onEnabled(Context ctx) {
        super.onEnabled(ctx);
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(
            new ComponentName(ctx, MissingPersonsWidget.class));
        for (int id : ids) updateWidget(ctx, mgr, id);
    }
}
