plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "An API for providing internal services for Gradle modules"

dependencies {
    api(projects.javaLanguageExtensions)
}
