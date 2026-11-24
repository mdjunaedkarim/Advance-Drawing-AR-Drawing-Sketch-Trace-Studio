# ProGuard / R8 keep rules for Google Play Release builds

# Preserve line numbers and annotations for Play Console crash deobfuscation
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep WebView JavaScript bridge methods
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep OpenCV native JNI classes
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# Keep ML Kit Selfie Segmentation & CameraX
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class androidx.camera.** { *; }

# Keep Google Mobile Ads & Play Billing
-keep class com.google.android.gms.ads.** { *; }
-keep class com.android.billingclient.** { *; }

# Keep custom Android Views and native methods
-keepclasseswithmembers class * {
    native <methods>;
}
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Suppress optional Guava annotation warnings
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn com.google.j2objc.annotations.**
