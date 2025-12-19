# Keep class names
-dontobfuscate


-dontwarn org.jetbrains.annotations.**

# Entry point
-keep class org.gradle.wrapper.GradleWrapperMain { public static void main(java.lang.String[]); }
