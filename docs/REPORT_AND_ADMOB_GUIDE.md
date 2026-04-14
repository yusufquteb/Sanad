# 📊 تقرير التشخيص الشامل — تطبيق سند
## مراجعة من زوايا: Build Error · Frontend · Backend · UX · AdMob · Play Store

---

## 🔴 المشكلة الفورية — خطأ البناء

### الخطأ
```
error: package com.google.android.play.core.tasks does not exist
import com.google.android.play.core.tasks.Task;
```

### السبب الجذري
مكتبة `play:app-update:2.x` الجديدة تخلّت عن `com.google.android.play.core.tasks.Task`
وأصبحت تستخدم GMS Tasks API مباشرةً (`com.google.android.gms.tasks.Task`).
الـ `InAppUpdateManager.java` الذي أُرسل في المرحلة السابقة كان يحتوي على هذا الـ import.

### الحل
**الملف المُصلَّح: `InAppUpdateManager.java`** (في هذا الـ ZIP)

التغيير: حذف `import com.google.android.play.core.tasks.Task;`
واستخدام `.addOnSuccessListener()` و `.addOnFailureListener()` مباشرة دون الحاجة لـ Task.

---

## 🔴 مشكلة UX + AdMob Policy — خطرة

### CaseDetailActivity تعرض Interstitial
```java
// السطر 73 في CaseDetailActivity.java
AdsManager.getInstance(this).showInterstitialAd(this, null);
```

**هذا خطأ مزدوج:**

| الجانب | المشكلة |
|--------|---------|
| UX | يقاطع المستخدم قبل أن يرى تفاصيل الحالة |
| AdMob Policy | يُعتبر انتهاكاً صريحاً لسياسة AdMob |
| Play Store | يمكن أن يتسبب في تعليق الحساب |
| الهدف | المستخدم يأتي ليرى معلومات شخص مفقود — الإعلان يعرقله |

**الحل:** أزل هذا السطر من `CaseDetailActivity.java`:
```java
// احذف هذا السطر بالكامل (سطر 73):
AdsManager.getInstance(this).showInterstitialAd(this, null);
```

---

## 🔴 مشكلة بنائية — Native Ad غير مكتمل

### BrowseActivity — AdViewHolder
```java
// السطر 730 في BrowseActivity.java
void bind(NativeAd ad) {
    adHeadline.setText(ad.getHeadline());
    if (ad.getCallToAction() != null) adCta.setText(ad.getCallToAction());
    adView.setNativeAd(ad);  // ← يُفعَّل قبل ربط MediaView و Icon
}
```

**المشاكل:**
1. لا يربط `MediaView` بـ `ad.getMediaContent()` → الصورة لا تظهر
2. لا يربط `ad_icon` → أيقونة المُعلِن مفقودة
3. لا يربط `ad_body` → النص الثانوي مفقود
4. `setNativeAd()` يُستدعى مبكراً قبل ربط كل الـ views

**الحل:** استخدم `AdsManager.bindNativeAdToView(ad, adView)` في الملف المُصلَّح.

---

## 🟡 مشكلة تصميم — Dark Mode معطّل لكن غير مُبلَّغ عنه

### values-night/colors.xml
الملف موجود لكن يحتوي على **نفس ألوان Light Mode تماماً**.
هذا يعني:
- تطبيق "يدّعي" دعم الـ Dark Mode لكن لا يطبّقه
- الأبيض على أبيض في Dark Mode = نصوص غير مقروءة

**الخيار 1 (موصى به):** إضافة `uiMode` flag في Manifest لإلغاء الـ Dark Mode كلياً:
```xml
<!-- في AndroidManifest.xml عند كل Activity أو في Application -->
android:forceDarkAllowed="false"
```

**الخيار 2:** تطبيق ألوان Dark Mode حقيقية في `values-night/colors.xml`:
```xml
<color name="md_background">#0F172A</color>
<color name="md_surface">#1E293B</color>
<color name="md_on_surface">#F1F5F9</color>
<color name="md_on_surface_variant">#94A3B8</color>
```

---

## 🟡 مشكلة اللغة — 4 مكونات بدون LanguageHelper

