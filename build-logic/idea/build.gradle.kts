plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

description = "Provides a plugin that configures IntelliJ's idea-ext plugin"

dependencies {
    implementation("gradlebuild:basics")
    implementation("gradle.plugin.org.jetbrains.gradle.plugin.idea-ext:gradle-idea-ext")
}
