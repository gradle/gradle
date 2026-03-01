plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Internal API for Gradle authentication schemes"

dependencies {
    api(projects.baseServices)    // @NonExtensible (org.gradle.api.NonExtensible)
    api(projects.credentialsApi)  // Credentials interface
    api(projects.resources)       // Authentication interface (org.gradle.authentication.Authentication)
}
