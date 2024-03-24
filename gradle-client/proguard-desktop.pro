# =========================================================================
# GLOBAL CONFIGURATION
#
# For reference https://android.googlesource.com/platform/sdk/+/master/files/proguard-android-optimize.txt

-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-optimizations !class/unboxing/enum,!code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 1

# =========================================================================
# UNUSED CLASSES

# Logback dynamic janino
-dontwarn org.codehaus.janino.**
-dontwarn org.codehaus.commons.compiler.**

# Logback networking
-dontwarn jakarta.servlet.**
-dontwarn jakarta.mail.**

# Kotlinx DateTime
-dontwarn kotlinx.datetime.**

# =========================================================================
# KEEP

-keepattributes *Annotation*
-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclasseswithmembernames class * {
    native <methods>;
}

# Kotlin metadata and data classes
-keep class kotlin.Metadata { *; }
-keepclasseswithmembers class * {
    public ** component1();
    <fields>;
}

# Coroutines
-keep class kotlinx.coroutines.swing.SwingDispatcherFactory

# Logging
-keep class org.slf4j.** { *; }
-keep class ch.qos.logback.** { *; }
-keep class org.gradle.client.logic.util.Logback*

# Gradle shaded dependencies
-dontwarn org.gradle.internal.impldep.**
-keep class org.gradle.internal.impldep.** { *; }