### المكونات المفقودة
```
FilterBottomSheetFragment.java  ← يفتح كـ bottom sheet من BrowseActivity
                                  إذا غيّر المستخدم اللغة ستبقى عربية
```

**الحل:** أضف في `FilterBottomSheetFragment.java`:
```java
@Override
public void onAttach(@NonNull Context context) {
    super.onAttach(LanguageHelper.applyLanguage(context));
}
```

*ملاحظة: AdminDashboardReportAdapter و BrowseDiffCallback و BrowseViewModel
هي Adapters/ViewModels وليست Activities — لا تحتاج LanguageHelper.*

---

## 🟡 مشكلة AdMob — IDs التجريبية في Production

### AdsManager.java
```java
// ⚠️ هذه IDs اختبارية — ستعطي $0 أرباح في Production
public static final String BANNER_ID       = "ca-app-pub-3940256099942544/6300978111";
public static final String INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712";
```

**يجب استبدالها بـ IDs حقيقية قبل الرفع.**
انظر القسم "دليل AdMob الكامل" أدناه.

---

## 🟡 مشكلة Performance — الـ RecyclerViews بدون تحسين

### ما وجدناه
لا يوجد `setHasFixedSize(true)` في:
- `BrowseActivity` ← أكثر الشاشات استخداماً
- `NotificationsActivity`
- `MyReportsActivity`
- `LeaderboardActivity`

**الحل الفوري:**
```java
// في كل Activity بعد recyclerView.setLayoutManager(...)
recyclerView.setHasFixedSize(true);
```

---

## 🟡 مشكلة AdMob — APP_ID تجريبي في Manifest

```xml
<!-- AndroidManifest.xml -->
android:value="ca-app-pub-3940256099942544~3347511713"
```

هذا هو الـ Test APP_ID من Google للتطوير.
**يجب استبداله بـ APP_ID الخاص بك** عند النشر.
إذا تركته في Production:
- AdMob Console لن يُسجّل impressions
- قد يُعلَّق الحساب

---

## 🟢 ما هو جيد ✅

| الجانب | التقييم |
|--------|---------|
| نظام الألوان Material 3 | ✅ متسق ومنظّم |
| LanguageHelper في 20 من 21 Activity | ✅ شامل |
| Frequency Cap للـ Interstitial (3 دق) | ✅ صحيح |
| NativeAd في Browse فقط (لا في Detail) | ✅ صحيح |
| لا إعلانات في Chat | ✅ صحيح |
| AdsManager Singleton مع preload | ✅ كفء |
| اتجاه RTL مضبوط | ✅ سليم |
| Theme موحّد بالكامل (Material3 Cairo) | ✅ احترافي |

---
---

# 📱 دليل AdMob الشامل — Native وInterstitial

---

## 1️⃣ إنشاء حساب AdMob والحصول على IDs الحقيقية

### الخطوات
```
1. اذهب إلى: https://apps.admob.com
2. سجّل الدخول بحساب Google
3. اضغط: Add app
4. اختر: Android
5. أدخل اسم التطبيق: Sanad
6. ستحصل على: APP_ID
   مثال: ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX
```

### إنشاء Ad Units
```
في AdMob Console → Your app → Add ad unit

أنشئ 4 وحدات:
  1. Banner       → ستحصل على ID مثل: ca-app-pub-XXX/YYY
  2. Interstitial → ca-app-pub-XXX/YYY
  3. Rewarded     → ca-app-pub-XXX/YYY
  4. Native       → ca-app-pub-XXX/YYY
```

### تحديث التطبيق
```java
// في AdsManager.java — استبدل الـ Test IDs:
public static final String BANNER_ID       = "ca-app-pub-XXXXX/YYYYY"; // الحقيقي
public static final String INTERSTITIAL_ID = "ca-app-pub-XXXXX/YYYYY";
public static final String REWARDED_ID     = "ca-app-pub-XXXXX/YYYYY";
public static final String NATIVE_ID       = "ca-app-pub-XXXXX/YYYYY";
```

```xml
<!-- في AndroidManifest.xml -->
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-XXXXX~YYYYY"/> <!-- APP_ID الحقيقي -->
```

