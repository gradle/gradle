if (GradleVersion.current() >= GradleVersion.version("8.0")) {
    apply(from = File(gradle.gradleUserHomeDir, "gradle8/cache-settings.init.gradle.kts"))
}
