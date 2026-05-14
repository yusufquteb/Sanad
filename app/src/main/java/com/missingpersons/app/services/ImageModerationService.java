package com.missingpersons.app.services;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

/**
 * ImageModerationService — مراجعة الصور قبل النشر.
 *
 * يُحلّل الصورة ويُرجع ModerationReport بناءً على:
 *   - حجم الصورة (صغيرة جداً → PENDING)
 *   - السطوع الشديد أو الداكن الشديد → PENDING
 *   - صور طبيعية → APPROVED
 *
 * ملاحظة: هذه نسخة محلية بسيطة. في الإنتاج يمكن استبدالها
 * باستدعاء Cloud Function أو Cloud Vision API.
 */
public final class ImageModerationService {

    private static final String TAG = "ImageModerationService";

    private static final int   MIN_DIM        = 64;
    private static final float DARK_THRESHOLD = 20f;
    private static final float OVER_THRESHOLD = 245f;

    private ImageModerationService() {}

    // ── Result types ──────────────────────────────────────────────────────

    public enum ModerationResult {
        APPROVED,
        PENDING,
        REJECTED
    }

    public static class ModerationReport {
        public final ModerationResult result;
        public final String           reason;

        public ModerationReport(ModerationResult result, String reason) {
            this.result = result;
            this.reason = reason;
        }

        @Override
        public String toString() {
            return "ModerationReport{result=" + result + " reason=" + reason + "}";
        }
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * يُحلّل الصورة ويُرجع تقرير المراجعة.
     * آمن للاستدعاء من Background Thread.
     *
     * @param ctx context (محجوز للاستخدام المستقبلي)
     * @param bmp الصورة المراد مراجعتها
     * @return ModerationReport لا يكون null أبداً
     */
    public static ModerationReport moderate(Context ctx, Bitmap bmp) {
        if (bmp == null) {
            return new ModerationReport(ModerationResult.PENDING, "صورة فارغة");
        }

        try {
            // 1. تحقق الحجم
            if (bmp.getWidth() < MIN_DIM || bmp.getHeight() < MIN_DIM) {
                Log.w(TAG, "صورة صغيرة جداً: " + bmp.getWidth() + "x" + bmp.getHeight());
                return new ModerationReport(ModerationResult.PENDING,
                    "الصورة صغيرة جداً — تحتاج مراجعة بشرية");
            }

            float brightness = computeBrightness(bmp);

            // 2. صورة داكنة جداً
            if (brightness < DARK_THRESHOLD) {
                Log.w(TAG, "صورة داكنة جداً: brightness=" + brightness);
                return new ModerationReport(ModerationResult.PENDING,
                    "الصورة داكنة — تحتاج مراجعة بشرية");
            }

            // 3. صورة مبيضة جداً (overexposed)
            if (brightness > OVER_THRESHOLD) {
                Log.w(TAG, "صورة مبيضة جداً: brightness=" + brightness);
                return new ModerationReport(ModerationResult.PENDING,
                    "الصورة مبيضة — تحتاج مراجعة بشرية");
            }

            Log.d(TAG, "✅ صورة مقبولة: brightness=" + String.format("%.1f", brightness));
            return new ModerationReport(ModerationResult.APPROVED, null);

        } catch (Exception e) {
            Log.e(TAG, "moderate: " + e.getMessage());
            return new ModerationReport(ModerationResult.PENDING,
                "خطأ أثناء المراجعة — سيُراجَع يدوياً");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static float computeBrightness(Bitmap bmp) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int step = Math.max(1, Math.min(w, h) / 32);
        long sum = 0;
        int  count = 0;
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int px = bmp.getPixel(x, y);
                int r = (px >> 16) & 0xFF;
                int g = (px >> 8)  & 0xFF;
                int b =  px        & 0xFF;
                sum += (int)(0.299f * r + 0.587f * g + 0.114f * b);
                count++;
            }
        }
        return count > 0 ? (float) sum / count : 128f;
    }
}
