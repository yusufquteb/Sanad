# 📦 دليل رفع تطبيق سند على Google Play Store
## المرحلة 7.4 — Production Readiness

---

## ✅ قائمة التحقق الكاملة قبل الرفع

---

## 1️⃣ إعداد الـ APK / AAB

### 1.1 توليد Signed Bundle
```
Build → Generate Signed Bundle / APK
  → Android App Bundle (.aab)    ← هذا ما يطلبه Play Store
  → اختر keystore
  → Release build
```

### 1.2 إعدادات ضرورية في build.gradle
```groovy
defaultConfig {
    applicationId "com.missingpersons.app"  // لا تغيّره بعد الرفع الأول
    minSdk 24          // Android 7.0 فأعلى
    targetSdk 35       // آخر API level مدعوم
    versionCode 10     // زِد بـ 1 في كل رفع
    versionName "2.0.0"
}
```

### 1.3 التحقق من الحجم
- `.aab` مقبول حتى 150 MB (بدون splits)
- مع `bundle splits`: كل حزمة لغة/كثافة أصغر بكثير
- تحقق: `Build → Analyze APK`

---

## 2️⃣ حساب Google Play Console

### 2.1 إنشاء حساب
- الموقع: https://play.google.com/console
- الرسوم: **$25 لمرة واحدة** (ليس اشتراكاً)
- نوع الحساب: Personal أو Organization

### 2.2 متطلبات الهوية
- حساب Google شخصي
- بطاقة ائتمان أو Prepaid card للدفع الأولي
- هوية شخصية (ID verification — مطلوب منذ 2023)

---

## 3️⃣ بيانات التطبيق على Console

### 3.1 اسم التطبيق
```
الاسم الرئيسي:    سند | Sanad
Short Description: منصة مجانية للبحث عن المفقودين وإعادة لمّ شملهم
Full Description:  (انظر القسم 6 أدناه)
```

### 3.2 التصنيف
```
Category:     Social
Content Rating: Everyone  (لكن يحتاج IARC questionnaire)
```

### 3.3 الدول المستهدفة
```
المرحلة الأولى:  مصر ✅
مرحلة التوسع:   كل الدول العربية
```

---

## 4️⃣ الصور والأصول المطلوبة

| العنصر | المقاس | ملاحظة |
|--------|--------|--------|
| App Icon | 512×512 px PNG | بدون شفافية، خلفية صلبة |
| Feature Graphic | 1024×500 px | للبانر الرئيسي في Store |
| Phone Screenshots | 1080×1920 أو 1080×2340 | مطلوب 2 على الأقل |
| 7" Tablet (اختياري) | 1200×1920 | موصى به |
| 10" Tablet (اختياري) | 1920×1200 | موصى به |
| Promo Video (اختياري) | YouTube link | 30-120 ثانية |

### نصائح Screenshots
- عرض الصفحة الرئيسية
- عرض شاشة البحث
- عرض تفاصيل حالة
- عرض الخريطة
- عرض لوحة الأوائل

---

## 5️⃣ سياسة الخصوصية (إلزامية)

### 5.1 المتطلبات
Privacy Policy **إلزامية** لأن التطبيق:
- يجمع بيانات شخصية (اسم، صور)
- يستخدم Location
- يخزن بيانات أطفال (بلاغات)

### 5.2 ما يجب أن تغطيه
```
1. البيانات التي تُجمع (الاسم، الصور، الموقع، FCM token)
2. كيف تُستخدم (لإيجاد المفقودين، لا لأغراض تجارية)
3. مع من تُشارك (Firebase/Google فقط)
4. حقوق المستخدم (الحذف، التصحيح)
5. سياسة الأطفال (COPPA compliance)
6. بيانات التواصل
```

### 5.3 رابط السياسة
```
https://sanad-app.web.app/privacy
```
يجب أن يكون الرابط:
- متاحاً للعامة (بدون تسجيل)
- يفتح بدون تنزيل
- باللغة العربية (+ إنجليزي موصى به)

### 5.4 خيارات مجانية لاستضافة Privacy Policy
- **Firebase Hosting**: `firebase deploy --only hosting`
- **GitHub Pages**: مجاني وسريع
- **Carrd.co**: لصفحات بسيطة

---

## 6️⃣ وصف التطبيق الكامل (Full Description)

