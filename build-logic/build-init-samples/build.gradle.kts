plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

description = "Provides a plugin to generate samples using internal build init APIs"

dependencies {
    implementation(projects.jvm)

    implementation("gradlebuild:basics")
    implementation(buildLibs.gradleGuidesPlugin)
}
