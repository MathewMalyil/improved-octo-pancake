# --- Core keeps you already need ---
-keep class androidx.room.** { *; }
-keepclassmembers class * { @androidx.room.* <methods>; }
-keep class * extends androidx.room.RoomDatabase

-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keep class com.squareup.okhttp3.** { *; }
-keep class com.squareup.retrofit2.** { *; }
-keepclassmembers class * { @com.google.gson.annotations.SerializedName <fields>; }

-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

-keep class androidx.core.content.FileProvider { *; }

# --- Apache POI / OOXML / XMLBeans / StAX (the important part) ---
# POI classes (docx/xlsx/pptx support)
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**

# XMLBeans + all OOXML schemas (ThemeDocument lives here)
-keep class org.apache.xmlbeans.** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.schemas.**

# StAX provider you set via System.setProperty (Woodstox)
-keep class com.ctc.wstx.** { *; }
-keep class org.codehaus.stax2.** { *; }
# If ServiceLoader is used by StAX on some devices:
-keep class javax.xml.stream.** { *; }

# Keep method signatures & annotations (reflection + generics)
-keepattributes Signature
-keepattributes *Annotation*

# --- App-specific (optional, tidy) ---
-keep class com.mmalyil.smartdocai.model.** { *; }
-keep public class com.mmalyil.smartdocai.**Activity { *; }
-keep public class com.mmalyil.smartdocai.MainActivity { *; }

# Retain line numbers in crash logs
-keepattributes SourceFile,LineNumberTable
# --- Keep StAX / Woodstox ---
-keep class javax.xml.stream.** { *; }
-keep class org.codehaus.stax2.** { *; }
-keep class com.ctc.wstx.** { *; }
-dontwarn javax.xml.stream.**
-dontwarn org.codehaus.stax2.**
-dontwarn com.ctc.wstx.**

# --- Suppress desktop-only classes not in Android ---
-dontwarn java.awt.**
-dontwarn org.osgi.framework.**
-dontwarn org.apache.logging.log4j.util.OsgiServiceLocator
-dontwarn org.apache.logging.log4j.util.PropertiesUtil
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**



# --- Apache POI / OOXML / XMLBeans ---
-keep class org.apache.poi.** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.schemas.**


-dontwarn org.apache.logging.log4j.**


# Log4j API references optional OSGi/BND bits – silence them
-dontwarn org.apache.logging.log4j.**
-dontwarn org.osgi.framework.**
-dontwarn aQute.bnd.annotation.spi.**

# Woodstox/StAX + POI/XMLBeans (you already have these, but ensure present)
-keep class javax.xml.stream.** { *; }
-keep class org.codehaus.stax2.** { *; }
-keep class com.ctc.wstx.** { *; }
-dontwarn javax.xml.stream.**
-dontwarn org.codehaus.stax2.**
-dontwarn com.ctc.wstx.**

-keep class org.apache.poi.** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.openxmlformats.schemas.**
-dontwarn org.apache.xmlbeans.**

-keep class org.apache.logging.log4j.** { *; }
-dontwarn org.apache.logging.log4j.**
-dontwarn org.osgi.framework.**
-dontwarn aQute.bnd.annotation.spi.**

-keep class com.mmalyil.smartdocai.**model**.** { *; }
-keep class com.mmalyil.smartdocai.**api**.** { *; }
-keepclassmembers class * { @com.google.gson.annotations.SerializedName <fields>; }
-keepattributes Signature
-keepattributes *Annotation*