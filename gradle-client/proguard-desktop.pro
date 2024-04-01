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

# Android
-dontwarn android.**

# OkHttp3 Crypto
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

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
-keep class org.gradle.client.core.util.Logback*

# Gradle Tooling API
-keep class org.gradle.** { *; }
-dontwarn org.gradle.internal.impldep.**

# Decompose
-keep class com.arkivanov.decompose.extensions.compose.mainthread.**

# JDBC
-keep class **.sqlite.** { *; }

# KTor Client
-keepclassmembers class io.ktor.** { volatile <fields>; }
-keep class io.ktor.client.engine.** { *; }
