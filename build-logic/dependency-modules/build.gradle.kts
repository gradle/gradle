plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

description = "Provides a plugin to minify and correct metadata for dependencies used by Gradle"

dependencies {
    implementation(project(":basics"))
    implementation("com.google.code.gson:gson")
}
