# ══════════════════════════════════════════════════════════════
#  proguard-rules.pro — قواعد ProGuard الشاملة
#  [المرحلة 7.3] سند — Sanad App
# ══════════════════════════════════════════════════════════════

# ── الاحتفاظ بمعلومات الأخطاء للـ Crash Reports ──────────────
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
-renamesourcefileattribute SourceFile

# ── Annotations والـ Signatures ──────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ══════════════════════════════════════════════════════════════
#  Firebase
# ══════════════════════════════════════════════════════════════

-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ── Firebase Database Models (يجب حمايتها من obfuscation) ──
-keep class com.missingpersons.app.models.ReportModel { *; }
-keep class com.missingpersons.app.models.UserModel { *; }
-keep class com.missingpersons.app.models.ReportEntity { *; }
-keep class com.missingpersons.app.domain.model.Person { *; }

# ── Firebase يستخدم Reflection لقراءة الـ Fields ──
-keepclassmembers class com.missingpersons.app.models.** {
    public <fields>;
    public <init>();
}

# ══════════════════════════════════════════════════════════════
#  Room Database
# ══════════════════════════════════════════════════════════════

-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.missingpersons.app.models.AppDatabase { *; }
-keep class com.missingpersons.app.models.ReportDao { *; }
-keep class com.missingpersons.app.models.ReportEntity { *; }
-dontwarn androidx.room.**

# ══════════════════════════════════════════════════════════════
#  TensorFlow Lite (Face Recognition)
# ══════════════════════════════════════════════════════════════

-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
-dontwarn org.tensorflow.**
-dontwarn org.tensorflow.lite.**

# احتفظ بـ FaceEmbeddingManager كاملاً (يستخدم TFLite بشكل مباشر)
-keep class com.missingpersons.app.utils.FaceEmbeddingManager { *; }
-keep class com.missingpersons.app.utils.TFLiteFaceRecognizer { *; }
-keep class com.missingpersons.app.utils.FaceMatcher { *; }

# ══════════════════════════════════════════════════════════════
#  ML Kit (Face Detection)
# ══════════════════════════════════════════════════════════════

-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ══════════════════════════════════════════════════════════════
#  OSMDroid (Maps)
# ══════════════════════════════════════════════════════════════

-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# ══════════════════════════════════════════════════════════════
#  Coil (Image Loading)
# ══════════════════════════════════════════════════════════════

-dontwarn coil.**
-keep class coil.** { *; }

# ══════════════════════════════════════════════════════════════
#  WorkManager (Background Tasks)
# ══════════════════════════════════════════════════════════════

-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Workers المحددة ──────────────────────────────────────────
-keep class com.missingpersons.app.workers.BackgroundMatchWorker { *; }
-keep class com.missingpersons.app.workers.ChatCleanupWorker { *; }
-keep class com.missingpersons.app.workers.DailyReportWorker { *; }
-keep class com.missingpersons.app.workers.ProximityCheckWorker { *; }

# ══════════════════════════════════════════════════════════════
#  ZXing (QR Code)
# ══════════════════════════════════════════════════════════════

-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ══════════════════════════════════════════════════════════════
#  Lottie (Animations)
# ══════════════════════════════════════════════════════════════

-dontwarn com.airbnb.lottie.**
-keep class com.airbnb.lottie.** { *; }

# ══════════════════════════════════════════════════════════════
#  MPAndroidChart
# ══════════════════════════════════════════════════════════════

-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# ══════════════════════════════════════════════════════════════
#  AdMob
# ══════════════════════════════════════════════════════════════

-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# ══════════════════════════════════════════════════════════════
#  Play Core (In-App Update + Review)
# ══════════════════════════════════════════════════════════════

-keep class com.google.android.play.core.** { *; }
-dontwarn com.google.android.play.core.**

# ══════════════════════════════════════════════════════════════
#  FCM Service
# ══════════════════════════════════════════════════════════════

-keep class com.missingpersons.app.services.FCMService { *; }

# ══════════════════════════════════════════════════════════════
#  Widget
# ══════════════════════════════════════════════════════════════

-keep class com.missingpersons.app.widget.MissingPersonsWidget { *; }

# ══════════════════════════════════════════════════════════════
#  Activities (منع rename للـ deep links وIntents)
# ══════════════════════════════════════════════════════════════

-keep class com.missingpersons.app.activities.** { *; }
-keep class com.missingpersons.app.MyApplication { *; }

# ══════════════════════════════════════════════════════════════
#  Utils الحيوية
# ══════════════════════════════════════════════════════════════

-keep class com.missingpersons.app.utils.ShareHelper { *; }
-keep class com.missingpersons.app.utils.AnalyticsHelper { *; }
-keep class com.missingpersons.app.utils.NotificationHelper { *; }
-keep class com.missingpersons.app.utils.AmberAlertManager { *; }
-keep class com.missingpersons.app.utils.CrossMatchManager { *; }
-keep class com.missingpersons.app.utils.RoleManager { *; }
-keep class com.missingpersons.app.utils.PointsManager { *; }
-keep class com.missingpersons.app.utils.InAppUpdateManager { *; }
-keep class com.missingpersons.app.utils.AccessibilityUtils { *; }
-keep class com.missingpersons.app.utils.PerformanceConfig { *; }

# ══════════════════════════════════════════════════════════════
#  General
# ══════════════════════════════════════════════════════════════

-dontwarn javax.annotation.**
-dontwarn sun.misc.**
-dontwarn java.lang.invoke.**
-dontwarn okhttp3.**
-dontwarn okio.**

# احتفظ بـ Enum values (Firebase يستخدمها أحياناً كـ String)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# احتفظ بـ Parcelable
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# احتفظ بـ Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# احتفظ بـ R class
-keepclassmembers class **.R$* {
    public static <fields>;
}

# ── ViewBinding ────────────────────────────────────────────
-keep class **.databinding.** { *; }

# ── Kotlin metadata (مطلوب للـ Coil الذي يستخدم Kotlin) ───
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
