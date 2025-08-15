# Keep generic signatures & annotations (Retrofit/Gson reflection)
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable

# --- Your models & API (Gson/Retrofit) ---
-keep class com.mmalyil.smartdocai.model.** { *; }
-keep interface com.mmalyil.smartdocai.api.** { *; }
-keepclassmembers class * { @com.google.gson.annotations.SerializedName <fields>; }
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }

# Retrofit / OkHttp / Gson libs
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn com.google.gson.**
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class com.google.gson.** { *; }

# --- Apache POI + XMLBeans + OOXML ---
-keep class org.apache.poi.** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.schemas.**

# StAX / Woodstox used by POI
-keep class javax.xml.stream.** { *; }
-keep class org.codehaus.stax2.** { *; }
-keep class com.ctc.wstx.** { *; }
-dontwarn javax.xml.stream.**
-dontwarn org.codehaus.stax2.**
-dontwarn com.ctc.wstx.**

# Silence desktop-only/optional bits referenced by POI/log4j
-dontwarn java.awt.**
-dontwarn org.osgi.framework.**
-dontwarn org.apache.logging.log4j.**
-dontwarn aQute.bnd.annotation.spi.**

# ML Kit / CameraX (safe to keep)
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# PdfBox-Android
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**

# Your app entry points
-keep public class com.mmalyil.smartdocai.**Activity { *; }
-keep public class com.mmalyil.smartdocai.SmartDocAIApp { *; }