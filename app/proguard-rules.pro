# Insta1 Proguard / R8 Rules

# Preserve Kotlin Reflection & Coroutines
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class kotlinx.coroutines.** { *; }

# Preserve Compose Runtime
-keep class androidx.compose.runtime.** { *; }

# Keep data models
-keep class com.example.insta1.model.** { *; }

# Google Play Billing
-keep class com.android.billingclient.** { *; }

# Google Mobile Ads
-keep public class com.google.android.gms.ads.** {
   public *;
}
-keep public class com.google.ads.** {
   public *;
}
