plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implements logic for generating jacoco reports"

dependencies {
    api(projects.antWorker)
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.internalInstrumentationApi)
    api(libs.jspecify)

    implementation(projects.daemonServerWorker)
    implementation(projects.stdlibJavaExtensions)
    implementation(libs.guava)
    implementation(libs.groovy)
}
