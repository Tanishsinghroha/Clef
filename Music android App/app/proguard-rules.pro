# Add project specific ProGuard rules here.
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Retrofit & Gson rules for API models
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*
-keep class com.music.app.** { *; }
-keep class com.music.app.api.** { *; }
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn okio.**
-dontwarn javax.annotation.**