```
سند هو منصة مجانية تساعد الأسر المصرية في البحث عن ذويهم المفقودين 
والتواصل بشكل آمن وسريع.

🔍 ما يمكنك فعله:
• رفع بلاغات المفقودين مع الصور والتفاصيل
• البحث بالوجه باستخدام الذكاء الاصطناعي
• تصفح الحالات المفتوحة على الخريطة
• التواصل مع أسر المفقودين مباشرةً
• متابعة حالة بلاغاتك لحظةً بلحظة

🤖 الذكاء الاصطناعي في خدمتك:
يقارن التطبيق صور البلاغات تلقائياً ويُنبّهك عند وجود تطابق محتمل

🚨 تنبيه الأطفال المفقودين:
عند اختفاء طفل، يصل تنبيه فوري لجميع المستخدمين في نفس المحافظة

🏆 نظام المكافآت:
كل إسهام في إيجاد المفقودين يُضيف نقاطاً لحسابك

📊 الإحصائيات والنجاح:
تابع أعداد الحالات المحلولة وقصص لمّ شمل الأسر

التطبيق مجاني تماماً وبدون إعلانات مزعجة.
كل بلاغ يمكن أن يغيّر حياة أسرة كاملة.
```

---

## 7️⃣ تصنيف المحتوى (Content Rating)

### ملء استبيان IARC
في Play Console → Rating:
```
Category: Social Networking
Questions:
- User-generated content: ✅ YES (photos, comments)
- User interaction: ✅ YES (chat)
- Shares location: ✅ YES
- Deals with sensitive topics: ✅ YES (missing persons)
```

**النتيجة المتوقعة:** `Teen` أو `Everyone`

> **مهم:** لأن التطبيق يحتوي على صور أشخاص ومعلومات حساسة،
> قد يطلب Play Store توضيحاً إضافياً. أجب بصدق.

---

## 8️⃣ Data Safety Form (إلزامي منذ 2022)

ملأ في Play Console → Data Safety:

| البيانات | تُجمع؟ | سبب | مشاركة؟ |
|---------|--------|-----|---------|
| الاسم | ✅ | تعريف المستخدم | ❌ |
| البريد الإلكتروني | ✅ | تسجيل الدخول | ❌ |
| الصور (Photos and videos) | ✅ | بلاغات المفقودين + استخراج بصمة الوجه للمطابقة | ❌ |
| الموقع التقريبي | ✅ | فلترة المحافظة | ❌ |
| Device ID | ✅ | FCM Notifications | ❌ |
| Crash Logs | ✅ | Firebase Crashlytics | Google |

**Data Encryption:** YES (Firebase يشفّر البيانات أثناء النقل — TLS)
**Data Deletion:** YES (المستخدم يحذف حسابه من الإعدادات)

### ⚠️ بند حرج — بصمة الوجه (Face Embedding)

التطبيق يستخرج **بصمة رقمية للوجه** (float vector) من كل صورة بلاغ عبر
نموذج AdaFace/TFLite محلياً على الجهاز، ويخزّنها في Firebase للمطابقة
التلقائية. هذا **بيانات بيومترية حساسة** حتى لو لم يظهر تصنيف "Biometric"
صراحةً في نموذج Data Safety — يجب مراعاة ما يلي حتى لا يُرفض التطبيق:

1. **في نموذج Data Safety**: ضمن فئة "Photos and videos" أو "Personal
   info → Other info"، فعّل "Collected" و"Processed ephemerally" = **لا**
   (لأنها تُخزَّن)، ووضّح صراحةً في حقل الوصف أن الصور تُستخدم أيضاً
   لاستخراج ميزات وجه (facial features) لأغراض المطابقة.
2. **In-App Prominent Disclosure**: يجب عرض شاشة/حوار داخل التطبيق نفسه
   (وليس فقط رابط خارجي) يشرح أن الصورة سيُحلَّل وجهها بالذكاء الاصطناعي،
   **قبل** طلب صلاحية الكاميرا/المعرض مباشرة، مع زر موافقة صريح.
   ✅ تم تنفيذ هذا في الكود
   (`PermissionHelper.ensureBiometricConsent`) — يظهر قبل أول استخدام
   لكاميرا/معرض في `SearchActivity` (بحث بالوجه)، `ReportActivity`
   (بلاغ مفقود/معثور)، و`FoundPersonActivity`.
