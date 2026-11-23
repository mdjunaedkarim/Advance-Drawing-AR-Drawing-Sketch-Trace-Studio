# ProGuard / R8 keep rules for Google Play Release builds

# Keep WebView JavaScript bridge methods
-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep OpenCV native JNI classes
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# Keep Google Mobile Ads & Play Billing
-keep class com.google.android.gms.ads.** { *; }
-keep class com.android.billingclient.** { *; }
