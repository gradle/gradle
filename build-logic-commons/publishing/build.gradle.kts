plugins {
    `kotlin-dsl`
}

group = "gradlebuild"

description = "Provides a plugin for publishing some of Gradle's subprojects to Artifactory or the Plugin Portal"

dependencies {
    api(platform(projects.buildPlatform))

    implementation(projects.basics)
    implementation(projects.moduleIdentity)

    implementation(buildLibs.publishPlugin)
}
