-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**
-dontwarn java.awt.**
-dontwarn org.osgi.**
-dontwarn aQute.bnd.annotation.spi.**

# --- Silence optional libs not packaged (XMLBeans → Saxon, StAX validation, OSGi/BND) ---
-dontwarn net.sf.saxon.**
-dontwarn org.codehaus.stax2.validation.**
-dontwarn aQute.bnd.annotation.spi.**
-dontwarn org.osgi.framework.**

# (you likely already have these; ok if duplicated)
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.schemas.**
-dontwarn org.apache.poi.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn com.google.gson.**

# --- Silence optional libs not packaged ---
-dontwarn net.sf.saxon.**
-dontwarn org.codehaus.stax2.validation.**
-dontwarn aQute.bnd.annotation.spi.**
-dontwarn org.osgi.framework.**

# (you probably already have these)
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.schemas.**
-dontwarn org.apache.poi.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn com.google.gson.**

# Keep JSON models/annotations
-keep class com.mmalyil.smartdocai.model.** { *; }
-keepclassmembers class * { @com.google.gson.annotations.SerializedName <fields>; }

# Keep Retrofit interfaces and annotations
-keep interface com.mmalyil.smartdocai.api.** { *; }
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }

# Keep generic signatures & annotations
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod

# Optional: retain line numbers
-keepattributes SourceFile,LineNumberTable