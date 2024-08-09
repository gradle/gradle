plugins {
    `kotlin-dsl`
}

group = "gradlebuild"

description = "Provides a plugin for publishing some of Gradle's subprojects to Artifactory or the Plugin Portal"

dependencies {
    implementation(projects.basics)
    implementation(projects.moduleIdentity)

    implementation("com.gradle.publish:plugin-publish-plugin")
}
