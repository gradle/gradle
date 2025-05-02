plugins {
    `kotlin-dsl`
}

description = "Provides a plugin to define the version and name for subproject publications"

group = "gradlebuild"

dependencies {
    api(platform(projects.buildPlatform))

    implementation(projects.basics)

    implementation("com.google.code.gson:gson")
}
