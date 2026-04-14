package com.missingpersons.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

/**
 * FaceMatcher - مقارنة الوجوه بدون TFLite (متوافق 100%)
 * MVP mode: pixel + histogram comparison
 */
public class FaceMatcher {

    private static final float MATCH_THRESHOLD = 0.70f;
    private final Context context;

    public interface MatchCallback {
        void onResult(boolean isMatch, float confidence, String message);
    }

    public FaceMatcher(Context context) {
        this.context = context;
    }

    public void compareFaces(Bitmap bitmap1, Bitmap bitmap2, MatchCallback callback) {
        if (bitmap1 == null || bitmap2 == null) {
            new Handler(Looper.getMainLooper()).post(() ->
                callback.onResult(false, 0f, "خطأ: صورة فارغة"));
            return;
        }
        new Thread(() -> {
            float similarity = pixelSimilarity(bitmap1, bitmap2);
            boolean isMatch = similarity >= MATCH_THRESHOLD;
            String message = isMatch
                    ? "تطابق محتمل بنسبة " + (int)(similarity * 100) + "%"
                    : "لا يوجد تطابق كافٍ (" + (int)(similarity * 100) + "%)";
            new Handler(Looper.getMainLooper()).post(() ->
                callback.onResult(isMatch, similarity, message));
        }).start();
    }

    public float compareFacesSync(Bitmap bitmap1, Bitmap bitmap2) {
        if (bitmap1 == null || bitmap2 == null) return 0f;
        return pixelSimilarity(bitmap1, bitmap2);
    }

    public boolean isMatch(float similarity) {
        return similarity >= MATCH_THRESHOLD;
    }

    private float pixelSimilarity(Bitmap b1, Bitmap b2) {
        int size = 64;
        Bitmap r1 = Bitmap.createScaledBitmap(b1, size, size, true);
        Bitmap r2 = Bitmap.createScaledBitmap(b2, size, size, true);

        int[] hist1 = new int[256];
        int[] hist2 = new int[256];
        long pixelDiff = 0;
        long maxDiff = (long) size * size * 3L * 255L;

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                int p1 = r1.getPixel(x, y);
                int p2 = r2.getPixel(x, y);
                int r1v = (p1 >> 16) & 0xFF, g1v = (p1 >> 8) & 0xFF, b1v = p1 & 0xFF;
                int r2v = (p2 >> 16) & 0xFF, g2v = (p2 >> 8) & 0xFF, b2v = p2 & 0xFF;
                pixelDiff += Math.abs(r1v - r2v) + Math.abs(g1v - g2v) + Math.abs(b1v - b2v);
                hist1[(r1v + g1v + b1v) / 3]++;
                hist2[(r2v + g2v + b2v) / 3]++;
            }
        }

        float pixelSim = 1.0f - ((float) pixelDiff / maxDiff);
        double histSim = 0;
        int total = size * size;
        for (int i = 0; i < 256; i++) {
            histSim += Math.sqrt((double) hist1[i] * hist2[i] / ((double) total * total));
        }
        return (float)(pixelSim * 0.5 + histSim * 0.5);
    }

    public void release() { /* nothing to release */ }
}
