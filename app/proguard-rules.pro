-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface

-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn sun.misc.Unsafe
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

-keepattributes SourceFile,LineNumberTable

-keep class com.netease.nimlib.** { *; }
-keep class com.netease.nim.** { *; }
-dontwarn com.netease.nimlib.**
-dontwarn com.netease.nim.**
-renamesourcefileattribute SourceFile
