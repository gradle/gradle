plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Internal API for Gradle authentication schemes"

dependencies {
    api(projects.baseServices)
    api(projects.credentialsApi)
    api(projects.stdlibJavaExtensions)
}
