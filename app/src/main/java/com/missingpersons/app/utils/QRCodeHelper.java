package com.missingpersons.app.utils;

import android.graphics.Bitmap;
import android.graphics.Color;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * QRCodeHelper — توليد QR Code لكل بلاغ
 */
public class QRCodeHelper {

    private static final int QR_SIZE = 512;

    /**
     * توليد QR Code من reportId
     * يمكن مسحه بأي تطبيق QR للوصول لتفاصيل البلاغ
     */
    public static Bitmap generateQR(String reportId) {
        return generateQR(reportId, QR_SIZE);
    }

    public static Bitmap generateQR(String content, int size) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 2);

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints);

            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * توليد deep link للبلاغ
     */
    public static String getReportLink(String reportId) {
        return "https://missingpersons.app/report/" + reportId;
    }

    /**
     * توليد QR بـ deep link
     */
    public static Bitmap generateReportQR(String reportId) {
        return generateQR(getReportLink(reportId));
    }
}