---

## 2️⃣ Banner Ad — التطبيق الصحيح

### في Layout XML
```xml
<!-- في أسفل activity_browse.xml أو activity_home_v2.xml -->
<com.google.android.gms.ads.AdView
    android:id="@+id/ad_banner"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_gravity="bottom"
    ads:adSize="BANNER"
    ads:adUnitId="@string/banner_ad_unit_id"/>
```

### في strings.xml
```xml
<!-- DEBUG -->
<string name="banner_ad_unit_id">ca-app-pub-3940256099942544/6300978111</string>
<!-- RELEASE: استبدل بالـ ID الحقيقي -->
```

### في Java
```java
AdView adBanner = findViewById(R.id.ad_banner);
AdsManager.getInstance(this).loadBannerAd(adBanner);
```

### أين تضع Banner ✅ / ❌
| الشاشة | هل تضع Banner؟ |
|--------|--------------|
| الرئيسية (Home) | ✅ أسفل الشاشة |
| Browse | ✅ أسفل القائمة |
| البحث | ✅ |
| تفاصيل الحالة | ❌ انتهاك |
| المحادثة | ❌ انتهاك |
| نموذج الرفع | ❌ مشتّت |

---

## 3️⃣ Interstitial Ad — التطبيق الصحيح

### متى تعرض Interstitial ✅
```
✅ بعد إرسال بلاغ ناجح (مرة كل بلاغين)
✅ بعد إكمال عملية "وجدت شخصاً"
✅ عند التنقل من قسم لآخر (مرة كل 3 تنقلات)
```

### متى لا تعرض Interstitial ❌
```
❌ عند فتح تفاصيل الحالة  ← تُلغي إمكانية المساعدة
❌ في المحادثة             ← مقاطعة للتواصل
❌ أثناء البحث             ← مُزعج
❌ عند فتح التطبيق         ← أسوأ تجربة ممكنة
❌ أكثر من مرة كل 3 دقائق  ← تُنفّر المستخدمين
```

### الكود
```java
// في ReportActivity — بعد نجاح uploadReport():
AdsManager.getInstance(this).showInterstitialOnSubmit(this, () -> {
    // هذا يُنفَّذ بعد انتهاء الإعلان أو إذا لم يُعرض
    Toast.makeText(this, "✅ تم إرسال البلاغ", Toast.LENGTH_SHORT).show();
    startActivity(new Intent(this, MyReportsActivity.class));
    finish();
});
```

---

## 4️⃣ Native Ad — التطبيق الصحيح في BrowseActivity

### كيف يعمل الآن (مشكلة)
```java
// BrowseActivity.java — AdViewHolder.bind()
void bind(NativeAd ad) {
    adHeadline.setText(ad.getHeadline());         // ✅
    if (ad.getCallToAction() != null)              // ✅
        adCta.setText(ad.getCallToAction());
    adView.setNativeAd(ad);  // ⚠️ مبكر جداً — MediaView غير مربوط
    // ❌ لا MediaView
    // ❌ لا Icon
    // ❌ لا Body
}
```

### الحل الصحيح (في الملف المُصلَّح)
```java
// استبدل bind() بالكامل:
void bind(NativeAd ad) {
    AdsManager.bindNativeAdToView(ad, adView);
    // هذا يربط: Headline + Body + MediaView + Icon + CTA + setNativeAd()
}
```

### layout/item_ad_card.xml — التحقق من IDs
```xml
<!-- تأكد أن هذه الـ IDs موجودة في item_ad_card.xml -->
R.id.ad_media       → MediaView
R.id.ad_icon        → ImageView
R.id.ad_headline    → TextView
R.id.ad_body        → TextView
R.id.ad_call_to_action → Button
```

### تسلسل الـ Items في BrowseActivity Adapter
```
Position 0 → Case Card (data[0])
Position 1 → Case Card (data[1])
Position 2 → Case Card (data[2])
Position 3 → Case Card (data[3])
Position 4 → 🟦 Native Ad        ← (position+1) % 5 == 0
Position 5 → Case Card (data[4])
...
Position 9 → 🟦 Native Ad
```

---

