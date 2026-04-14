package com.missingpersons.app.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.concurrent.TimeUnit;

import coil.Coil;
import coil.ImageLoader;
import coil.disk.DiskCache;
import coil.memory.MemoryCache;
import okhttp3.OkHttpClient;

/**
 * PerformanceConfig — إعدادات الأداء
 *
 * [إصلاح] DiskCache.Builder.directory() يقبل java.io.File وليس java.nio.file.Path
 * السبب: Coil 2.x على Android يستخدم File API لا NIO Path
 */
public final class PerformanceConfig {

    private static final String TAG           = "PerformanceConfig";
    private static final long   DISK_CACHE_MB = 50L;
    private static final float  MEM_FRACTION  = 0.25f;

    private PerformanceConfig() {}

    // ════════════════════════════════════════════════════════
    //  init — استدعِ في MyApplication.onCreate()
    // ════════════════════════════════════════════════════════

    public static void init(@NonNull Context ctx) {
        initCoil(ctx);
        initFirebasePersistence();
        Log.d(TAG, "✅ Performance config applied");
    }

    // ════════════════════════════════════════════════════════
    //  Coil ImageLoader
    // ════════════════════════════════════════════════════════

    private static void initCoil(@NonNull Context ctx) {
        try {
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20,    TimeUnit.SECONDS)
                .writeTimeout(20,   TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();

            // [إصلاح] نستخدم java.io.File مباشرة بدلاً من Path.resolve()
            // كان: ctx.getCacheDir().toPath().resolve("image_cache")  ← خطأ
            // الآن: new File(ctx.getCacheDir(), "image_cache")         ← صحيح
            File imageCacheDir = new File(ctx.getCacheDir(), "image_cache");

            ImageLoader loader = new ImageLoader.Builder(ctx)
                .memoryCache(
                    new MemoryCache.Builder(ctx)
                        .maxSizePercent(MEM_FRACTION)
                        .build()
                )
                .diskCache(
                    new DiskCache.Builder()
                        .directory(imageCacheDir)           // ← File مباشرة
                        .maxSizeBytes(DISK_CACHE_MB * 1024 * 1024)
                        .build()
                )
                .okHttpClient(okHttpClient)
                .respectCacheHeaders(false)
                .crossfade(300)
                .build();

            Coil.setImageLoader(loader);
            Log.d(TAG, "Coil: mem=" + (int)(MEM_FRACTION * 100) + "% disk="
                + DISK_CACHE_MB + "MB dir=" + imageCacheDir.getAbsolutePath());

        } catch (Exception e) {
            Log.e(TAG, "Coil init failed: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════
    //  Firebase keepSynced
    // ════════════════════════════════════════════════════════

    private static void initFirebasePersistence() {
        try {
            com.google.firebase.database.FirebaseDatabase db =
                com.google.firebase.database.FirebaseDatabase.getInstance();
            db.getReference("reports").keepSynced(true);
            db.getReference("stats").keepSynced(true);
            Log.d(TAG, "Firebase keepSynced: reports + stats");
        } catch (Exception e) {
            Log.w(TAG, "Firebase keepSynced failed: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════
    //  RecyclerView Optimizations
    // ════════════════════════════════════════════════════════

    /**
     * تطبيق إعدادات الأداء على RecyclerView
     *
     * @param rv        الـ RecyclerView
     * @param fixedSize true إذا حجم الـ RecyclerView ثابت لا يتغير
     */
    public static void optimizeRecyclerView(@NonNull RecyclerView rv, boolean fixedSize) {
        rv.setHasFixedSize(fixedSize);

        RecyclerView.LayoutManager lm = rv.getLayoutManager();
        if (lm instanceof androidx.recyclerview.widget.LinearLayoutManager) {
            ((androidx.recyclerview.widget.LinearLayoutManager) lm)
                .setInitialPrefetchItemCount(4);
        }
    }
}
