plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

description = "Provides a plugin for publishing some of Gradle's subprojects to Artifactory or the Plugin Portal"

dependencies {
    implementation(project(":module-identity"))
    implementation(project(":integration-testing"))

    implementation("com.gradle.publish:plugin-publish-plugin")
}
