package com.missingpersons.app.utils;

import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.widget.ImageView;
import android.content.Context;

/**
 * CaseCategory — تصنيفات حالات المفقودين
 */
public class CaseCategory {

    // ─── أنواع الحالات ───
    public static final String MISSING_CHILD       = "missing_child";       // طفل مفقود
    public static final String ALZHEIMER_ELDERLY   = "alzheimer_elderly";   // كبير سن / ألزهايمر
    public static final String ORPHAN_UNKNOWN      = "orphan_unknown";      // مجهول النسب / أطفال ملاجئ
    public static final String ACCIDENT_AMNESIA    = "accident_amnesia";    // حادث / فاقد ذاكرة
    public static final String UNKNOWN_DECEASED    = "unknown_deceased";    // متوفى مجهول الهوية
    public static final String OTHER               = "other";              // أخرى

    /** أسماء الفئات بالعربية */
    public static final String[] LABELS_AR = {
        "👶 طفل مفقود / مخطوف",
        "🧓 كبير سن / ألزهايمر",
        "🏠 مجهول النسب / أطفال ملاجئ",
        "🚗 حادث / فاقد ذاكرة",
        "⚰️ متوفى مجهول الهوية",
        "📋 أخرى"
    };

    /** أسماء الفئات بالإنجليزية */
    public static final String[] LABELS_EN = {
        "👶 Missing / Kidnapped Child",
        "🧓 Elderly / Alzheimer",
        "🏠 Unknown Lineage / Orphan",
        "🚗 Accident / Amnesia",
        "⚰️ Unknown Deceased",
        "📋 Other"
    };

    /** المفاتيح */
    public static final String[] KEYS = {
        MISSING_CHILD, ALZHEIMER_ELDERLY, ORPHAN_UNKNOWN,
        ACCIDENT_AMNESIA, UNKNOWN_DECEASED, OTHER
    };

    /** هل هذه الحالة متوفى؟ */
    public static boolean isDeceased(String category) {
        return UNKNOWN_DECEASED.equals(category);
    }

    /** هل هذه حالة عاجلة (طفل)؟ */
    public static boolean isUrgent(String category) {
        return MISSING_CHILD.equals(category);
    }

    /** الحصول على اسم الفئة بالعربية */
    public static String getLabelAr(String key) {
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equals(key)) return LABELS_AR[i];
        }
        return LABELS_AR[LABELS_AR.length - 1];
    }

    /**
     * عمل blur للصورة (للمتوفين)
     * يستخدم StackBlur بدون RenderScript للتوافق
     */
    public static Bitmap blurBitmap(Context context, Bitmap original, float radius) {
        if (original == null) return null;
        try {
            Bitmap blurred = original.copy(Bitmap.Config.ARGB_8888, true);
            // Simple box blur
            int r = (int) Math.min(radius, 25);
            if (r < 1) r = 1;
            int w = blurred.getWidth();
            int h = blurred.getHeight();
            int[] pixels = new int[w * h];
            blurred.getPixels(pixels, 0, w, 0, 0, w, h);

            for (int pass = 0; pass < 2; pass++) {
                // Horizontal pass
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int rr = 0, gg = 0, bb = 0, count = 0;
                        for (int dx = -r; dx <= r; dx++) {
                            int nx = x + dx;
                            if (nx >= 0 && nx < w) {
                                int p = pixels[y * w + nx];
                                rr += (p >> 16) & 0xFF;
                                gg += (p >> 8) & 0xFF;
                                bb += p & 0xFF;
                                count++;
                            }
                        }
                        pixels[y * w + x] = 0xFF000000 | ((rr / count) << 16) | ((gg / count) << 8) | (bb / count);
                    }
                }
            }

            blurred.setPixels(pixels, 0, w, 0, 0, w, h);
            return blurred;
        } catch (Exception e) {
            return original;
        }
    }

    /**
     * تحميل صورة مع blur إذا كانت حالة متوفى
     * يضغط المستخدم على الصورة لعرضها بدون blur
     */
    public static void setupBlurredImage(ImageView imageView, Bitmap original,
                                          String category, Context context) {
        if (isDeceased(category) && original != null) {
            // صورة مطموسة
            Bitmap small = Bitmap.createScaledBitmap(original,
                original.getWidth() / 4, original.getHeight() / 4, true);
            Bitmap blurred = blurBitmap(context, small, 20);
            Bitmap scaled = Bitmap.createScaledBitmap(blurred,
                original.getWidth(), original.getHeight(), true);
            imageView.setImageBitmap(scaled);

            // ضغط لعرض الأصلي
            imageView.setOnClickListener(v -> {
                new android.app.AlertDialog.Builder(context)
                    .setTitle("⚠️ تحذير")
                    .setMessage("هذه صورة متوفى مجهول الهوية.\nهل تريد عرض الصورة؟")
                    .setPositiveButton("عرض", (d, w) -> {
                        imageView.setImageBitmap(original);
                        imageView.setOnClickListener(null);
                    })
                    .setNegativeButton("إلغاء", null)
                    .show();
            });
        } else {
            imageView.setImageBitmap(original);
        }
    }
}
