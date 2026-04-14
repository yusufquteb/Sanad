package com.missingpersons.app.utils;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import com.google.android.gms.ads.*;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

/**
 * AdsManager v3.5 — مدير الإعلانات الذكي
 *
 * ✅ Banner Ad — أسفل الرئيسية والتصفح
 * ✅ Interstitial — عند إرسال بلاغ (مرة كل 2 بلاغ)
 * ✅ Rewarded Video — اختياري قبل ميزة إضافية
 * ✅ Native-style ads — بين عناصر القائمة (كل 5 عناصر)
 *
 * سياسة الإعلانات (غير مزعجة):
 * - لا إعلانات في: تفاصيل الحالة، المحادثات، صفحة الإبلاغ نفسها
 * - Interstitial: مرة كل بلاغين فقط (ليس كل مرة)
 * - Banner: ثابت أسفل الصفحات الرئيسية فقط
 * - لا إعلانات popup مفاجئة
 */
public class AdsManager {

    private static final String TAG = "AdsManager";
    private static AdsManager instance;
    private Activity activity;
    private InterstitialAd mInterstitialAd;
    private RewardedAd mRewardedAd;
    private int submitCount = 0;
    private int navigationCount = 0;
    private boolean interstitialLoading = false;
    // Frequency cap: لا interstitial أكثر من مرة كل 3 دقائق
    private static final long MIN_INTERSTITIAL_INTERVAL_MS = 3 * 60 * 1000L;
    private long lastInterstitialShownAt = 0L;

    // ══ Test Ad Unit IDs (للتطوير — استبدل عند النشر) ══
    public static final String BANNER_ID       = "ca-app-pub-3940256099942544/6300978111";
    public static final String INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712";
    public static final String REWARDED_ID     = "ca-app-pub-3940256099942544/5224354917";
    public static final String NATIVE_ID       = "ca-app-pub-3940256099942544/2247696110";

    /** عدد عناصر القائمة بين كل إعلان native */
    public static final int NATIVE_AD_INTERVAL = 5;

    /** عدد البلاغات بين كل interstitial */
    private static final int INTERSTITIAL_EVERY_N = 2;

    private static final boolean ADS_ENABLED = true;

    private AdsManager(Activity activity) {
        this.activity = activity;
        preloadInterstitial();
    }

    public static AdsManager getInstance(Activity activity) {
        if (instance == null) instance = new AdsManager(activity);
        else instance.activity = activity;
        return instance;
    }

    // ═══════════════════════════════════════
    //  BANNER AD
    // ═══════════════════════════════════════

    public void loadBannerAd(AdView bannerView) {
        if (!ADS_ENABLED || bannerView == null) return;
        AdRequest adRequest = new AdRequest.Builder().build();
        bannerView.loadAd(adRequest);
        bannerView.setAdListener(new AdListener() {
            @Override public void onAdLoaded() {
                bannerView.setVisibility(View.VISIBLE);
            }
            @Override public void onAdFailedToLoad(LoadAdError e) {
                bannerView.setVisibility(View.GONE);
            }
        });
    }

    // ═══════════════════════════════════════
    //  INTERSTITIAL AD (عند إرسال بلاغ)
    // ═══════════════════════════════════════

    private void preloadInterstitial() {
        if (!ADS_ENABLED || activity == null || interstitialLoading) return;
        interstitialLoading = true;
        InterstitialAd.load(activity, INTERSTITIAL_ID,
            new AdRequest.Builder().build(),
            new InterstitialAdLoadCallback() {
                @Override public void onAdLoaded(@androidx.annotation.NonNull InterstitialAd ad) {
                    mInterstitialAd = ad;
                    interstitialLoading = false;
                }
                @Override public void onAdFailedToLoad(@androidx.annotation.NonNull LoadAdError e) {
                    mInterstitialAd = null;
                    interstitialLoading = false;
                }
            });
    }

