plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implements logic for generating jacoco reports"

dependencies {
    api(projects.ant)
    api(projects.antWorker)
    api(projects.baseServices)
    api(projects.coreApi)

    api(projects.internalInstrumentationApi)
    api(libs.jspecify)

    implementation(projects.daemonServerWorker)
    implementation(projects.stdlibJavaExtensions)
    implementation(libs.guava)
    implementation(libs.groovy)
}
