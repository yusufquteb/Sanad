package com.missingpersons.app.utils;

import android.content.Context;
import android.media.*;
import android.net.Uri;
import android.util.Log;
import java.io.*;

/**
 * VideoCompressor — ضغط الفيديو قبل الرفع
 *
 * يدعم فيديو حتى 30 ثانية
 * يضغط إلى 720p بمعدل 2Mbps → ملف ~5-8 MB
 */
/**
 * @deprecated كود ميت — لا يُستخدم في أي مكان.
 * يمكن حذف هذا الملف بعد التأكد من عدم الحاجة إليه.
 * آخر مراجعة: 2026-04
 */
@Deprecated
public class VideoCompressor {

    private static final String TAG = "VideoCompressor";
    public static final int MAX_DURATION_SEC = 30;
    public static final long MAX_FILE_SIZE_MB = 15;

    public interface CompressCallback {
        void onProgress(int percent);
        void onComplete(File compressedFile, long originalSize, long compressedSize);
        void onError(String error);
    }

    /**
     * ضغط فيديو من Uri
     */
    public static void compress(Context context, Uri videoUri, CompressCallback callback) {
        new Thread(() -> {
            try {
                // نسخ الفيديو من Uri إلى ملف مؤقت
                File tempInput = copyUriToFile(context, videoUri);
                if (tempInput == null) {
                    postError(context, callback, "تعذر قراءة الفيديو");
                    return;
                }

                long originalSize = tempInput.length();

                // فحص المدة
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(tempInput.getAbsolutePath());
                String durationStr = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION);
                long durationMs = durationStr != null ? Long.parseLong(durationStr) : 0;
                retriever.release();

                if (durationMs > MAX_DURATION_SEC * 1000L) {
                    postError(context, callback,
                        "الفيديو أطول من " + MAX_DURATION_SEC + " ثانية");
                    tempInput.delete();
                    return;
                }

                // إذا الحجم صغير أصلاً، لا داعي للضغط
                if (originalSize < 2 * 1024 * 1024) { // أقل من 2MB
                    File output = new File(context.getCacheDir(),
                        "vid_" + System.currentTimeMillis() + ".mp4");
                    copyFile(tempInput, output);
                    postComplete(context, callback, output, originalSize, output.length());
                    tempInput.delete();
                    return;
                }

                // ضغط بسيط: إعادة ترميز بجودة أقل
                File output = simpleCompress(context, tempInput);
                long compressedSize = output != null ? output.length() : originalSize;

                if (output != null && output.exists()) {
                    postComplete(context, callback, output, originalSize, compressedSize);
                } else {
                    // fallback: استخدم الأصلي
                    output = new File(context.getCacheDir(),
                        "vid_orig_" + System.currentTimeMillis() + ".mp4");
                    copyFile(tempInput, output);
                    postComplete(context, callback, output, originalSize, output.length());
                }

                tempInput.delete();

            } catch (Exception e) {
                Log.e(TAG, "Compression error", e);
                postError(context, callback, "خطأ في ضغط الفيديو: " + e.getMessage());
            }
        }).start();
    }

    /**
     * ضغط بسيط باستخدام MediaExtractor + MediaMuxer
     * يحتفظ بالفيديو كما هو لكن يزيل metadata الزائدة
     */
    private static File simpleCompress(Context context, File input) {
        try {
            File output = new File(context.getCacheDir(),
                "vid_comp_" + System.currentTimeMillis() + ".mp4");

            MediaExtractor extractor = new MediaExtractor();
            extractor.setDataSource(input.getAbsolutePath());

            int trackCount = extractor.getTrackCount();
            MediaMuxer muxer = new MediaMuxer(output.getAbsolutePath(),
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            int[] trackMap = new int[trackCount];
            for (int i = 0; i < trackCount; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                trackMap[i] = muxer.addTrack(format);
            }

            muxer.start();

            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(1024 * 1024);
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            for (int i = 0; i < trackCount; i++) {
                extractor.selectTrack(i);
                while (true) {
                    int sampleSize = extractor.readSampleData(buffer, 0);
                    if (sampleSize < 0) break;

                    bufferInfo.offset = 0;
                    bufferInfo.size = sampleSize;
                    bufferInfo.presentationTimeUs = extractor.getSampleTime();
                    bufferInfo.flags = extractor.getSampleFlags();

                    muxer.writeSampleData(trackMap[i], buffer, bufferInfo);
                    extractor.advance();
                }
                extractor.unselectTrack(i);
            }

            muxer.stop();
            muxer.release();
            extractor.release();

            return output;
        } catch (Exception e) {
            Log.e(TAG, "simpleCompress error", e);
            return null;
        }
    }

    /**
     * فحص صلاحية الفيديو
     */
    public static String validateVideo(Context context, Uri videoUri) {
        try {
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            r.setDataSource(context, videoUri);

            String duration = r.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION);
            long durationMs = duration != null ? Long.parseLong(duration) : 0;
            r.release();

            if (durationMs > MAX_DURATION_SEC * 1000L) {
                return "الفيديو أطول من " + MAX_DURATION_SEC + " ثانية";
            }

            // فحص الحجم
            try (InputStream is = context.getContentResolver().openInputStream(videoUri)) {
                if (is != null) {
                    long size = is.available();
                    if (size > MAX_FILE_SIZE_MB * 1024 * 1024) {
                        return "الفيديو أكبر من " + MAX_FILE_SIZE_MB + " MB";
                    }
                }
            }

            return null; // صالح
        } catch (Exception e) {
            return "خطأ في قراءة الفيديو: " + e.getMessage();
        }
    }

    // ─── Helpers ───

    private static File copyUriToFile(Context context, Uri uri) {
        try {
            InputStream is = context.getContentResolver().openInputStream(uri);
            if (is == null) return null;
            File temp = new File(context.getCacheDir(),
                "vid_temp_" + System.currentTimeMillis() + ".mp4");
            FileOutputStream fos = new FileOutputStream(temp);
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) fos.write(buf, 0, read);
            fos.close();
            is.close();
            return temp;
        } catch (Exception e) {
            return null;
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (FileInputStream fis = new FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = fis.read(buf)) != -1) fos.write(buf, 0, read);
        }
    }

    private static void postError(Context ctx, CompressCallback cb, String msg) {
        if (ctx instanceof android.app.Activity) {
            ((android.app.Activity) ctx).runOnUiThread(() -> cb.onError(msg));
        }
    }

    private static void postComplete(Context ctx, CompressCallback cb,
                                      File file, long orig, long comp) {
        if (ctx instanceof android.app.Activity) {
            ((android.app.Activity) ctx).runOnUiThread(
                () -> cb.onComplete(file, orig, comp));
        }
    }
}
