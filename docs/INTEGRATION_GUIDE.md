# 🔧 دليل التكامل — المرحلتان 6 و7
## كيفية دمج الملفات الجديدة في مشروع سند

---

## 📁 هيكل الملفات المُسلَّمة

```
sanad_phase67/
├── app/
│   ├── build.gradle                          ← استبدل الملف الحالي
│   ├── proguard-rules.pro                    ← استبدل الملف الحالي
│   └── src/main/
│       ├── java/com/missingpersons/app/
│       │   ├── utils/
│       │   │   ├── InAppUpdateManager.java   ← ملف جديد
│       │   │   ├── AccessibilityUtils.java   ← ملف جديد
│       │   │   └── PerformanceConfig.java    ← ملف جديد
│       │   └── widget/
│       │       └── MissingPersonsWidget.java ← يستبدل الملف الحالي
│       └── res/
│           ├── values/
│           │   └── strings_additions.xml     ← دمج في strings.xml
│           └── values-en/
│               └── strings_additions.xml     ← دمج في strings.xml
├── firebase_database_rules.json              ← يستبدل الملف الحالي
├── firebase_storage_rules.txt                ← ملف جديد
└── docs/
    └── PLAY_STORE_GUIDE.md                   ← وثيقة مرجعية
```

---

## 🔴 الخطوة 1 — نسخ الملفات الجديدة

### 1.1 Java Utils الجديدة
```
انسخ إلى: app/src/main/java/com/missingpersons/app/utils/
  ✦ InAppUpdateManager.java
  ✦ AccessibilityUtils.java
  ✦ PerformanceConfig.java
```

### 1.2 Widget المحدَّث
```
استبدل: app/src/main/java/com/missingpersons/app/widget/MissingPersonsWidget.java
```
> **ملاحظة:** يحتاج `ReportDao.getLatestApproved(int limit)` — أضف هذه الدالة
> إلى `ReportDao.java` إذا لم تكن موجودة:
> ```java
> @Query("SELECT * FROM reports WHERE status='approved' ORDER BY timestamp DESC LIMIT :limit")
> List<ReportEntity> getLatestApproved(int limit);
> ```

### 1.3 build.gradle
```
استبدل: app/build.gradle بالكامل
التغيير الرئيسي: إضافة Play Core dependencies
  com.google.android.play:app-update:2.1.0
  com.google.android.play:review:2.0.1
```

### 1.4 ProGuard
```
استبدل: app/proguard-rules.pro بالكامل
```

---

## 🟡 الخطوة 2 — دمج الـ Strings

افتح `app/src/main/res/values/strings.xml` وأضف محتوى `strings_additions.xml`
قبل السطر الأخير `</resources>`:

```xml
<!-- الصق هنا محتوى strings_additions.xml (بدون السطر الأول والأخير <resources> و</resources>) -->
```

كرر نفس الشيء مع `values-en/strings.xml`.

> **تجنب التكرار:** ابحث عن كل key قبل إضافته.

---

## 🟡 الخطوة 3 — Firebase Rules

### 3.1 Database Rules
```
انسخ محتوى firebase_database_rules.json
افتح: Firebase Console → Realtime Database → Rules
الصق وانشر
```

### 3.2 Storage Rules
```
انسخ محتوى firebase_storage_rules.txt
افتح: Firebase Console → Storage → Rules
الصق وانشر
```

### 3.3 تفعيل Custom Claims للـ Admin (للـ Storage Rules)
أضف Custom Claim لكل أدمن عبر Firebase Admin SDK أو Cloud Function:
```javascript
// في functions/index.js أضف هذه الدالة (مرة واحدة)
exports.setAdminRole = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'Not signed in');
  // تحقق من أن المُستدعي هو أدمن حالي
  const caller = await admin.auth().getUser(context.auth.uid);
  if (caller.customClaims?.role !== 'admin') {
    throw new functions.https.HttpsError('permission-denied', 'Not an admin');
  }
  await admin.auth().setCustomUserClaims(data.uid, { role: data.role });
  return { success: true };
});
```

---

## 🟢 الخطوة 4 — تفعيل PerformanceConfig في MyApplication

```java
// في MyApplication.java → onCreate()
@Override
public void onCreate() {
    super.onCreate();
    
    // [7.1] تهيئة الأداء (Coil + Firebase keepSynced)
    PerformanceConfig.init(this);
    
    // ... باقي الكود الحالي
}
```

---

## 🟢 الخطوة 5 — تفعيل InAppUpdateManager في NewHomeActivity

