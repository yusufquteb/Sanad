package com.missingpersons.app.utils;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

/**
 * AccessibilityUtils — أدوات إمكانية الوصول
 *
 * [المرحلة 7.2] Accessibility
 *
 * يشمل:
 *  ✅ ضمان touch target 48dp لكل العناصر القابلة للضغط
 *  ✅ إضافة contentDescription للصور
 *  ✅ دعم TalkBack للمعلومات الديناميكية
 *  ✅ وصف البطاقات والحالات للقارئات
 *  ✅ إعلانات إمكانية الوصول للتغييرات
 */
public final class AccessibilityUtils {

    private AccessibilityUtils() {}

    // ════════════════════════════════════════════════════════
    //  Touch Target 48dp
    // ════════════════════════════════════════════════════════

    private static final int MIN_TOUCH_DP = 48;

    /**
     * يوسّع منطقة الضغط لعنصر صغير حتى 48dp
     * استدعِه في onViewCreated/onBindViewHolder على الأزرار الصغيرة
     *
     * Usage:
     *   AccessibilityUtils.expandTouchTarget(parent, btnShare);
     */
    public static void expandTouchTarget(final View parent, final View child) {
        if (parent == null || child == null) return;
        parent.post(() -> {
            try {
                Context ctx    = parent.getContext();
                float density  = ctx.getResources().getDisplayMetrics().density;
                int   minPx    = (int) (MIN_TOUCH_DP * density);

                Rect  bounds   = new Rect();
                child.getHitRect(bounds);

                int width  = bounds.width();
                int height = bounds.height();

                if (width < minPx) {
                    int expand = (minPx - width) / 2;
                    bounds.left  -= expand;
                    bounds.right += expand;
                }
                if (height < minPx) {
                    int expand = (minPx - height) / 2;
                    bounds.top    -= expand;
                    bounds.bottom += expand;
                }

                parent.setTouchDelegate(new TouchDelegate(bounds, child));
            } catch (Exception ignored) {}
        });
    }

    // ════════════════════════════════════════════════════════
    //  contentDescription للصور
    // ════════════════════════════════════════════════════════

