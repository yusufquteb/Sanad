package com.missingpersons.app.utils;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Log;

/**
 * ImagePreprocessor — تجهيز الصورة قبل إرسالها لكشف الوجه واستخراج الـ embedding.
 *
 * الخطوات:
 *   1. تصغير الصورة إذا تجاوزت MAX_DIM (للأداء)
 *   2. التأكد من أن الصورة بصيغة ARGB_8888
 */
public final class ImagePreprocessor {

    private static final String TAG     = "ImagePreprocessor";
    private static final int    MAX_DIM = 1024;

    private ImagePreprocessor() {}

    /**
     * تجهيز الصورة لمسار كشف الوجه واستخراج الـ embedding.
     *
     * @param raw الصورة الخام من الكاميرا أو المعرض
     * @return صورة مجهّزة (مصغّرة + ARGB_8888)، أو null إذا كانت المدخلات null
     */
    public static Bitmap preprocessBitmap(Bitmap raw) {
        if (raw == null) return null;

        try {
            Bitmap result = raw;

            // تحويل للصيغة المطلوبة إذا لزم
            if (result.getConfig() != Bitmap.Config.ARGB_8888) {
                result = result.copy(Bitmap.Config.ARGB_8888, false);
            }

            // تصغير الصورة الكبيرة للحفاظ على الذاكرة والأداء
            int w = result.getWidth(), h = result.getHeight();
            if (w > MAX_DIM || h > MAX_DIM) {
                float scale = (float) MAX_DIM / Math.max(w, h);
                Matrix m = new Matrix();
                m.postScale(scale, scale);
                result = Bitmap.createBitmap(result, 0, 0, w, h, m, true);
                Log.d(TAG, "تصغير الصورة: " + w + "x" + h
                    + " → " + result.getWidth() + "x" + result.getHeight());
            }

            return result;
        } catch (Exception e) {
            Log.e(TAG, "preprocessBitmap: " + e.getMessage());
            return raw; // fallback آمن
        }
    }
}