```java
// في NewHomeActivity.java

private InAppUpdateManager updateManager;

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // ... الكود الحالي ...
    
    // [7.4] فحص التحديثات
    updateManager = new InAppUpdateManager(this);
    updateManager.checkForUpdate(false); // false = Flexible
}

@Override
protected void onResume() {
    super.onResume();
    if (updateManager != null) updateManager.onResume();
}

@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == InAppUpdateManager.UPDATE_REQUEST_CODE
        && resultCode != RESULT_OK
        && updateManager != null) {
        updateManager.onUpdateCancelled();
    }
}

@Override
protected void onDestroy() {
    super.onDestroy();
    if (updateManager != null) updateManager.unregister();
}
```

---

## 🟢 الخطوة 6 — تفعيل AccessibilityUtils في BrowseActivity Adapter

في `onBindViewHolder` للـ Adapter في `BrowseActivity`:

```java
// بدلاً من:
holder.ivPhoto.setContentDescription("صورة " + name);

// استخدم:
AccessibilityUtils.describePersonPhoto(
    holder.ivPhoto, name, age, location);

// وللبطاقة كاملة:
AccessibilityUtils.describeCaseCard(
    holder.cardView, name, age, gender, location, status, timeAgo);

// توسيع touch target لزر المشاركة الصغير:
AccessibilityUtils.expandTouchTarget(holder.cardView, holder.btnShare);
```

---

## 🟢 الخطوة 7 — تحسين RecyclerViews

في كل Activity فيها RecyclerView:

```java
// في onCreate() بعد setup الـ LayoutManager:
PerformanceConfig.optimizeRecyclerView(recyclerView, true);
// true = إذا حجم الـ RV ثابت (لا يتغير بتغيير المحتوى)
// false = إذا الـ RV قد يتغير حجمه
```

---

## 🟢 الخطوة 8 — تفعيل Widget Sync

في `ReportRepository.java` أو `OfflineSyncManager.java`، بعد كل sync ناجح:

```java
// بعد حفظ البيانات في Room
MissingPersonsWidget.requestUpdate(context);
```

---

## 🟢 الخطوة 9 — إضافة ReportDao method

```java
// في ReportDao.java
@Query("SELECT * FROM reports WHERE status='approved' ORDER BY timestamp DESC LIMIT :limit")
List<ReportEntity> getLatestApproved(int limit);
```

---

## 🔵 الخطوة 10 — Deploy Firebase Functions

```bash
cd functions
npm install
firebase deploy --only functions
```

> الـ Functions الموجودة بالفعل في `index.js` تشمل:
> ✅ `onAmberAlertCreated` — FCM Geo-targeting
> ✅ `onReportResolved` — Success Stories تلقائية
> ✅ `enforceRateLimit` — Rate limiting
> ✅ `moderateReportImage` — Image moderation

---

## ⚠️ تحذيرات مهمة

### InAppUpdateManager يحتاج Google Play
- لا يعمل على أجهزة بدون Google Play Services
- في Debug build لن يجد تحديثات (Play Console فقط)
- اختبره عبر Internal Testing track في Play Console

### Storage Rules و Custom Claims
- يحتاج تفعيل Custom Claims للأدمن مرة واحدة
- بدون ذلك، الأدمن لن يستطيع حذف صور من Storage
- الـ Realtime Database rules لا تتأثر (تقرأ من database مباشرة)

### Widget و Room
- Widget يقرأ من Room فقط — إذا لم تكن هناك بيانات في Room بعد لن يعرض شيئاً
- تأكد أن `OfflineSyncManager` يحفظ في Room عند أول تشغيل

---

## 📊 ملخص التغييرات

| الملف | نوع التغيير | المرحلة |
|-------|-------------|---------|
| `InAppUpdateManager.java` | جديد | 7.4 |
| `AccessibilityUtils.java` | جديد | 7.2 |
| `PerformanceConfig.java` | جديد | 7.1 |
| `MissingPersonsWidget.java` | تحديث (Room) | 1.4 + 7.1 |
| `build.gradle` | تحديث (Play Core) | 7.4 |
| `proguard-rules.pro` | تحديث (شامل) | 7.3 |
| `strings_additions.xml` | جديد (دمج) | 6.3 + 7.2 |
| `firebase_database_rules.json` | تحديث (أمان) | 5.1 |
| `firebase_storage_rules.txt` | جديد | 5.1 + 7 |
| `PLAY_STORE_GUIDE.md` | جديد (وثيقة) | 7.4 |
