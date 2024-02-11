# Keep class names
-dontobfuscate

# Entry point
-keep class org.gradle.wrapper.GradleWrapperMain { public static void main(java.lang.String[]); }
