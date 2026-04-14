package com.missingpersons.app.utils;

import android.content.Context;
import android.widget.ImageView;
import coil.Coil;
import coil.ImageLoader;
import coil.request.ImageRequest;
import coil.transform.RoundedCornersTransformation;
import coil.transform.CircleCropTransformation;

/**
 * CoilImageLoader — واجهة بسيطة لـ Coil (بديل Glide)
 * تُستخدم بدلاً من Glide في كامل التطبيق
 *
 * مميزات Coil على Glide:
 * ✅ أخف وزناً (أقل 40% من Glide)
 * ✅ Kotlin-first مع Java interop
 * ✅ دعم تلقائي لـ CoroutineScope
 * ✅ أسرع في التحميل الأول
 * ✅ إدارة ذاكرة أفضل
 */
public class CoilImageLoader {

    /**
     * تحميل صورة من URL إلى ImageView
     */
    public static void load(Context context, Object data, ImageView imageView) {
        if (context == null || imageView == null) return;
        ImageRequest request = new ImageRequest.Builder(context)
                .data(data)
                .target(imageView)
                .crossfade(300)
                .build();
        Coil.imageLoader(context).enqueue(request);
    }

    /**
     * تحميل صورة مع placeholder
     */
    public static void load(Context context, Object data, ImageView imageView, int placeholder) {
        if (context == null || imageView == null) return;
        ImageRequest request = new ImageRequest.Builder(context)
                .data(data)
                .target(imageView)
                .placeholder(placeholder)
                .error(placeholder)
                .crossfade(300)
                .build();
        Coil.imageLoader(context).enqueue(request);
    }

    /**
     * تحميل صورة دائرية (بديل CircularTransformation)
     * مع null-safe check على الـ URL + error fallback
     */
    public static void loadCircle(Context context, Object data, ImageView imageView, int placeholder) {
        if (context == null || imageView == null) return;
        // إذا كان الـ URL فارغاً أو null → أظهر الـ placeholder فوراً
        if (data == null || (data instanceof String && ((String) data).trim().isEmpty())) {
            imageView.setImageResource(placeholder);
            return;
        }
        ImageRequest request = new ImageRequest.Builder(context)
                .data(data)
                .target(imageView)
                .placeholder(placeholder)
                .error(placeholder)
                .crossfade(300)
                .transformations(new CircleCropTransformation())
                .build();
        Coil.imageLoader(context).enqueue(request);
    }

    /**
     * تحميل صورة بزوايا مدورة
     */
    public static void loadRounded(Context context, Object data, ImageView imageView,
                                    int placeholder, float cornerRadius) {
        if (context == null || imageView == null) return;
        ImageRequest request = new ImageRequest.Builder(context)
                .data(data)
                .target(imageView)
                .placeholder(placeholder)
                .error(placeholder)
                .crossfade(300)
                .transformations(new RoundedCornersTransformation(cornerRadius))
                .build();
        Coil.imageLoader(context).enqueue(request);
    }

    /**
     * مسح ذاكرة التخزين المؤقت
     */
    public static void clearCache(Context context) {
        if (context == null) return;
        coil.memory.MemoryCache memCache = Coil.imageLoader(context).getMemoryCache();
        if (memCache != null) memCache.clear();
    }
}
