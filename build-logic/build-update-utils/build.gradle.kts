plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
    id("gradlebuild.build-logic.groovy-dsl-gradle-plugin")
}

description = "Provides plugins that create update tasks for the Gradle build"

dependencies {
    implementation("com.google.code.gson:gson")
    implementation("org.jsoup:jsoup")
    implementation(project(":basics"))
}
