# ProGuard rules for PRONTO

# Keep all PRONTO classes (prevent R8 from removing)
-keep class com.example.pronto.** { *; }
-keepclassmembers class com.example.pronto.** { *; }

# Keep CallReceiver (BroadcastReceiver)
-keep class com.example.pronto.CallReceiver { *; }
-keepclassmembers class com.example.pronto.CallReceiver { *; }

# Keep CallerIdService
-keep class com.example.pronto.CallerIdService { *; }
-keepclassmembers class com.example.pronto.CallerIdService { *; }

# Keep MainActivity
-keep class com.example.pronto.MainActivity { *; }
-keepclassmembers class com.example.pronto.MainActivity { *; }

# Keep JavaScript interfaces for WebView bridge
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Prevent stripping of inner classes used as JS bridges
-keepclassmembers class com.example.pronto.CallerIdService$AndroidBridge {
    public *;
}

# Keep annotations
-keepattributes *Annotation*

# Preserve line numbers for stack traces
-keepattributes SourceFile,LineNumberTable

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}
