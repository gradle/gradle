plugins {
    `kotlin-dsl`
    alias(libs.plugins.versions) // Access version catalog in builSrc build file for plugin
}

repositories {
    gradlePluginPortal()
}

dependencies {
    // Access version catalog in buildSrc build file for dependencies
    implementation(plugin(libs.plugins.jacocolog)) // Plugin dependency
    implementation(libs.groovy.core) // Regular library from version catalog
    implementation("org.apache.commons:commons-lang3:3.9") // Direct dependency
}

// Helper function that transforms a Gradle Plugin alias from a
// Version Catalog into a valid dependency notation for buildSrc
fun DependencyHandlerScope.plugin(plugin: Provider<PluginDependency>) =
    plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }
