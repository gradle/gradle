plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Provides high-level insights into a Gradle build (--profile)"

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.serviceProvider)
    api(projects.time)
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.enterpriseLogging)

    implementation(projects.logging)
    implementation(projects.loggingApi)
    implementation(projects.reportRendering)
    implementation(projects.serviceLookup)

    implementation(libs.guava)
    implementation(libs.jspecify)

    testImplementation(projects.internalTesting)

    integTestImplementation(libs.jsoup)

    integTestDistributionRuntimeOnly(projects.distributionsFull)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
