package com.missingpersons.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.util.Log;
import androidx.multidex.MultiDexApplication;
import androidx.work.*;
import coil.Coil;
import coil.ImageLoader;
import coil.disk.DiskCache;
import coil.memory.MemoryCache;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.database.FirebaseDatabase;
import com.missingpersons.app.BuildConfig;
import com.missingpersons.app.utils.FaceEmbeddingManager;
import com.missingpersons.app.utils.RateLimiter;
import com.missingpersons.app.utils.RoleManager;
import com.missingpersons.app.workers.BackgroundMatchWorker;
import com.missingpersons.app.workers.ProximityCheckWorker;
import com.missingpersons.app.workers.ChatCleanupWorker;
import com.missingpersons.app.workers.DailyReportWorker;
import java.util.Arrays;
import java.io.File;
import java.util.concurrent.TimeUnit;

public class MyApplication extends MultiDexApplication {

    public static final String CHANNEL_ID       = "missing_persons_channel";
    public static final String CHANNEL_ADMIN_ID = "admin_notifications";
    private static final String TAG             = "MyApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        // ── Firebase Offline Persistence (يجب قبل أي استخدام لـ Firebase) ──
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
            FirebaseDatabase.getInstance().setPersistenceCacheSizeBytes(10 * 1024 * 1024L);
            Log.d(TAG, "✅ Firebase offline persistence enabled");
        } catch (Exception e) {
            Log.w(TAG, "Firebase persistence already enabled: " + e.getMessage());
        }

        // ── تهيئة RoleManager (يجب قبل أي Activity) ─────────────────
        // يخزّن Context لاستخدام SharedPreferences للـ offline cache
        try {
            RoleManager.init(this);
            Log.d(TAG, "✅ RoleManager initialized");
        } catch (Exception e) {
            Log.e(TAG, "RoleManager init error: " + e.getMessage());
        }

        try { createNotificationChannels(); } catch (Exception e) { Log.e(TAG, "createNotificationChannels: " + e); }
        try { initCrashlytics();            } catch (Exception e) { Log.e(TAG, "initCrashlytics: "            + e); }
        try { initCoilImageLoader();        } catch (Exception e) { Log.e(TAG, "initCoilImageLoader: "        + e); }
        try { initAdMob();                  } catch (Exception e) { Log.e(TAG, "initAdMob: "                  + e); }
        try { initAnalytics();              } catch (Exception e) { Log.e(TAG, "initAnalytics: "              + e); }
        try { initAppCheck();               } catch (Exception e) { Log.e(TAG, "initAppCheck: "               + e); }
        try { initFaceRecognition();        } catch (Exception e) { Log.e(TAG, "initFaceRecognition: "        + e); }
        try { BackgroundMatchWorker.scheduleDailyMatch(this);  } catch (Exception e) { Log.e(TAG, "scheduleDailyMatch: "     + e); }
        try { ProximityCheckWorker.scheduleProximityCheck(this); } catch (Exception e) { Log.e(TAG, "scheduleProximityCheck: " + e); }
        try { scheduleDailyChatCleanup();   } catch (Exception e) { Log.e(TAG, "scheduleDailyChatCleanup: "   + e); }
        try { DailyReportWorker.scheduleDailyReport(this);     } catch (Exception e) { Log.e(TAG, "scheduleDailyReport: "    + e); }
        try { RateLimiter.fetchAndCacheDailyLimit(this);       } catch (Exception e) { Log.e(TAG, "fetchAndCacheDailyLimit: " + e); }
    }

    private void initCrashlytics() {
        FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
        crashlytics.setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG);
        crashlytics.setCustomKey("app_version", BuildConfig.VERSION_NAME);
        // Global uncaught exception handler — يسجّل الـ crash قبل الموت
        Thread.UncaughtExceptionHandler defaultHandler =
            Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            crashlytics.recordException(throwable);
            Log.e(TAG, "Uncaught exception on thread " + thread.getName(), throwable);
            if (defaultHandler != null) defaultHandler.uncaughtException(thread, throwable);
        });
        Log.d(TAG, "✅ Crashlytics initialized (collection=" + !BuildConfig.DEBUG + ")");
    }

    private void scheduleDailyChatCleanup() {
        PeriodicWorkRequest cleanupWork =
            new PeriodicWorkRequest.Builder(ChatCleanupWorker.class, 24, TimeUnit.HOURS)
                .setConstraints(new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "chat_cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupWork);
        Log.d(TAG, "✅ Daily chat cleanup scheduled");
    }

    private void initFaceRecognition() {
        try {
            FaceEmbeddingManager.init(this);
            Log.d(TAG, "✅ FaceEmbeddingManager initialized");
        } catch (Exception e) {
            Log.e(TAG, "FaceEmbedding init error (non-fatal): " + e.getMessage());
        }
    }

    private void initAnalytics() {
        try {
            com.missingpersons.app.utils.AnalyticsHelper.init(this);
            com.missingpersons.app.utils.AnalyticsHelper.logAppOpen();
            Log.d(TAG, "✅ Analytics initialized");
        } catch (Exception e) {
            Log.e(TAG, "Analytics init error: " + e.getMessage());
        }
    }

    private void initAppCheck() {
        try {
            com.google.firebase.appcheck.FirebaseAppCheck appCheck =
                com.google.firebase.appcheck.FirebaseAppCheck.getInstance();
            appCheck.installAppCheckProviderFactory(
                com.google.firebase.appcheck.playintegrity
                    .PlayIntegrityAppCheckProviderFactory.getInstance());
            Log.d(TAG, "✅ App Check initialized");
        } catch (Exception e) {
            Log.e(TAG, "App Check init error (non-fatal): " + e.getMessage());
        }
    }

    private void initCoilImageLoader() {
        ImageLoader imageLoader = new ImageLoader.Builder(this)
            .memoryCache(new MemoryCache.Builder(this)
                .maxSizePercent(0.20)
                .build())
            .diskCache(new DiskCache.Builder()
                .directory(new File(getCacheDir(), "image_cache"))
                .maxSizeBytes(150L * 1024 * 1024)
                .build())
            .crossfade(true)
            .crossfade(300)
            .respectCacheHeaders(false)
            .build();
        Coil.setImageLoader(imageLoader);
        Log.d(TAG, "✅ Coil ImageLoader initialized");
    }

    private void initAdMob() {
        try {
            RequestConfiguration config = new RequestConfiguration.Builder()
                .setTestDeviceIds(Arrays.asList("EMULATOR"))
                .build();
            MobileAds.setRequestConfiguration(config);
            MobileAds.initialize(this, initializationStatus ->
                Log.d(TAG, "✅ AdMob initialized"));
        } catch (Exception e) {
            Log.e(TAG, "❌ AdMob init error (non-fatal): " + e.getMessage());
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;

            NotificationChannel mainChannel = new NotificationChannel(
                CHANNEL_ID, "إشعارات المفقودين", NotificationManager.IMPORTANCE_HIGH);
            mainChannel.setDescription("إشعارات عند العثور على تطابق أو رد");
            mainChannel.enableVibration(true);
            mainChannel.setShowBadge(true);

            NotificationChannel adminChannel = new NotificationChannel(
                CHANNEL_ADMIN_ID, "إشعارات الإدارة", NotificationManager.IMPORTANCE_DEFAULT);
            adminChannel.setDescription("بلاغات جديدة تنتظر المراجعة");

            NotificationChannel amberChannel = new NotificationChannel(
                "amber_alerts", "تنبيهات الأطفال المفقودين",
                NotificationManager.IMPORTANCE_HIGH);
            amberChannel.setDescription("تنبيهات عاجلة عند اختفاء أطفال في منطقتك");
            amberChannel.enableVibration(true);
            amberChannel.setLockscreenVisibility(1);

            manager.createNotificationChannel(mainChannel);
            manager.createNotificationChannel(adminChannel);
            manager.createNotificationChannel(amberChannel);
        }
    }
}
