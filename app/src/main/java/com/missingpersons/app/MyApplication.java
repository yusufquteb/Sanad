package com.missingpersons.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
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
import com.missingpersons.app.utils.AiError;
import com.missingpersons.app.utils.PerformanceConfig;
import com.missingpersons.app.utils.FaceEmbeddingManager;
import com.missingpersons.app.utils.RateLimiter;
import com.missingpersons.app.utils.RoleManager;
import com.missingpersons.app.utils.TFLiteFaceRecognizer;
import com.missingpersons.app.workers.BackgroundMatchWorker;
import com.missingpersons.app.workers.ProximityCheckWorker;
import com.missingpersons.app.workers.ChatCleanupWorker;
import com.missingpersons.app.workers.DailyReportWorker;
import java.util.Arrays;
import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * MyApplication
 *
 * [المرحلة 1 — إضافة TFLite Startup Verification]
 *
 * ════════════════════════════════════════════════════════
 * التغييرات الجديدة:
 *
 * ✅ verifyTFLiteModel() — تحقق فوري من تحميل النموذج
 *    → يُشغَّل في background thread فور بدء التطبيق
 *    → إذا فشل: يُسجّل في Crashlytics + يضع Custom Key
 *    → لا يُوقف التطبيق (non-fatal) لكن يُنبّه الفريق
 *
 * ✅ تسجيل embeddingVersion و modelVersion في Crashlytics
 *    → يُسهّل تتبع الأخطاء عبر إصدارات النماذج المختلفة
 *
 * ════════════════════════════════════════════════════════
 * RULE: أي إصدار يُنشر يجب أن يُمرّ verifyTFLiteModel()
 * بنجاح على الجهاز المستهدف قبل النشر.
 * ════════════════════════════════════════════════════════
 */
public class MyApplication extends MultiDexApplication {

    public static final String CHANNEL_ID       = "missing_persons_channel";
    public static final String CHANNEL_ADMIN_ID = "admin_notifications";
    private static final String TAG             = "MyApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        // ── [7.1] تهيئة الأداء (Coil + Firebase keepSynced) ─────────────────
        try { PerformanceConfig.init(this); } catch (Exception e) { android.util.Log.e(TAG, "PerformanceConfig: " + e); }

