plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Public API interfaces for Gradle credentials"

dependencies {
    api(projects.baseServices)  // @NonExtensible (org.gradle.api.NonExtensible)
    api(libs.jspecify)

    // @ToBeReplacedByLazyProperty — follow the same pattern as core-api for this annotation
    compileOnly(projects.internalInstrumentationApi)
}
