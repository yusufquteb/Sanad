package com.missingpersons.app.utils;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.*;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.nativead.MediaView;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.missingpersons.app.R;

/**
 * AdsManager v4.1
 *
 * [إصلاح] loadRewardedAd(null) كانت ambiguous بين:
 *   loadRewardedAd(OnRewardCallback)
 *   loadRewardedAd(OnRewardedAdLoadedListener)   ← Legacy
 * الحل: حذف الـ Legacy overloads المتعارضة واستبدالها بـ
 *       dالـ cast الصريح عند الاستدعاء
 *
 * سياسة الإعلانات:
 *  ✅ Banner   — Browse + Home فقط
 *  ✅ Native   — بين بطاقات Browse (كل 5 عناصر)
 *  ✅ Interstitial — بعد إرسال بلاغ (كل بلاغين + 3 دق cap)
 *  ✅ Rewarded — ميزات إضافية (اختياري)
 *  ❌ لا إعلانات في تفاصيل الحالة أو المحادثات
 */
public class AdsManager {

    private static final String TAG = "AdsManager";

    // ══ Test IDs — استبدل بـ IDs حقيقية من AdMob Console قبل النشر ══
    public static final String BANNER_ID       = "ca-app-pub-3940256099942544/6300978111";
    public static final String INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712";
    public static final String REWARDED_ID     = "ca-app-pub-3940256099942544/5224354917";
    public static final String NATIVE_ID       = "ca-app-pub-3940256099942544/2247696110";

    public static final int  NATIVE_AD_INTERVAL       = 5;
    private static final int INTERSTITIAL_EVERY_N     = 2;
    private static final long MIN_INTERSTITIAL_INTERVAL = 3 * 60 * 1000L;
    private static final boolean ADS_ENABLED = true;

    private static AdsManager instance;
    private Activity activity;
    private InterstitialAd mInterstitialAd;
    private RewardedAd     mRewardedAd;
    private boolean        interstitialLoading = false;
    private int            submitCount         = 0;
    private long           lastInterstitialAt  = 0L;

    private AdsManager(Activity activity) {
        this.activity = activity;
        preloadInterstitial();
    }

    public static AdsManager getInstance(Activity activity) {
        if (instance == null) instance = new AdsManager(activity);
        else if (activity != null) instance.activity = activity;
        return instance;
    }

    // ════════════════════════════════════════════════════════
    //  BANNER
    // ════════════════════════════════════════════════════════

    public void loadBannerAd(AdView bannerView) {
        if (!ADS_ENABLED || bannerView == null) return;
        bannerView.loadAd(new AdRequest.Builder().build());
        bannerView.setAdListener(new AdListener() {
            @Override public void onAdLoaded() {
                bannerView.setVisibility(View.VISIBLE);
            }
            @Override public void onAdFailedToLoad(@NonNull LoadAdError e) {
                bannerView.setVisibility(View.GONE);
            }
        });
    }

    // ════════════════════════════════════════════════════════
    //  INTERSTITIAL
    // ════════════════════════════════════════════════════════

    private void preloadInterstitial() {
        if (!ADS_ENABLED || activity == null || interstitialLoading) return;
        interstitialLoading = true;
        InterstitialAd.load(activity, INTERSTITIAL_ID,
            new AdRequest.Builder().build(),
            new InterstitialAdLoadCallback() {
                @Override public void onAdLoaded(@NonNull InterstitialAd ad) {
                    mInterstitialAd     = ad;
                    interstitialLoading = false;
                }
                @Override public void onAdFailedToLoad(@NonNull LoadAdError e) {
                    mInterstitialAd     = null;
                    interstitialLoading = false;
                }
            });
    }

    /** بعد إرسال بلاغ — مرة كل بلاغين */
    public void showInterstitialOnSubmit(Activity caller, Runnable onComplete) {
        if (caller != null) activity = caller;
        submitCount++;
        boolean ok = ADS_ENABLED
            && mInterstitialAd != null
            && (submitCount % INTERSTITIAL_EVERY_N == 0)
            && (System.currentTimeMillis() - lastInterstitialAt >= MIN_INTERSTITIAL_INTERVAL);
        if (!ok) { if (onComplete != null) onComplete.run(); return; }
        showInterstitialInternal(onComplete);
    }

    /** عند التنقل — مع frequency cap */
    public void showInterstitialAd(Activity caller, Runnable onComplete) {
        if (caller != null) activity = caller;
        boolean ok = ADS_ENABLED
            && mInterstitialAd != null
            && (System.currentTimeMillis() - lastInterstitialAt >= MIN_INTERSTITIAL_INTERVAL);
        if (!ok) { if (onComplete != null) onComplete.run(); return; }
        showInterstitialInternal(onComplete);
    }

