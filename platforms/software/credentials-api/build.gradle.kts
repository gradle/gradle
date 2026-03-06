plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Public API interfaces for Gradle credentials"

dependencies {
    api(projects.baseServices)
    api(libs.jspecify)

    compileOnly(projects.internalInstrumentationApi) {
        because("Provides @ToBeReplacedByLazyProperty annotation, following the same pattern as core-api")
    }
}
