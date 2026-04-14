# ── Firebase ──────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# ── TensorFlow Lite ───────────────────────────────────────
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.**

# ── OSMDroid ──────────────────────────────────────────────
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# ── ML Kit ────────────────────────────────────────────────
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ── Coil ──────────────────────────────────────────────────
-dontwarn coil.**

# ── App models ────────────────────────────────────────────
-keep class com.missingpersons.app.utils.ReportModel { *; }
-keep class com.missingpersons.app.utils.UserModel { *; }
-keepclassmembers class com.missingpersons.app.** {
    public <fields>;
    public <methods>;
}

# ── WorkManager ───────────────────────────────────────────
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# ── General ───────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
-dontwarn javax.annotation.**