    private void showInterstitialInternal(Runnable onComplete) {
        mInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() {
                lastInterstitialAt = System.currentTimeMillis();
                mInterstitialAd    = null;
                preloadInterstitial();
                if (onComplete != null) onComplete.run();
            }
            @Override public void onAdFailedToShowFullScreenContent(@NonNull AdError e) {
                mInterstitialAd = null;
                preloadInterstitial();
                if (onComplete != null) onComplete.run();
            }
        });
        mInterstitialAd.show(activity);
    }

    // ════════════════════════════════════════════════════════
    //  NATIVE AD — bindNativeAdToView كامل
    // ════════════════════════════════════════════════════════

    /**
     * ربط NativeAd بـ NativeAdView
     * استدعِ هذا في onBindViewHolder لـ AdViewHolder
     */
    public static void bindNativeAdToView(@NonNull NativeAd ad,
                                          @NonNull NativeAdView adView) {
        // 1. Headline
        TextView headline = adView.findViewById(R.id.ad_headline);
        if (headline != null) {
            headline.setText(ad.getHeadline());
            adView.setHeadlineView(headline);
        }

        // 2. Body
        TextView body = adView.findViewById(R.id.ad_body);
        if (body != null) {
            if (ad.getBody() != null) {
                body.setText(ad.getBody());
                body.setVisibility(View.VISIBLE);
            } else {
                body.setVisibility(View.GONE);
            }
            adView.setBodyView(body);
        }

        // 3. MediaView
        MediaView mediaView = adView.findViewById(R.id.ad_media);
        if (mediaView != null) {
            adView.setMediaView(mediaView);
            if (ad.getMediaContent() != null) {
                mediaView.setMediaContent(ad.getMediaContent());
                mediaView.setVisibility(View.VISIBLE);
            } else {
                mediaView.setVisibility(View.GONE);
            }
        }

        // 4. Icon
        ImageView iconView = adView.findViewById(R.id.ad_icon);
        if (iconView != null) {
            NativeAd.Image icon = ad.getIcon();
            if (icon != null) {
                iconView.setImageDrawable(icon.getDrawable());
                iconView.setVisibility(View.VISIBLE);
            } else {
                iconView.setVisibility(View.GONE);
            }
            adView.setIconView(iconView);
        }

        // 5. CTA Button
        Button ctaButton = adView.findViewById(R.id.ad_call_to_action);
        if (ctaButton != null) {
            if (ad.getCallToAction() != null) {
                ctaButton.setText(ad.getCallToAction());
                ctaButton.setVisibility(View.VISIBLE);
            } else {
                ctaButton.setVisibility(View.GONE);
            }
            adView.setCallToActionView(ctaButton);
        }

        // 6. يجب أن يكون آخر خطوة — بعد ربط كل الـ views
        adView.setNativeAd(ad);
    }

    // ════════════════════════════════════════════════════════
    //  REWARDED
    // ════════════════════════════════════════════════════════

    public void loadRewardedAd(OnRewardCallback callback) {
        if (!ADS_ENABLED || activity == null) return;
        RewardedAd.load(activity, REWARDED_ID,
            new AdRequest.Builder().build(),
            new RewardedAdLoadCallback() {
                @Override public void onAdLoaded(@NonNull RewardedAd ad) {
                    mRewardedAd = ad;
                    if (callback != null) callback.onReady();
                }
                @Override public void onAdFailedToLoad(@NonNull LoadAdError e) {
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
                // [إصلاح] cast صريح لتجنب الـ ambiguity
                loadRewardedAd((OnRewardCallback) null);
            }
            @Override public void onAdFailedToShowFullScreenContent(@NonNull AdError e) {
                mRewardedAd = null;
                if (callback != null) callback.onSkipped();
            }
        });
        mRewardedAd.show(activity, item -> {
            if (callback != null) callback.onRewarded();
        });
    }

    // ════════════════════════════════════════════════════════
    //  Position Helpers
    // ════════════════════════════════════════════════════════

    public static boolean isAdPosition(int position) {
        return ADS_ENABLED && position > 0
            && (position + 1) % NATIVE_AD_INTERVAL == 0;
    }

    public static int getDataPosition(int adapterPosition) {
        if (!ADS_ENABLED) return adapterPosition;
        return adapterPosition - (adapterPosition / NATIVE_AD_INTERVAL);
    }

    public static int getAdapterCount(int dataCount) {
        if (!ADS_ENABLED || dataCount == 0) return dataCount;
        return dataCount + (dataCount / (NATIVE_AD_INTERVAL - 1));
    }

    public void destroy() { instance = null; }

    // ════════════════════════════════════════════════════════
    //  Interfaces
    // ════════════════════════════════════════════════════════

    public interface OnRewardCallback {
        default void onReady()    {}
        default void onRewarded() {}
        default void onSkipped()  {}
    }

    public interface NativeAdsLoadedCallback {
        void onLoaded(java.util.List<NativeAd> ads);
    }

    // ════════════════════════════════════════════════════════
    //  Legacy compatibility — بدون overloads متعارضة
    //  [إصلاح] الـ Legacy methods أصبحت محددة بـ cast صريح
    // ════════════════════════════════════════════════════════

    /** @deprecated استخدم loadRewardedAd(OnRewardCallback) */
    @Deprecated
    public void loadRewardedAdLegacy(Runnable onLoaded) {
        loadRewardedAd(new OnRewardCallback() {
            @Override public void onReady() {
                if (onLoaded != null) onLoaded.run();
            }
        });
    }

    /** @deprecated استخدم showRewardedAd(Activity, OnRewardCallback) */
    @Deprecated
    public void showRewardedAdLegacy(Runnable onRewarded) {
        showRewardedAd(null, new OnRewardCallback() {
            @Override public void onRewarded() {
                if (onRewarded != null) onRewarded.run();
            }
        });
    }

    /** @deprecated استخدم showInterstitialAd(Activity, Runnable) */
    @Deprecated
    public void showInterstitialAd() {
        showInterstitialAd(null, null);
    }
}
