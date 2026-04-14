package com.missingpersons.app.utils;

import android.content.Context;
import android.os.Bundle;
import com.google.firebase.analytics.FirebaseAnalytics;

/**
 * AnalyticsHelper — تتبع أحداث التطبيق بـ Firebase Analytics
 */
public class AnalyticsHelper {

    private static FirebaseAnalytics analytics;

    public static void init(Context context) {
        if (analytics == null) {
            analytics = FirebaseAnalytics.getInstance(context);
        }
    }

    private static void logEvent(String event, Bundle params) {
        if (analytics != null) analytics.logEvent(event, params);
    }

    // ─── أحداث البلاغات ───

    public static void logReportCreated(String reportId, String gender) {
        Bundle b = new Bundle();
        b.putString("report_id", reportId);
        b.putString("gender", gender);
        logEvent("report_created", b);
    }

    public static void logReportApproved(String reportId) {
        Bundle b = new Bundle();
        b.putString("report_id", reportId);
        logEvent("report_approved", b);
    }

    public static void logFoundPersonCreated(String reportId) {
        Bundle b = new Bundle();
        b.putString("report_id", reportId);
        logEvent("found_person_created", b);
    }

    // ─── أحداث المطابقة ───

    public static void logFaceMatchFound(String reportId, String foundId, int percent) {
        Bundle b = new Bundle();
        b.putString("report_id", reportId);
        b.putString("found_id", foundId);
        b.putInt("similarity", percent);
        logEvent("face_match_found", b);
    }

    // ─── أحداث البحث ───

    public static void logSearchPerformed(String searchType) {
        Bundle b = new Bundle();
        b.putString("search_type", searchType);
        logEvent("search_performed", b);
    }

    public static void logSearchWithFilters(String gender, String ageRange, String area) {
        Bundle b = new Bundle();
        if (gender != null) b.putString("gender", gender);
        if (ageRange != null) b.putString("age_range", ageRange);
        if (area != null) b.putString("area", area);
        logEvent("search_with_filters", b);
    }

    // ─── أحداث الشات ───

    public static void logChatStarted(String chatId) {
        Bundle b = new Bundle();
        b.putString("chat_id", chatId);
        logEvent("chat_started", b);
    }

    // ─── أحداث المشاركة ───

    public static void logReportShared(String reportId, String method) {
        Bundle b = new Bundle();
        b.putString("report_id", reportId);
        b.putString("share_method", method);
        logEvent("report_shared", b);
    }

    public static void logPDFExported(String reportId) {
        Bundle b = new Bundle();
        b.putString("report_id", reportId);
        logEvent("pdf_exported", b);
    }

    // ─── أحداث عامة ───

    public static void logScreenView(String screenName) {
        Bundle b = new Bundle();
        b.putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName);
        logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, b);
    }

    public static void logLogin(String method) {
        Bundle b = new Bundle();
        b.putString(FirebaseAnalytics.Param.METHOD, method);
        logEvent(FirebaseAnalytics.Event.LOGIN, b);
    }

    public static void logAppOpen() {
        logEvent("app_open", null);
    }
}
