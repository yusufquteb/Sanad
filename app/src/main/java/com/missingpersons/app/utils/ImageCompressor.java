package com.missingpersons.app.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import java.io.*;

public class ImageCompressor {

    private static final int MAX_DIMENSION = 800;
    private static final long MAX_SIZE_BYTES = 200 * 1024; // 200KB
    private static final int INITIAL_QUALITY = 80;

    /** WebP format - 60% أصغر من JPEG بنفس الجودة */
    public static final boolean USE_WEBP = android.os.Build.VERSION.SDK_INT >= 30;

    public interface CompressCallback {
        void onCompressed(File compressedFile);
        void onError(String error);
    }

    public static void compress(Context context, File originalFile, CompressCallback callback) {
        new Thread(() -> {
            try {
                Bitmap bitmap = decodeSampledBitmap(originalFile.getAbsolutePath());
                bitmap = fixImageRotation(bitmap, originalFile.getAbsolutePath());
                bitmap = resizeBitmap(bitmap, MAX_DIMENSION);

                // اختيار الامتداد حسب الدعم
                String ext = USE_WEBP ? ".webp" : ".jpg";
                Bitmap.CompressFormat format = USE_WEBP
                    ? Bitmap.CompressFormat.WEBP_LOSSY
                    : Bitmap.CompressFormat.JPEG;

                File compressedFile = new File(context.getCacheDir(),
                        "compressed_" + System.currentTimeMillis() + ext);

                int quality = INITIAL_QUALITY;
                do {
                    FileOutputStream fos = new FileOutputStream(compressedFile);
                    bitmap.compress(format, quality, fos);
                    fos.flush();
                    fos.close();
                    quality -= 10;
                } while (compressedFile.length() > MAX_SIZE_BYTES && quality > 10);

                final Bitmap finalBitmap = bitmap;
                final File finalFile = compressedFile;

                if (context instanceof Activity) {
                    ((Activity) context).runOnUiThread(() -> {
                        finalBitmap.recycle();
                        callback.onCompressed(finalFile);
                    });
                }

            } catch (IOException e) {
                if (context instanceof Activity) {
                    ((Activity) context).runOnUiThread(() ->
                            callback.onError("خطأ في ضغط الصورة: " + e.getMessage()));
                }
            }
        }).start();
    }

    /**
     * ضغط سريع sync — يعيد ملف مضغوط
     */
    public static File compressSync(Context context, File original) {
        try {
            Bitmap bitmap = decodeSampledBitmap(original.getAbsolutePath());
            if (bitmap == null) return original;
            bitmap = fixImageRotation(bitmap, original.getAbsolutePath());
            bitmap = resizeBitmap(bitmap, MAX_DIMENSION);

            String ext = USE_WEBP ? ".webp" : ".jpg";
            Bitmap.CompressFormat format = USE_WEBP
                ? Bitmap.CompressFormat.WEBP_LOSSY
                : Bitmap.CompressFormat.JPEG;

            File out = new File(context.getCacheDir(),
                "comp_" + System.currentTimeMillis() + ext);
            FileOutputStream fos = new FileOutputStream(out);
            bitmap.compress(format, 75, fos);
            fos.close();
            bitmap.recycle();
            return out;
        } catch (Exception e) {
            return original;
        }
    }

    private static Bitmap decodeSampledBitmap(String filePath) {
        // قراءة الأبعاد أولاً بدون تحميل الصورة كاملة (توفير RAM)
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);

        // حساب inSampleSize
        options.inSampleSize = calculateInSampleSize(options, MAX_DIMENSION, MAX_DIMENSION);
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565; // توفير الذاكرة

        return BitmapFactory.decodeFile(filePath, options);
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight &&
                    (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private static Bitmap resizeBitmap(Bitmap bitmap, int maxDimension) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if (width <= maxDimension && height <= maxDimension) return bitmap;

        float ratio = Math.min((float) maxDimension / width, (float) maxDimension / height);
        int newWidth = Math.round(width * ratio);
        int newHeight = Math.round(height * ratio);

        Bitmap resized = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
        bitmap.recycle();
        return resized;
    }

    /**
     * fixImageRotation — يصحح اتجاه الصورة بناءً على بيانات EXIF
     * يدعم: rotate 90/180/270 + flip horizontal/vertical + transpose/transverse
     */
    private static Bitmap fixImageRotation(Bitmap bitmap, String imagePath) {
        try {
            ExifInterface exif = new ExifInterface(imagePath);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);
                    break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    matrix.preScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    matrix.preScale(1, -1);
                    break;
                case ExifInterface.ORIENTATION_TRANSPOSE:
                    matrix.postRotate(90);
                    matrix.preScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_TRANSVERSE:
                    matrix.postRotate(-90);
                    matrix.preScale(-1, 1);
                    break;
                default:
                    return bitmap; // NORMAL — no change
            }

            Bitmap rotated = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) bitmap.recycle();
            return rotated;

        } catch (IOException e) {
            return bitmap; // fallback — return original
        }
    }

    // ضغط سريع من Uri مباشرة
    public static Bitmap getCompressedBitmapFromPath(String path) {
        try {
            Bitmap bitmap = decodeSampledBitmap(path);
            bitmap = fixImageRotation(bitmap, path);
            return resizeBitmap(bitmap, MAX_DIMENSION);
        } catch (Exception e) {
            return null;
        }
    }
}
