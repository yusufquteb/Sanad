package com.missingpersons.app.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * ShareHelper — ينشئ بطاقة صورة للبلاغ ويشاركها
 *
 * الاستخدام:
 *   ShareHelper.shareAsImage(context, name, addr, age, gender, ts, id, photoBitmap);
 *   ShareHelper.shareToWhatsApp(context, name, addr, age, gender, ts, id, photoBitmap);
 *   ShareHelper.shareReport(context, name, addr, age, gender, ts, id, photoBitmap);
 */
public class ShareHelper {

    private static final int CARD_W = 900;
    private static final int CARD_H = 600;

    // ── ألوان البطاقة ────────────────────────────────────────────────────
    private static final int C_BG       = 0xFF1A5276;   // أزرق داكن
    private static final int C_HEADER   = 0xFF0D2E42;   // أزرق أغمق
    private static final int C_ACCENT   = 0xFFE74C3C;   // أحمر للتحذير
    private static final int C_WHITE    = 0xFFFFFFFF;
    private static final int C_LIGHT    = 0xCCFFFFFF;
    private static final int C_PHOTO_BG = 0xFF2C3E50;

    // ════════════════════════════════════════════════════════════════════
    //  Public API
    // ════════════════════════════════════════════════════════════════════

    /** مشاركة عامة (يفتح sheet الاختيار) */
    public static void shareReport(Context ctx, String name, String addr,
                                   String age, String gender, long timestamp,
                                   String reportId, Bitmap photo) {
        Uri uri = buildCardUri(ctx, name, addr, age, gender, timestamp, reportId, photo);
        if (uri == null) { shareText(ctx, name, addr, reportId); return; }

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_TEXT, buildText(name, addr, reportId));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ctx.startActivity(Intent.createChooser(intent, "مشاركة البلاغ"));
    }

    /** مشاركة مباشرة لواتساب */
    public static void shareToWhatsApp(Context ctx, String name, String addr,
                                       String age, String gender, long timestamp,
                                       String reportId, Bitmap photo) {
        Uri uri = buildCardUri(ctx, name, addr, age, gender, timestamp, reportId, photo);
        if (uri == null) { shareText(ctx, name, addr, reportId); return; }

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setPackage("com.whatsapp");
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_TEXT, buildText(name, addr, reportId));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            ctx.startActivity(intent);
        } catch (Exception e) {
            // واتساب غير مثبت — fallback للمشاركة العامة
            shareReport(ctx, name, addr, age, gender, timestamp, reportId, photo);
        }
    }

    /** للاستخدام من BrowseActivity / item card */
    public static void shareAsImage(Context ctx, String name, String addr,
                                    String age, String gender, long timestamp,
                                    String reportId, Bitmap photo) {
        shareReport(ctx, name, addr, age, gender, timestamp, reportId, photo);
    }

    // ════════════════════════════════════════════════════════════════════
    //  Card Generation
    // ════════════════════════════════════════════════════════════════════

    private static Uri buildCardUri(Context ctx, String name, String addr,
                                    String age, String gender, long timestamp,
                                    String reportId, Bitmap photo) {
        try {
            Bitmap card = buildCard(name, addr, age, gender, timestamp, reportId, photo);
            File dir  = new File(ctx.getCacheDir(), "shared_cards");
            dir.mkdirs();
            File file = new File(dir, "missing_" + reportId + ".png");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                card.compress(Bitmap.CompressFormat.PNG, 95, fos);
            }
            card.recycle();
            return FileProvider.getUriForFile(ctx,
                ctx.getPackageName() + ".provider", file);
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap buildCard(String name, String addr, String age,
                                    String gender, long timestamp,
                                    String reportId, Bitmap photo) {
        Bitmap bmp    = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint  paint  = new Paint(Paint.ANTI_ALIAS_FLAG);

        // ── خلفية ────────────────────────────────────────────────────
        paint.setColor(C_BG);
        canvas.drawRect(0, 0, CARD_W, CARD_H, paint);

        // ── شريط العنوان ─────────────────────────────────────────────
        paint.setColor(C_HEADER);
        canvas.drawRect(0, 0, CARD_W, 90, paint);

        // عنوان "مفقود / Missing"
        paint.setColor(C_ACCENT);
        paint.setTextSize(48f);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("🔴 مفقود", CARD_W - 30, 62, paint);

        // اسم التطبيق
        paint.setColor(C_LIGHT);
        paint.setTextSize(22f);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("سند | Sanad", 30, 58, paint);

        // ── صورة الشخص (يسار) ────────────────────────────────────────
        int photoSize = 200;
        int photoX    = 40;
        int photoY    = 110;
        if (photo != null && !photo.isRecycled()) {
            Bitmap scaled = Bitmap.createScaledBitmap(photo, photoSize, photoSize, true);
            paint.setColor(C_PHOTO_BG);
            canvas.drawRoundRect(new RectF(photoX - 4, photoY - 4,
                photoX + photoSize + 4, photoY + photoSize + 4),
                16, 16, paint);
            // نسخ الصورة كدائرة
            Bitmap circle = toCircleBitmap(scaled, photoSize);
            canvas.drawBitmap(circle, photoX, photoY, null);
            circle.recycle();
            scaled.recycle();
        } else {
            // placeholder
            paint.setColor(C_PHOTO_BG);
            canvas.drawRoundRect(new RectF(photoX, photoY,
                photoX + photoSize, photoY + photoSize), 16, 16, paint);
            paint.setColor(C_LIGHT);
            paint.setTextSize(80f);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("👤", photoX + photoSize / 2f, photoY + photoSize * 0.65f, paint);
        }

        // ── بيانات الشخص (يمين الصورة) ───────────────────────────────
        int textX = photoX + photoSize + 30;
        int textY = 145;

        // الاسم
        paint.setColor(C_WHITE);
        paint.setTextSize(42f);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.LEFT);
        String safeName = name != null ? name : "غير محدد";
        canvas.drawText(safeName.length() > 20
            ? safeName.substring(0, 20) + "…" : safeName, textX, textY, paint);

        textY += 50;
        paint.setTextSize(26f);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setColor(C_LIGHT);

        // العمر والجنس
        String ageGender = "العمر: " + (age != null ? age : "?")
            + "    |    " + (gender != null ? gender : "غير محدد");
        canvas.drawText(ageGender, textX, textY, paint);

        textY += 38;
        // الموقع
        if (addr != null && !addr.isEmpty()) {
            paint.setColor(0xFFAED6F1);
            String shortAddr = addr.length() > 35 ? addr.substring(0, 35) + "…" : addr;
            canvas.drawText("📍 " + shortAddr, textX, textY, paint);
        }

        textY += 38;
        // التاريخ
        if (timestamp > 0) {
            paint.setColor(C_LIGHT);
            String dateStr = new SimpleDateFormat("dd/MM/yyyy", new Locale("ar"))
                .format(new Date(timestamp));
            canvas.drawText("📅 " + dateStr, textX, textY, paint);
        }

        // ── نداء المساعدة ─────────────────────────────────────────────
        paint.setColor(0xFFE74C3C);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(32f);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("هل رأيت هذا الشخص؟ أرجوك ساعد في العثور عليه",
            CARD_W / 2f, 420, paint);

        // ── رقم البلاغ + ترقيم ────────────────────────────────────────
        paint.setColor(C_HEADER);
        canvas.drawRect(0, CARD_H - 80, CARD_W, CARD_H, paint);

        paint.setColor(C_LIGHT);
        paint.setTextSize(22f);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("رقم البلاغ: " + (reportId != null ? reportId : "—"), 30, CARD_H - 48, paint);

        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("حمّل تطبيق المفقودين للمساعدة", CARD_W - 30, CARD_H - 48, paint);

        // ── خط فاصل ──────────────────────────────────────────────────
        paint.setColor(C_ACCENT);
        paint.setStrokeWidth(4f);
        canvas.drawLine(0, CARD_H - 82, CARD_W, CARD_H - 82, paint);


        // ══ Watermark سند ══════════════════════════════════════════
        paint.setColor(0x22000000); // شبه شفاف
        paint.setTextSize(18f);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
        canvas.drawText("سند | Sanad  •  مش لوحدك", 20, CARD_H - 12, paint);

        // خط تحت الـ watermark
        paint.setColor(0x331565C0);
        paint.setStrokeWidth(2f);
        canvas.drawLine(0, CARD_H - 30, CARD_W, CARD_H - 30, paint);
        // ══════════════════════════════════════════════════════════

        return bmp;
    }

    private static Bitmap toCircleBitmap(Bitmap src, int size) {
        Bitmap out    = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint  paint  = new Paint(Paint.ANTI_ALIAS_FLAG);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(src, 0, 0, paint);
        return out;
    }

    // ════════════════════════════════════════════════════════════════════
    //  Text fallback
    // ════════════════════════════════════════════════════════════════════

    private static String buildText(String name, String addr, String reportId) {
        StringBuilder sb = new StringBuilder();
        sb.append("🔴 *مفقود*\n");
        sb.append("الاسم: ").append(name != null ? name : "غير محدد").append("\n");
        if (addr != null && !addr.isEmpty())
            sb.append("الموقع: ").append(addr).append("\n");
        if (reportId != null)
            sb.append("رقم البلاغ: #").append(reportId).append("\n");
        sb.append("\nهل رأيت هذا الشخص؟ تواصل معنا من خلال تطبيق المفقودين.");
        return sb.toString();
    }

    private static void shareText(Context ctx, String name, String addr, String id) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, buildText(name, addr, id));
        ctx.startActivity(Intent.createChooser(i, "مشاركة البلاغ"));
    }

    // ════════════════════════════════════════════════════════════════════
    //  [7.6] Deep Link helpers — مُضافة للتوافق مع CaseDetailActivity
    // ════════════════════════════════════════════════════════════════════

    private static final String DEEP_LINK_SCHEME = "sanad";
    private static final String DEEP_LINK_HOST   = "report";

    /**
     * بناء deep link URI: sanad://report/{reportId}
     */
    public static android.net.Uri buildReportUri(String reportId) {
        return android.net.Uri.parse(DEEP_LINK_SCHEME + "://" + DEEP_LINK_HOST + "/" + reportId);
    }

    /**
     * استخراج reportId من deep link URI.
     * يُستدعى في CaseDetailActivity.onCreate():
     *   String id = ShareHelper.extractReportId(getIntent().getData());
     */
    public static String extractReportId(android.net.Uri uri) {
        if (uri == null) return null;
        if (!DEEP_LINK_SCHEME.equals(uri.getScheme())) return null;
        if (!DEEP_LINK_HOST.equals(uri.getHost())) return null;
        String path = uri.getPath();
        if (path == null || path.isEmpty()) return null;
        return path.startsWith("/") ? path.substring(1) : path;
    }

}