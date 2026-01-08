# ProGuard rules for DocMaster PDF Toolkit
# Google Play Store compliant configuration

# Keep source file names and line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Gson rules - preserve generic type information
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# Keep Apache POI classes (Word document support)
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**

# Keep iText PDF classes (PDF manipulation)
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**

# Missing classes - dontwarn rules (desktop classes not available on Android)
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn javax.imageio.**
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**
-dontwarn org.osgi.framework.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.slf4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn com.graphbuilder.**
-dontwarn org.bouncycastle.**
-dontwarn org.apache.batik.**
-dontwarn org.apache.commons.logging.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.commons.codec.**
-dontwarn org.apache.http.**
-dontwarn de.rototor.pdfbox.**
-dontwarn com.sun.**
-dontwarn sun.**

# Google Tink / Crypto (used by security-crypto)
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }

# Keep logging classes
-keep class org.slf4j.** { *; }
-keep class org.apache.logging.log4j.** { *; }

# Keep app model classes
-keep class com.docreader.models.** { *; }

# Keep app views
-keep class com.docreader.views.** { *; }

# Keep app utils (PDF converters, editors)
-keep class com.docreader.utils.** { *; }

# Keep ViewBinding classes
-keep class * implements androidx.viewbinding.ViewBinding {
    public static * inflate(android.view.LayoutInflater);
    public static * inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
}

# AndroidX rules
-keep class androidx.** { *; }
-dontwarn androidx.**

# Material Design
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# Prevent stripping of native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Optimize
-optimizationpasses 5
-allowaccessmodification
