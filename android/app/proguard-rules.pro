# AetherX release ProGuard rules

-keepattributes *Annotation*,InnerClasses,Signature,Exceptions

# Capacitor
-keep class com.getcapacitor.** { *; }
-keep @com.getcapacitor.annotation.CapacitorPlugin class * { *; }
-keep class * extends com.getcapacitor.Plugin { *; }
-keepclassmembers class * extends com.getcapacitor.Plugin {
    @com.getcapacitor.PluginMethod <methods>;
}

# AetherX native wallpaper layer
-keep class com.aetherx.livewallpaper.** { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# AndroidX general
-dontwarn androidx.**

# Keep info/error logs because AETHERX release APK must expose build and wallpaper diagnostics.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
