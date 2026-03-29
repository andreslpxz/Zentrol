# D2D Remote ProGuard Rules

# Keep Gson serialization
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.d2dremote.model.** { *; }
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep AccessibilityService
-keep class com.d2dremote.service.TouchAccessibilityService { *; }
