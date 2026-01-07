# Add project specific ProGuard rules here.

# Gson rules - preserve generic type information
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# Keep Apache POI classes
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**

# Keep iText PDF classes
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**

# Keep PDF Viewer
-keep class com.github.barteksc.** { *; }

# Missing classes - dontwarn rules
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn org.osgi.framework.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.slf4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn com.graphbuilder.**
-dontwarn org.bouncycastle.**
-dontwarn org.apache.batik.**
-dontwarn org.apache.commons.logging.**

# Keep logging classes
-keep class org.slf4j.** { *; }
-keep class org.apache.logging.log4j.** { *; }

# Keep model classes
-keep class com.docreader.models.** { *; }

# Keep views
-keep class com.docreader.views.** { *; }
