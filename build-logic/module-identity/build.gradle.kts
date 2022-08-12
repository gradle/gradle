plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

description = "Provides a plugin to define the version and name for subproject publications"

dependencies {
    implementation(project(":basics"))

    implementation("com.google.code.gson:gson")
}
