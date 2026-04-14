package com.missingpersons.app.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * PDFExportHelper — يُصدِّر تقرير مفقود كـ PDF جاهز للشرطة
 *
 * الاستخدام:
 *   PDFExportHelper.exportReport(context, name, addr, age, gender, ts,
 *                                reporterId, reportId, photoBitmap);
 */
public class PDFExportHelper {

    private static final int PAGE_W = 595;   // A4 @ 72dpi
    private static final int PAGE_H = 842;

    // ── ألوان ─────────────────────────────────────────────────────────
    private static final int C_DARK   = 0xFF1A5276;
    private static final int C_BLACK  = 0xFF000000;
    private static final int C_GRAY   = 0xFF757575;
    private static final int C_LGRAY  = 0xFFEEEEEE;
    private static final int C_WHITE  = 0xFFFFFFFF;
    private static final int C_RED    = 0xFFB71C1C;

    public static void exportReport(Context ctx,
                                    String name, String addr, String age,
                                    String gender, long timestamp,
                                    String reporterId, String reportId,
                                    Bitmap photo) {
        new Thread(() -> {
            try {
                File   pdfFile = createPdf(ctx, name, addr, age, gender,
                                           timestamp, reporterId, reportId, photo);
                Uri    uri     = FileProvider.getUriForFile(ctx,
                                     ctx.getPackageName() + ".provider", pdfFile);

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, "application/pdf");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                              | Intent.FLAG_ACTIVITY_NEW_TASK);

                // محاولة مشاركة عبر PDF viewer أو WhatsApp
                Intent chooser = Intent.createChooser(intent, "فتح / مشاركة PDF");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(chooser);

            } catch (Exception e) {
                ((android.app.Activity) ctx).runOnUiThread(() ->
                    Toast.makeText(ctx, "خطأ في إنشاء PDF: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    // ════════════════════════════════════════════════════════════════════

    private static File createPdf(Context ctx,
                                  String name, String addr, String age,
                                  String gender, long timestamp,
                                  String reporterId, String reportId,
                                  Bitmap photo) throws Exception {
        PdfDocument doc  = new PdfDocument();
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create();
        PdfDocument.Page page = doc.startPage(info);
        Canvas c = page.getCanvas();

        drawPage(c, name, addr, age, gender, timestamp, reporterId, reportId, photo);

        doc.finishPage(page);

        File dir  = new File(ctx.getCacheDir(), "pdf_reports");
        dir.mkdirs();
        File file = new File(dir, "report_" + reportId + ".pdf");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            doc.writeTo(fos);
        }
        doc.close();
        return file;
    }

    private static void drawPage(Canvas c,
                                 String name, String addr, String age,
                                 String gender, long timestamp,
                                 String reporterId, String reportId,
                                 Bitmap photo) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // ── خلفية بيضاء ──────────────────────────────────────────────
        p.setColor(C_WHITE);
        c.drawRect(0, 0, PAGE_W, PAGE_H, p);

        // ── شريط العنوان ─────────────────────────────────────────────
        p.setColor(C_DARK);
        c.drawRect(0, 0, PAGE_W, 70, p);

        p.setColor(C_WHITE);
        p.setTextSize(26f);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText("نموذج بلاغ مفقود — المفقودون", PAGE_W / 2f, 44, p);

        // ── خط أحمر ──────────────────────────────────────────────────
        p.setColor(C_RED);
        c.drawRect(0, 70, PAGE_W, 76, p);

        // ── صورة الشخص ───────────────────────────────────────────────
        int photoSize = 130;
        int photoX    = 30, photoY = 90;

        if (photo != null && !photo.isRecycled()) {
            Bitmap scaled = Bitmap.createScaledBitmap(photo, photoSize, photoSize, true);
            p.setColor(C_LGRAY);
            c.drawRect(photoX - 2, photoY - 2,
                       photoX + photoSize + 2, photoY + photoSize + 2, p);
            c.drawBitmap(scaled, photoX, photoY, null);
            scaled.recycle();
        } else {
            p.setColor(C_LGRAY);
            c.drawRect(photoX, photoY, photoX + photoSize, photoY + photoSize, p);
            p.setColor(C_GRAY);
            p.setTextSize(50f);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText("👤", photoX + photoSize / 2f, photoY + photoSize * 0.65f, p);
        }

        // ── بيانات الشخص ─────────────────────────────────────────────
        int col2X = photoX + photoSize + 25;
        int y     = 110;

        drawField(c, p, "الاسم الكامل", name, col2X, y);             y += 44;
        drawField(c, p, "العمر", age, col2X, y);                     y += 44;
        drawField(c, p, "الجنس", gender, col2X, y);                  y += 44;

        // ── خط فاصل ──────────────────────────────────────────────────
        y = photoY + photoSize + 20;
        p.setColor(C_LGRAY);
        p.setStrokeWidth(1.5f);
        c.drawLine(30, y, PAGE_W - 30, y, p);
        y += 16;

        // ── معلومات إضافية ────────────────────────────────────────────
        sectionTitle(c, p, "معلومات البلاغ", y); y += 28;

        drawField(c, p, "آخر موقع معروف", addr, 30, y);               y += 44;

        String dateStr = timestamp > 0
            ? new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("ar"))
                .format(new Date(timestamp)) : "—";
        drawField(c, p, "تاريخ رفع البلاغ", dateStr, 30, y);          y += 44;
        drawField(c, p, "رقم البلاغ", "#" + reportId, 30, y);         y += 44;

        // ── تعليمات ───────────────────────────────────────────────────
        p.setColor(C_LGRAY);
        c.drawLine(30, y, PAGE_W - 30, y, p);
        y += 20;

        sectionTitle(c, p, "للإبلاغ عن معلومات", y); y += 28;

        p.setColor(C_BLACK);
        p.setTextSize(14f);
        p.setTypeface(Typeface.DEFAULT);
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText("يُرجى التواصل مع الجهات المختصة أو عبر تطبيق المفقودين مباشرةً.",
            PAGE_W - 30, y, p);
        y += 20;
        c.drawText("لا تتقاضَ مالاً مقابل المعلومات. لا تلتقِ بأحد في مكان منعزل.",
            PAGE_W - 30, y, p);

        // ── تذييل ─────────────────────────────────────────────────────
        p.setColor(C_DARK);
        c.drawRect(0, PAGE_H - 45, PAGE_W, PAGE_H, p);

        p.setColor(C_WHITE);
        p.setTextSize(13f);
        p.setTypeface(Typeface.DEFAULT);
        p.setTextAlign(Paint.Align.CENTER);
        String footer = "تم الإنشاء بواسطة تطبيق المفقودون  |  "
            + new SimpleDateFormat("dd/MM/yyyy", new Locale("ar")).format(new Date());
        c.drawText(footer, PAGE_W / 2f, PAGE_H - 22, p);
    }

    private static void drawField(Canvas c, Paint p,
                                  String label, String value,
                                  int x, int y) {
        // تسمية
        p.setColor(C_GRAY);
        p.setTextSize(13f);
        p.setTypeface(Typeface.DEFAULT);
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText(label, PAGE_W - 30, y - 4, p);

        // قيمة
        p.setColor(C_BLACK);
        p.setTextSize(17f);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextAlign(Paint.Align.RIGHT);
        String val = (value != null && !value.isEmpty()) ? value : "—";
        if (val.length() > 45) val = val.substring(0, 45) + "…";
        c.drawText(val, PAGE_W - 30, y + 18, p);

        // خط أسفل الحقل
        p.setColor(C_LGRAY);
        p.setStrokeWidth(1f);
        c.drawLine(x, y + 26, PAGE_W - 30, y + 26, p);
    }

    private static void sectionTitle(Canvas c, Paint p, String title, int y) {
        p.setColor(C_DARK);
        p.setTextSize(17f);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText(title, PAGE_W - 30, y, p);
    }
}
