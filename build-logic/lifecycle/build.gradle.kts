plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

description = "Provides a plugin to define entry point lifecycle tasks used for development (e.g., sanityCheck)"

dependencies {
    implementation(project(":basics"))

    implementation("com.google.code.gson:gson")
}
