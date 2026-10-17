# Homa AI ProGuard Rules

# Keep WebView JavaScript interface
-keepclassmembers class ai.homa.app.HomaWebInterface {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep billing classes
-keep class com.android.billingclient.** { *; }
-keep class ir.myket.** { *; }

# Keep Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Keep model classes used in billing
-keep class ai.homa.app.billing.** { *; }
-keep class ai.homa.app.models.** { *; }

# Remove debug logs in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}