    /**
     * عرض Interstitial عند إرسال بلاغ (مرة كل بلاغين)
     * غير مزعج — لا يظهر كل مرة
     */
    public void showInterstitialOnSubmit(Activity caller, Runnable onComplete) {
        if (caller != null) activity = caller;
        submitCount++;

        if (!ADS_ENABLED || mInterstitialAd == null || submitCount % INTERSTITIAL_EVERY_N != 0) {
            if (onComplete != null) onComplete.run();
            return;
        }
        // Frequency cap: لا تعرض interstitial إذا مضى أقل من 3 دقائق على آخر واحد
        long now = System.currentTimeMillis();
        if (now - lastInterstitialShownAt < MIN_INTERSTITIAL_INTERVAL_MS) {
            if (onComplete != null) onComplete.run();
            return;
        }

        mInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() {
                lastInterstitialShownAt = System.currentTimeMillis();
                mInterstitialAd = null;
                preloadInterstitial();
                if (onComplete != null) onComplete.run();
            }
            @Override public void onAdFailedToShowFullScreenContent(@androidx.annotation.NonNull AdError e) {
                if (onComplete != null) onComplete.run();
            }
        });
        mInterstitialAd.show(activity);
    }

    /**
     * عرض Interstitial عند التنقل (مرة كل 3 تنقلات)
     */
    public void showInterstitialAd(Activity caller, Runnable onComplete) {
        if (caller != null) activity = caller;
        navigationCount++;

        if (!ADS_ENABLED || mInterstitialAd == null || navigationCount % 3 != 0) {
            if (onComplete != null) onComplete.run();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastInterstitialShownAt < MIN_INTERSTITIAL_INTERVAL_MS) {
            if (onComplete != null) onComplete.run();
            return;
        }

        mInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() {
                lastInterstitialShownAt = System.currentTimeMillis();
                mInterstitialAd = null;
                preloadInterstitial();
                if (onComplete != null) onComplete.run();
            }
            @Override public void onAdFailedToShowFullScreenContent(@androidx.annotation.NonNull AdError e) {
                if (onComplete != null) onComplete.run();
            }
        });
        mInterstitialAd.show(activity);
    }

    // ═══════════════════════════════════════
    //  REWARDED VIDEO AD
    // ═══════════════════════════════════════

    public void loadRewardedAd(OnRewardCallback callback) {
        if (!ADS_ENABLED || activity == null) return;
        RewardedAd.load(activity, REWARDED_ID,
            new AdRequest.Builder().build(),
            new RewardedAdLoadCallback() {
                @Override public void onAdLoaded(@androidx.annotation.NonNull RewardedAd ad) {
                    mRewardedAd = ad;
                    if (callback != null) callback.onReady();
                }
                @Override public void onAdFailedToLoad(@androidx.annotation.NonNull LoadAdError e) {
                    mRewardedAd = null;
                }
            });
    }

    public void showRewardedAd(Activity caller, OnRewardCallback callback) {
        if (caller != null) activity = caller;
        if (!ADS_ENABLED || mRewardedAd == null || activity == null) {
            if (callback != null) callback.onSkipped();
            return;
        }
        mRewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() {
                mRewardedAd = null;
                loadRewardedAd((OnRewardCallback) null);
            }
        });
        mRewardedAd.show(activity, item -> {
            if (callback != null) callback.onRewarded();
        });
    }

    // ═══════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════

    /**
     * هل هذا الموضع في القائمة يجب أن يكون إعلان؟
     * يُستخدم في BrowseActivity لعرض إعلانات بين الحالات
     */
    public static boolean isAdPosition(int position) {
        return ADS_ENABLED && position > 0 && (position + 1) % NATIVE_AD_INTERVAL == 0;
    }

    /**
     * حساب الموضع الحقيقي في البيانات (بعد استثناء مواضع الإعلانات)
     */
    public static int getDataPosition(int adapterPosition) {
        if (!ADS_ENABLED) return adapterPosition;
        int adsBefore = adapterPosition / NATIVE_AD_INTERVAL;
        return adapterPosition - adsBefore;
    }

    /**
     * حساب حجم الـ adapter (بيانات + إعلانات)
     */
    public static int getAdapterCount(int dataCount) {
        if (!ADS_ENABLED || dataCount == 0) return dataCount;
        int ads = dataCount / (NATIVE_AD_INTERVAL - 1);
        return dataCount + ads;
    }

    public void destroy() { instance = null; }

    // ══ Callbacks ══
    public interface OnRewardCallback {
        default void onReady() {}
        default void onRewarded() {}
        default void onSkipped() {}
    }

    public interface OnInterstitialLoadedListener { void onLoaded(); }
    public interface OnRewardedAdLoadedListener   { void onLoaded(); }
    public interface OnUserEarnedRewardListener   { void onRewardEarned(); }

    // Legacy compatibility
    public void loadInterstitialAd(OnInterstitialLoadedListener l) { preloadInterstitial(); }
    public void showInterstitialAd() { showInterstitialAd(null, null); }
    public void loadRewardedAd(OnRewardedAdLoadedListener l) { loadRewardedAd((OnRewardCallback) null); }
    public void showRewardedAd(OnUserEarnedRewardListener l) {
        showRewardedAd(null, new OnRewardCallback() {
            @Override public void onRewarded() { if (l != null) l.onRewardEarned(); }
        });
    }
}
