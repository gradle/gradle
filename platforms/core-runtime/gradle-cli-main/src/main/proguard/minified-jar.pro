# Keep class names
-dontobfuscate

# Ignore missing symbols
-ignorewarnings

# Entry point
-keep class org.gradle.launcher.GradleMain { public static void main(java.lang.String[]); }
