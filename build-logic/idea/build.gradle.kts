plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

dependencies {
    implementation(project(":basics"))
    implementation("gradle.plugin.org.jetbrains.gradle.plugin.idea-ext:gradle-idea-ext")
}
