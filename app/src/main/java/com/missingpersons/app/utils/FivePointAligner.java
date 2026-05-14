package com.missingpersons.app.utils;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.util.Log;

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceLandmark;

/**
 * FivePointAligner — محاذاة الوجه بخمس نقاط مرجعية.
 *
 * يُحوِّل الوجه المكتشف إلى canonical face بحجم 112×112 باستخدام
 * Similarity Transform (scale + rotate + translate, no shear).
 *
 * النقاط المرجعية: left eye, right eye, nose tip, left mouth, right mouth.
 * مأخوذة من: https://github.com/mk-minchul/AdaFace
 *
 * Fallback: إذا نقصت أي landmark → يرجع للـ 2-point (عيون فقط).
 */
public final class FivePointAligner {

    private static final String TAG = "FivePointAligner";

    /** نقاط المرجع الثابتة لـ canonical face 112×112 */
    private static final float[][] REFERENCE_POINTS = {
        {38.2946f, 51.6963f},  // left eye
        {73.5318f, 51.5014f},  // right eye
        {56.0252f, 71.7366f},  // nose tip
        {41.5493f, 92.3655f},  // left mouth
        {70.7299f, 92.2041f}   // right mouth
    };

    public FivePointAligner() {}

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * محاذاة الوجه باستخدام ML Kit Face landmarks.
     *
     * @param src     الصورة الأصلية
     * @param face    نتيجة كشف ML Kit (يجب أن يكون LANDMARK_MODE_ALL)
     * @return الصورة المحاذاة، أو الصورة الأصلية عند الفشل
     */
    public Bitmap align(Bitmap src, Face face) {
        if (src == null || face == null) return src;
        try {
            FaceLandmark leftEye    = face.getLandmark(FaceLandmark.LEFT_EYE);
            FaceLandmark rightEye   = face.getLandmark(FaceLandmark.RIGHT_EYE);
            FaceLandmark noseBase   = face.getLandmark(FaceLandmark.NOSE_BASE);
            FaceLandmark leftMouth  = face.getLandmark(FaceLandmark.MOUTH_LEFT);
            FaceLandmark rightMouth = face.getLandmark(FaceLandmark.MOUTH_RIGHT);

            if (leftEye == null || rightEye == null || noseBase == null
                    || leftMouth == null || rightMouth == null) {
                Log.d(TAG, "⚠️ نقاط ناقصة — fallback إلى 2-point alignment");
                return twoPointFallback(src, face);
            }

            float[][] srcPoints = {
                {leftEye.getPosition().x,    leftEye.getPosition().y},
                {rightEye.getPosition().x,   rightEye.getPosition().y},
                {noseBase.getPosition().x,   noseBase.getPosition().y},
                {leftMouth.getPosition().x,  leftMouth.getPosition().y},
                {rightMouth.getPosition().x, rightMouth.getPosition().y}
            };

            Matrix m = computeSimilarityTransform(srcPoints, REFERENCE_POINTS);
            Bitmap aligned = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
            Log.d(TAG, "✅ 5-point alignment applied");
            return aligned;

        } catch (Exception e) {
            Log.w(TAG, "⚠️ خطأ في alignment — fallback: " + e.getMessage());
            return src;
        }
    }

    // ── Similarity Transform ─────────────────────────────────────────────

    /**
     * يحسب Similarity Transform (scale, rotate, translate بدون shear)
     * من 5 نقاط مصدر إلى 5 نقاط مرجعية.
     *
     * الخوارزمية: Least-Squares Similarity Transform من Umeyama (1991)
     */
    private Matrix computeSimilarityTransform(float[][] src, float[][] dst) {
        int n = src.length;
        float srcCx = 0, srcCy = 0, dstCx = 0, dstCy = 0;
        for (int i = 0; i < n; i++) {
            srcCx += src[i][0]; srcCy += src[i][1];
            dstCx += dst[i][0]; dstCy += dst[i][1];
        }
        srcCx /= n; srcCy /= n;
        dstCx /= n; dstCy /= n;

        float srcVar = 0, dot = 0, cross = 0;
        for (int i = 0; i < n; i++) {
            float sx = src[i][0] - srcCx, sy = src[i][1] - srcCy;
            float dx = dst[i][0] - dstCx, dy = dst[i][1] - dstCy;
            srcVar += sx * sx + sy * sy;
            dot    += sx * dx + sy * dy;
            cross  += sx * dy - sy * dx;
        }

        if (srcVar < 1e-6f) return new Matrix(); // fallback

        float scale  = dot / srcVar;
        double angle = Math.atan2(cross, dot);
        float  cosA  = (float) Math.cos(angle);
        float  sinA  = (float) Math.sin(angle);

        float tx = dstCx - scale * (srcCx * cosA - srcCy * sinA);
        float ty = dstCy - scale * (srcCx * sinA + srcCy * cosA);

        Matrix matrix = new Matrix();
        matrix.setValues(new float[]{
            scale * cosA, -scale * sinA, tx,
            scale * sinA,  scale * cosA, ty,
            0f, 0f, 1f
        });
        return matrix;
    }

    // ── Fallback ─────────────────────────────────────────────────────────

    /** محاذاة بسيطة من نقطتين (عيون) عندما تنقص المعالم */
    private Bitmap twoPointFallback(Bitmap src, Face face) {
        try {
            FaceLandmark l = face.getLandmark(FaceLandmark.LEFT_EYE);
            FaceLandmark r = face.getLandmark(FaceLandmark.RIGHT_EYE);
            if (l == null || r == null) return src;

            PointF lp = l.getPosition(), rp = r.getPosition();
            float angle = (float) Math.toDegrees(
                Math.atan2(rp.y - lp.y, rp.x - lp.x));
            if (Math.abs(angle) < 2f) return src;

            Matrix m = new Matrix();
            m.postRotate(-angle, src.getWidth() / 2f, src.getHeight() / 2f);
            return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
        } catch (Exception e) {
            return src;
        }
    }
}