        // ── Firebase Offline Persistence (يجب قبل أي استخدام لـ Firebase) ──
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
            FirebaseDatabase.getInstance().setPersistenceCacheSizeBytes(10 * 1024 * 1024L);
            Log.d(TAG, "✅ Firebase offline persistence enabled");
        } catch (Exception e) {
            Log.w(TAG, "Firebase persistence already enabled: " + e.getMessage());
        }

        // ── تهيئة RoleManager ─────────────────────────────────────────────
        try {
            RoleManager.init(this);
            Log.d(TAG, "✅ RoleManager initialized");
        } catch (Exception e) {
            Log.e(TAG, "RoleManager init error: " + e.getMessage());
        }

        try { createNotificationChannels(); } catch (Exception e) { Log.e(TAG, "createNotificationChannels: " + e); }
        try { initCrashlytics();            } catch (Exception e) { Log.e(TAG, "initCrashlytics: "            + e); }
        try { initAdMob();                  } catch (Exception e) { Log.e(TAG, "initAdMob: "                  + e); }
        try { initAnalytics();              } catch (Exception e) { Log.e(TAG, "initAnalytics: "              + e); }
        try { initAppCheck();               } catch (Exception e) { Log.e(TAG, "initAppCheck: "               + e); }

        // ── تهيئة وتحقق AI ── (ترتيب مهم: Crashlytics أولاً)
        try { initFaceRecognition();        } catch (Exception e) { Log.e(TAG, "initFaceRecognition: "        + e); }
        try { verifyTFLiteModel();          } catch (Exception e) { Log.e(TAG, "verifyTFLiteModel: "          + e); }

        try { BackgroundMatchWorker.scheduleDailyMatch(this);    } catch (Exception e) { Log.e(TAG, "scheduleDailyMatch: "     + e); }
        try { ProximityCheckWorker.scheduleProximityCheck(this); } catch (Exception e) { Log.e(TAG, "scheduleProximityCheck: " + e); }
        try { scheduleDailyChatCleanup();                        } catch (Exception e) { Log.e(TAG, "scheduleDailyChatCleanup: " + e); }
        try { DailyReportWorker.scheduleDailyReport(this);       } catch (Exception e) { Log.e(TAG, "scheduleDailyReport: "    + e); }
        try { RateLimiter.fetchAndCacheDailyLimit(this);         } catch (Exception e) { Log.e(TAG, "fetchAndCacheDailyLimit: " + e); }
    }

    // ════════════════════════════════════════════════════════
    //  [جديد] TFLite Startup Verification
    // ════════════════════════════════════════════════════════

    /**
     * يتحقق من تحميل نموذج mobilefacenet.tflite فور بدء التطبيق.
     *
     * يُشغَّل في background thread لأن تحميل النموذج يستغرق وقتاً.
     * إذا فشل:
     *   1. يُسجّل في Crashlytics كـ non-fatal exception
     *   2. يضع Custom Keys لتسهيل تحليل المشكلة
     *   3. يطبع تحذيراً واضحاً في Logcat
     *
     * لا يُوقف التطبيق — المستخدم يستطيع الاستعراض لكن لا يستطيع
     * رفع بلاغ بدون embedding (هذا يُعالج في ReportActivity).
     */
    private void verifyTFLiteModel() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                TFLiteFaceRecognizer recognizer = TFLiteFaceRecognizer.getInstance(this);

                if (recognizer.isAvailable()) {
                    Log.i(TAG, "✅ TFLite Model Verified: mobilefacenet.tflite جاهز"
                        + " | model=" + FaceEmbeddingManager.MODEL_VERSION
                        + " | embeddingVersion=" + FaceEmbeddingManager.EMBEDDING_VERSION
                        + " | threshold=" + TFLiteFaceRecognizer.MATCH_THRESHOLD);

                    // تسجيل معلومات النموذج في Crashlytics للمرجعية
                    try {
                        FirebaseCrashlytics c = FirebaseCrashlytics.getInstance();
                        c.setCustomKey("tflite_loaded",       true);
                        c.setCustomKey("model_version",       FaceEmbeddingManager.MODEL_VERSION);
                        c.setCustomKey("embedding_version",   FaceEmbeddingManager.EMBEDDING_VERSION);
                        c.setCustomKey("match_threshold",     TFLiteFaceRecognizer.MATCH_THRESHOLD);
                    } catch (Exception ignored) {}

                } else {
                    // ❌ النموذج غير متاح — هذا خطأ جوهري
                    Log.e(TAG, "❌ ❌ ❌ TFLite Model FAILED TO LOAD ❌ ❌ ❌");
                    Log.e(TAG, "→ تأكد من وجود mobilefacenet.tflite في app/src/main/assets/");
                    Log.e(TAG, "→ لن تعمل المطابقة على هذا الجهاز حتى يتم الإصلاح");

                    // تسجيل في Crashlytics
                    try {
                        FirebaseCrashlytics c = FirebaseCrashlytics.getInstance();
                        c.setCustomKey("tflite_loaded",     false);
                        c.setCustomKey("model_version",     FaceEmbeddingManager.MODEL_VERSION);
                        c.setCustomKey("embedding_version", FaceEmbeddingManager.EMBEDDING_VERSION);
                        c.setCustomKey("device_model",      android.os.Build.MODEL);
                        c.setCustomKey("android_version",   android.os.Build.VERSION.SDK_INT);
                        // تسجيل كـ non-fatal (لا يُغلق التطبيق)
                        c.recordException(new RuntimeException(
                            "AI_ERROR:" + AiError.MODEL_NOT_LOADED
                            + " | mobilefacenet.tflite failed to load"
                            + " | device=" + android.os.Build.MODEL
                            + " | sdk=" + android.os.Build.VERSION.SDK_INT));
                    } catch (Exception ignored) {}
                }

            } catch (Exception e) {
                Log.e(TAG, "❌ verifyTFLiteModel exception: " + e.getMessage());
                AiError.logAll(TAG, AiError.MODEL_NOT_LOADED,
                    "verifyTFLiteModel startup check", e);
            }
        });
    }

    // ════════════════════════════════════════════════════════
    //  باقي الدوال — بدون تغيير
    // ════════════════════════════════════════════════════════

    private void initCrashlytics() {
        FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
        crashlytics.setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG);
        crashlytics.setCustomKey("app_version", BuildConfig.VERSION_NAME);
        Thread.UncaughtExceptionHandler defaultHandler =
            Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            crashlytics.recordException(throwable);
            Log.e(TAG, "Uncaught exception on thread " + thread.getName(), throwable);
            if (defaultHandler != null) defaultHandler.uncaughtException(thread, throwable);
        });
        Log.d(TAG, "✅ Crashlytics initialized (collection=" + !BuildConfig.DEBUG + ")");
    }

    private void initFaceRecognition() {
        FaceEmbeddingManager.init(this);
        Log.d(TAG, "✅ FaceEmbeddingManager initialized");
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

            Uri notifSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            AudioAttributes audioAttr = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

            NotificationChannel mainChannel = new NotificationChannel(
                CHANNEL_ID, "إشعارات المفقودين", NotificationManager.IMPORTANCE_HIGH);
            mainChannel.setDescription("إشعارات عند العثور على تطابق أو رد");
            mainChannel.enableVibration(true);
            mainChannel.setVibrationPattern(new long[]{0, 400, 200, 400});
            mainChannel.setShowBadge(true);
            mainChannel.setSound(notifSound, audioAttr);

            NotificationChannel adminChannel = new NotificationChannel(
                CHANNEL_ADMIN_ID, "إشعارات الإدارة", NotificationManager.IMPORTANCE_DEFAULT);
            adminChannel.setDescription("بلاغات جديدة تنتظر المراجعة");
            adminChannel.setSound(notifSound, audioAttr);

            NotificationChannel amberChannel = new NotificationChannel(
                "amber_alerts", "تنبيهات الأطفال المفقودين",
                NotificationManager.IMPORTANCE_HIGH);
            amberChannel.setDescription("تنبيهات عاجلة عند اختفاء أطفال في منطقتك");
            amberChannel.enableVibration(true);
            amberChannel.setVibrationPattern(new long[]{0, 600, 300, 600, 300, 600});
            amberChannel.setLockscreenVisibility(1);
            amberChannel.setSound(notifSound, audioAttr);

            NotificationChannel msgChannel = new NotificationChannel(
                "chat_messages", "رسائل الدردشة", NotificationManager.IMPORTANCE_HIGH);
            msgChannel.setDescription("إشعارات الرسائل الجديدة");
            msgChannel.enableVibration(true);
            msgChannel.setShowBadge(true);
            msgChannel.setSound(notifSound, audioAttr);

            manager.createNotificationChannel(mainChannel);
            manager.createNotificationChannel(adminChannel);
            manager.createNotificationChannel(amberChannel);
            manager.createNotificationChannel(msgChannel);
        }
    }
}
