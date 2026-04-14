package com.missingpersons.app.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import androidx.annotation.NonNull;
import com.google.firebase.database.*;
import com.missingpersons.app.R;
import com.missingpersons.app.activities.BrowseActivity;
import com.missingpersons.app.activities.CaseDetailActivity;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * MissingPersonsWidget — ويدجت الشاشة الرئيسية
 *
 * يعرض آخر 3 حالات مفقودين مع:
 * - اسم الشخص
 * - المنطقة
 * - التاريخ
 * - زر "عرض الكل" يفتح BrowseActivity
 *
 * لإضافة الويدجت: اضغط مطولاً على الشاشة الرئيسية → ويدجت → المفقودون
 */
public class MissingPersonsWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids)
            updateWidget(context, manager, id);
    }

    public static void updateWidget(Context ctx, AppWidgetManager mgr, int widgetId) {
        RemoteViews views = new RemoteViews(ctx.getPackageName(),
            R.layout.widget_missing_persons);

        // زر "عرض الكل"
        Intent browseIntent = new Intent(ctx, BrowseActivity.class);
        PendingIntent browsePi = PendingIntent.getActivity(ctx, 0, browseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_btn_all, browsePi);

        // تحميل آخر 3 حالات من Firebase
        FirebaseDatabase.getInstance().getReference("reports")
            .orderByChild("timestamp")
            .limitToLast(3)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    List<DataSnapshot> list = new ArrayList<>();
                    for (DataSnapshot ch : snap.getChildren()) {
                        String status = ch.child("status").getValue(String.class);
                        if ("approved".equals(status)) list.add(ch);
                    }
                    // عكس الترتيب → الأحدث أولاً
                    Collections.reverse(list);

                    bindCase(ctx, views, list, 0,
                        R.id.widget_name1, R.id.widget_addr1, R.id.widget_date1, R.id.widget_row1);
                    bindCase(ctx, views, list, 1,
                        R.id.widget_name2, R.id.widget_addr2, R.id.widget_date2, R.id.widget_row2);
                    bindCase(ctx, views, list, 2,
                        R.id.widget_name3, R.id.widget_addr3, R.id.widget_date3, R.id.widget_row3);

                    mgr.updateAppWidget(widgetId, views);
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {
                    mgr.updateAppWidget(widgetId, views);
                }
            });

        mgr.updateAppWidget(widgetId, views);
    }

    private static void bindCase(Context ctx, RemoteViews views,
                                  List<DataSnapshot> list, int idx,
                                  int nameId, int addrId, int dateId, int rowId) {
        if (idx >= list.size()) {
            views.setViewVisibility(rowId, android.view.View.GONE);
            return;
        }
        DataSnapshot snap = list.get(idx);
        String name  = snap.child("personName").getValue(String.class);
        String addr  = snap.child("manualAddress").getValue(String.class);
        Long   ts    = snap.child("timestamp").getValue(Long.class);
        String id    = snap.getKey();

        views.setViewVisibility(rowId, android.view.View.VISIBLE);
        views.setTextViewText(nameId, name != null ? name : "مجهول");
        views.setTextViewText(addrId, addr != null
            ? (addr.length() > 25 ? addr.substring(0, 25) + "…" : addr) : "");
        views.setTextViewText(dateId, ts != null
            ? new SimpleDateFormat("dd/MM", new Locale("ar")).format(new Date(ts)) : "");

        // ضغط على الصف → تفاصيل الحالة
        if (id != null) {
            Intent detail = new Intent(ctx, CaseDetailActivity.class);
            detail.putExtra("reportId", id);
            PendingIntent pi = PendingIntent.getActivity(ctx, idx, detail,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(rowId, pi);
        }
    }
}
