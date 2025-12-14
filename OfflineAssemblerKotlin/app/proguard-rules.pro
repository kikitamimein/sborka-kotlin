# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in Android SDK proguard-rules.pro

# Keep Apache POI classes
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.openxmlformats.** { *; }
-keep class schemaorg_apache_xmlbeans.** { *; }

# Keep Gson classes
-keep class com.google.gson.** { *; }
-keep class com.offlineassembler.model.** { *; }

# Don't warn about missing classes
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.**
