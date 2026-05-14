package com.missingpersons.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.util.Log;

import java.io.InputStream;

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

            if (result.getConfig() != Bitmap.Config.ARGB_8888) {
                result = result.copy(Bitmap.Config.ARGB_8888, false);
            }

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
            return raw;
        }
    }

    /**
     * تحميل وتجهيز الصورة من Uri (للاستخدام من WorkManager).
     *
     * @param context السياق للوصول إلى ContentResolver
     * @param uri     رابط الصورة
     * @return صورة مجهّزة، أو null عند الفشل
     */
    public static Bitmap preprocessFromUri(Context context, Uri uri) {
        if (context == null || uri == null) return null;
        try {
            InputStream is = context.getContentResolver().openInputStream(uri);
            if (is == null) return null;
            Bitmap raw = BitmapFactory.decodeStream(is);
            is.close();
            return preprocessBitmap(raw);
        } catch (Exception e) {
            Log.e(TAG, "preprocessFromUri: " + e.getMessage());
            return null;
        }
    }

    /**
     * تحميل وتجهيز الصورة من مسار ملف.
     *
     * @param filePath المسار الكامل للملف
     * @return صورة مجهّزة، أو null عند الفشل
     */
    public static Bitmap preprocessFromFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) return null;
        try {
            Bitmap raw = BitmapFactory.decodeFile(filePath);
            return preprocessBitmap(raw);
        } catch (Exception e) {
            Log.e(TAG, "preprocessFromFile: " + e.getMessage());
            return null;
        }
    }

    /**
     * التحقق من صلاحية الصورة (الحجم الأدنى).
     *
     * @return null إذا كانت مقبولة، أو رسالة الخطأ
     */
    public static String validateImage(Bitmap bitmap) {
        if (bitmap == null) return AiError.NULL_BITMAP;
        if (bitmap.getWidth() < 48 || bitmap.getHeight() < 48) return AiError.FACE_TOO_SMALL;
        return null;
    }
}