## 5️⃣ Rewarded Ad — التطبيق الصحيح

### متى تستخدم Rewarded
```
✅ "شاهد إعلاناً للحصول على 10 نقاط"
✅ "شاهد إعلاناً لرفع 5 بلاغات بدلاً من 3"
✅ "شاهد إعلاناً لإزالة الـ Banner ليوم كامل"
```

### الكود في Java
```java
// 1. حمّل الإعلان مسبقاً (في onCreate):
AdsManager.getInstance(this).loadRewardedAd(new AdsManager.OnRewardCallback() {
    @Override public void onReady() {
        btnWatchAd.setEnabled(true); // فعّل الزر
    }
});

// 2. عند ضغط المستخدم على زر "شاهد إعلاناً":
AdsManager.getInstance(this).showRewardedAd(this, new AdsManager.OnRewardCallback() {
    @Override public void onRewarded() {
        // امنح المستخدم النقاط أو الميزة
        PointsManager.getInstance().addPoints(uid, PointsManager.ACTION_WATCH_AD);
        Toast.makeText(ctx, "🎉 حصلت على 10 نقاط!", Toast.LENGTH_SHORT).show();
    }
    @Override public void onSkipped() {
        Toast.makeText(ctx, "شاهد الإعلان كاملاً للحصول على النقاط",
            Toast.LENGTH_SHORT).show();
    }
});
```

---

## 6️⃣ متطلبات Play Store للإعلانات

### ما يجب تضمينه في Privacy Policy
```
نستخدم Google AdMob لعرض الإعلانات.
قد تجمع AdMob بيانات مجهولة الهوية لتخصيص الإعلانات.
يمكنك إيقاف تتبع الإعلانات من: إعدادات الجهاز → الخصوصية → الإعلانات.
```

### GDPR Consent (للمستخدمين الأوروبيين — اختياري لمصر)
إذا كنت تستهدف دول أوروبا:
```java
// في MyApplication.onCreate():
ConsentRequestParameters params = new ConsentRequestParameters.Builder()
    .setTagForUnderAgeOfConsent(false)
    .build();
ConsentInformation.getInstance(this).requestConsentInfoUpdate(params, ...);
```

### Content Policy للإعلانات
```
✅ لا تعرض إعلانات مخالفة للمحتوى الحساس (المفقودين)
✅ ضع دائماً علامة واضحة "إعلان" على النيتف
✅ لا تجعل الإعلان يشبه محتوى التطبيق الأصلي
✅ لا تحفّز المستخدمين على الضغط على الإعلانات
```

---

## 7️⃣ اختبار الإعلانات قبل الرفع

### خطوات التحقق
```
1. شغّل التطبيق بالـ Test IDs
2. تحقق من Logcat:
   ✅ "Banner loaded"
   ✅ "Interstitial ready"
   ✅ "Native ad bound: [headline]"
   ✅ "Rewarded ad ready"

3. Test Device ID:
   في MyApplication.java:
   .setTestDeviceIds(Arrays.asList("YOUR_DEVICE_ID"))
   
   الـ Device ID في Logcat:
   "I/Ads: Use RequestConfiguration.Builder().setTestDeviceIds(Arrays.asList(\"XXXX\"))"

4. التحقق من Native Ad:
   - يجب أن تظهر صورة الإعلان (MediaView)
   - يجب أن تظهر الأيقونة
   - زر CTA يجب أن يكون قابل للضغط
```

---

## 📋 قائمة تحقق نهائية قبل الرفع

```
[ ] استبدل Test APP_ID في AndroidManifest.xml
[ ] استبدل Test Ad Unit IDs في AdsManager.java
[ ] احذف سطر Interstitial من CaseDetailActivity.java
[ ] اختبر Native Ad binding مع bindNativeAdToView()
[ ] أضف Privacy Policy تذكر AdMob
[ ] تحقق من عدم ظهور إعلانات في صفحات الأزمة (Chat، Detail)
[ ] في MyApplication: أزل "EMULATOR" من setTestDeviceIds أو استبدله بـ ID حقيقي
[ ] اختبر على جهاز حقيقي (وليس Emulator) قبل الرفع
```