    /**
     * يضبط contentDescription لصورة شخص مفقود
     * @param iv        ImageView الهدف
     * @param name      اسم الشخص
     * @param age       العمر (0 = غير معروف)
     * @param location  الموقع
     */
    public static void describePersonPhoto(ImageView iv,
                                           String name,
                                           int age,
                                           String location) {
        if (iv == null) return;
        StringBuilder sb = new StringBuilder("صورة ");
        sb.append(name != null && !name.isEmpty() ? name : "شخص مجهول");
        if (age > 0) sb.append("، العمر ").append(age).append(" سنة");
        if (location != null && !location.isEmpty()) sb.append("، من ").append(location);
        iv.setContentDescription(sb.toString());
        // منع TalkBack من قراءة "صورة" مرتين
        ViewCompat.setAccessibilityDelegate(iv, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(
                View host, AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(ImageView.class.getName());
            }
        });
    }

    // ════════════════════════════════════════════════════════
    //  وصف البطاقة الكاملة للقارئ
    // ════════════════════════════════════════════════════════

    /**
     * يجعل بطاقة الحالة تُقرأ بشكل صحيح من TalkBack
     * يدمج كل البيانات في contentDescription واحدة للبطاقة الأم
     */
    public static void describeCaseCard(View card,
                                        String name,
                                        int age,
                                        String gender,
                                        String location,
                                        String status,
                                        String timeAgo) {
        if (card == null) return;

        StringBuilder desc = new StringBuilder("حالة مفقود: ");
        desc.append(name != null ? name : "مجهول");
        if (age > 0) desc.append("، عمره ").append(age).append(" سنة");
        if (gender != null && !gender.isEmpty()) desc.append("، ").append(gender);
        if (location != null && !location.isEmpty()) desc.append("، من ").append(location);
        desc.append("، الحالة: ").append(statusLabel(status));
        if (timeAgo != null) desc.append("، ").append(timeAgo);
        desc.append(". اضغط للتفاصيل.");

        card.setContentDescription(desc.toString());
        // منع الأبناء من الإزعاج — كل شيء في البطاقة الأم
        ViewCompat.setImportantForAccessibility(card,
            ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
        setChildrenImportance(card, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    private static String statusLabel(String status) {
        if (status == null) return "غير معروف";
        switch (status) {
            case "pending":  return "قيد المراجعة";
            case "approved": return "نشط";
            case "resolved": return "تم العثور عليه";
            case "matched":  return "تطابق محتمل";
            default:         return status;
        }
    }

    // ════════════════════════════════════════════════════════
    //  TalkBack Announcements
    // ════════════════════════════════════════════════════════

    /**
     * يُعلن عن تغيير هام لمستخدمي TalkBack
     * مثال: "تم رفع بلاغك بنجاح"، "3 نتائج وُجدت"
     */
    public static void announce(View anyView, String message) {
        if (anyView == null || message == null) return;
        anyView.announceForAccessibility(message);
    }

    /**
     * يعلن عن نتائج البحث
     */
    public static void announceSearchResults(View view, int count, String query) {
        if (view == null) return;
        String msg = count == 0
            ? "لا توجد نتائج لـ " + query
            : "وُجد " + count + " نتيجة لـ " + query;
        announce(view, msg);
    }

    /**
     * يعلن عن تحميل القائمة
     */
    public static void announceListLoaded(View view, int count, String type) {
        if (view == null) return;
        String msg = count == 0
            ? "لا توجد " + type
            : "تم تحميل " + count + " " + type;
        announce(view, msg);
    }

    // ════════════════════════════════════════════════════════
    //  حجم الخط والتباين
    // ════════════════════════════════════════════════════════

    /**
     * يُمكّن scaling الخط تلقائياً (sp وليس dp) لمحترمي إعدادات إمكانية الوصول
     * يُستدعى مرة واحدة في MyApplication.onCreate()
     */
    public static void applyFontScaleRespect(Context ctx) {
        // في Android لا نحتاج تدخل مباشر إذا استخدمنا sp بدلاً من dp
        // هذه الدالة تُذكّر: دائماً استخدم sp للنصوص وdp للأبعاد
        // للتحقق برمجياً:
        float scale = ctx.getResources().getConfiguration().fontScale;
        if (scale > 1.3f) {
            // المستخدم يستخدم خطاً كبيراً جداً — يمكن تقليل padding
            android.util.Log.d("AccessibilityUtils",
                "fontScale=" + scale + " — تأكد أن كل النصوص بـ sp");
        }
    }

    // ════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════

    private static void setChildrenImportance(View view, int importance) {
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            ViewCompat.setImportantForAccessibility(
                group.getChildAt(i), importance);
        }
    }

    /**
     * يضبط كل الـ ImageViews في ViewGroup كزخرفة (تُتجاهل من TalkBack)
     * يُستخدم للأيقونات الزخرفية
     */
    public static void markDecorative(ImageView... views) {
        for (ImageView iv : views) {
            if (iv != null) {
                iv.setContentDescription(null);
                ViewCompat.setImportantForAccessibility(
                    iv, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);
            }
        }
    }

    /**
     * يضبط عنوان Section لمجموعة عناصر (مجموعة إمكانية الوصول)
     */
    public static void setSectionHeading(TextView tv) {
        if (tv == null) return;
        ViewCompat.setAccessibilityDelegate(tv, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(
                View host, AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setHeading(true);
            }
        });
    }

    /**
     * يُضيف hint للـ AccessibilityNodeInfo (مفيد للحقول)
     */
    public static void setHint(View view, String hint) {
        if (view == null || hint == null) return;
        ViewCompat.setAccessibilityDelegate(view, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(
                View host, AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setHintText(hint);
            }
        });
    }
}