3. **سياسة الخصوصية** (رابط https://sanad-app.web.app/privacy) يجب أن
   تتضمن فقرة صريحة عن: أي بيانات بيومترية تُجمع (بصمة الوجه)، الغرض
   الحصري منها (مطابقة بلاغات المفقودين)، مدة الاحتفاظ، وآلية الحذف عند
   الطلب — هذا شرط منفصل عن نموذج Data Safety نفسه ولا يُغني أحدهما عن
   الآخر.
4. **الأطفال**: نظراً لاحتمال ظهور صور قاصرين في بلاغات مفقودين، تأكد أن
   وصف "Content Rating" وسياسة الخصوصية يوضحان أن معالجة صور القاصرين
   تقتصر على الغرض الإنساني (مطابقة/إيجاد) فقط، وأن نظام
   `AbuseReportHelper` متاح للإبلاغ عن أي إساءة استخدام.

---

## 9️⃣ سياسة المحتوى

### ما يجب التأكد منه:
```
✅ لا يوجد محتوى مسيء للأطفال
✅ صور المفقودين ليست sensational
✅ يوجد نظام إبلاغ عن المحتوى المسيء (AbuseReportHelper ✅)
✅ لا يُمكّن التطبيق الاتصال المباشر بأطراف غير موثوقة بدون مراجعة
```

### ما قد يرفضه Play Store:
- صور مثيرة للإثارة في بيانات المفقودين
- انتحال هوية جهات رسمية
- ادعاء التواصل مع الشرطة مباشرةً

---

## 🔟 قبل كل إصدار جديد

### Checklist رفع الإصدار
```
[ ] versionCode تم رفعه
[ ] versionName تم تحديثه
[ ] Release notes كُتبت (عربي + إنجليزي)
[ ] تم اختبار Release build على جهاز حقيقي
[ ] Firebase Crashlytics اختُبر
[ ] لا يوجد API key مكشوف في الكود
[ ] google-services.json محدّث
[ ] ProGuard mapping رُفع على Crashlytics
[ ] تم اختبار In-App Update
[ ] Screenshots محدّثة (إذا تغيّرت الواجهة)
```

---

## 1️⃣1️⃣ Release Notes النموذج

```
الإصدار 2.0.0 — ما الجديد؟

✨ ميزات جديدة:
• نظام تنبيه Amber Alert للأطفال المفقودين
• إضافة قصص النجاح تلقائياً عند إيجاد المفقود
• زر المشاركة في كل بطاقة
• تحديث تلقائي من داخل التطبيق

🔧 تحسينات:
• تحسين الأداء وسرعة تحميل الصور
• دعم أفضل لقارئات الشاشة TalkBack
• تقوية أمان قواعد Firebase

🐛 إصلاح أخطاء:
• إصلاح Widget عند انقطاع الإنترنت
• إصلاح تحميل البيانات في الإعدادات
```

---

## 1️⃣2️⃣ روابط مفيدة

| الموقع | الرابط |
|--------|--------|
| Play Console | https://play.google.com/console |
| Policy Center | https://support.google.com/googleplay/android-developer/answer/9859455 |
| Data Safety Guide | https://support.google.com/googleplay/android-developer/answer/10787469 |
| App Signing | https://developer.android.com/studio/publish/app-signing |
| IARC Rating | https://www.globalratings.com/for-developers.aspx |

---

## ⚠️ أشياء تؤدي للرفض الفوري

1. **API key مكشوف** في الكود أو AndroidManifest
2. **الصور تحتوي معلومات حساسة** (أرقام بطاقات، كلمات مرور)
3. **Privacy Policy غير موجودة** أو لا تتطابق مع الاستخدام الفعلي
4. **targetSdk قديم** — يجب أن يكون ≥ 35 (Android 15) قبل 31 أغسطس 2026
   للتطبيقات الحالية (المشروع الآن على `targetSdk 35` ✅). راجع
   [Target API level requirements](https://developer.android.com/google/play/requirements/target-sdk)
   قبل أي رفع جديد للتأكد من عدم تغيّر الموعد النهائي أو المتطلب لأحدث
   إصدار Android.
5. **Permissions غير مبررة** (لا تطلب إلا ما تحتاجه)
6. **TestFlight/Debug code** في Release build

---

*آخر تحديث: إصدار 2.0.0 — المرحلتان 6 و7*
