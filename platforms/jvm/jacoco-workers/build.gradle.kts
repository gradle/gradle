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
    api("com.google.guava:guava")
    api("org.apache.groovy:groovy")
    api("org.jspecify:jspecify")

    implementation(projects.daemonServerWorker)
    implementation(projects.stdlibJavaExtensions)
}
