package com.missingpersons.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Log;

import java.io.InputStream;

/**
 * ImagePreprocessor — تجهيز الصورة قبل إرسالها لكشف الوجه واستخراج الـ embedding.
 *
 * الخطوات:
 *   1. تصحيح دوران EXIF (كثير من الصور — خصوصاً القادمة من واتساب أو معارض
 *      لا تُعيد ترميز البكسل، بل تكتفي بحقل EXIF Orientation. صورة بهذا
 *      الحقل ولم تُصحَّح تصل مقلوبة/مائلة 90° لكاشف الوجه فيفشل اكتشاف
 *      الوجه من الأساس، أو — إذا نجح جزئياً — يُنتج قصّاً/محاذاة خاطئة)
 *   2. تصغير الصورة إذا تجاوزت MAX_DIM (للأداء)
 *   3. التأكد من أن الصورة بصيغة ARGB_8888
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
            raw = fixExifRotation(raw, context, uri);
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
            raw = fixExifRotation(raw, filePath);
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

    // ════════════════════════════════════════════════════════
    //  EXIF Rotation
    // ════════════════════════════════════════════════════════

    /**
     * تصحيح دوران/انعكاس الصورة حسب حقل EXIF Orientation — لصور مُختارة
     * من المعرض (content:// Uri).
     *
     * @param bitmap الصورة المفكوكة من decodeStream (بدون تصحيح EXIF بعد)
     * @param context للوصول إلى ContentResolver لإعادة قراءة EXIF من الـ Uri
     * @param uri     رابط الصورة الأصلي
     * @return صورة مُصحَّحة الاتجاه، أو الأصل إن تعذّرت القراءة
     */
    public static Bitmap fixExifRotation(Bitmap bitmap, Context context, Uri uri) {
        if (bitmap == null || context == null || uri == null) return bitmap;
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) return bitmap;
            return applyExifOrientation(bitmap, new ExifInterface(is));
        } catch (Exception e) {
            Log.w(TAG, "fixExifRotation(Uri): " + e.getMessage());
            return bitmap;
        }
    }

    /**
     * تصحيح دوران/انعكاس الصورة حسب حقل EXIF Orientation — لصور مُلتقطة
     * بالكاميرا ومحفوظة كملف مؤقت.
     *
     * @param bitmap   الصورة المفكوكة من decodeFile (بدون تصحيح EXIF بعد)
     * @param filePath مسار الملف نفسه الذي فُكّت منه الصورة
     * @return صورة مُصحَّحة الاتجاه، أو الأصل إن تعذّرت القراءة
     */
    public static Bitmap fixExifRotation(Bitmap bitmap, String filePath) {
        if (bitmap == null || filePath == null || filePath.isEmpty()) return bitmap;
        try {
            return applyExifOrientation(bitmap, new ExifInterface(filePath));
        } catch (Exception e) {
            Log.w(TAG, "fixExifRotation(path): " + e.getMessage());
            return bitmap;
        }
    }

    private static Bitmap applyExifOrientation(Bitmap bitmap, ExifInterface exif) {
        int orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:  matrix.postRotate(90);  break;
            case ExifInterface.ORIENTATION_ROTATE_180: matrix.postRotate(180); break;
            case ExifInterface.ORIENTATION_ROTATE_270: matrix.postRotate(270); break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.preScale(-1, 1); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:   matrix.preScale(1, -1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.postRotate(90); matrix.preScale(-1, 1); break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.postRotate(270); matrix.preScale(-1, 1); break;
            default:
                return bitmap; // NORMAL/UNDEFINED — لا تغيير
        }

        try {
            Bitmap rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) bitmap.recycle();
            return rotated;
        } catch (Exception e) {
            Log.w(TAG, "applyExifOrientation: " + e.getMessage());
            return bitmap;
        }
    }
}
