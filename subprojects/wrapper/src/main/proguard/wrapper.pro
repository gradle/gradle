# Ignore missing annotations
-ignorewarnings

# Keep class names
-dontobfuscate

# Keep directories in JAR
# TODO Remove this and fix WrapperGenerationIntegrationTest."wrapper JAR does not contain version in manifest"
-keepdirectories **/*

# Entry point
-keep class org.gradle.wrapper.GradleWrapperMain { public static void main(java.lang.String[]); }
