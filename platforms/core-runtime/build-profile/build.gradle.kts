plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Provides high-level insights into a Gradle build (--profile)"

dependencies {
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.dependencyManagementBase)
    api(projects.enterpriseLogging)
    api(projects.serviceProvider)
    api(projects.stdlibJavaExtensions)
    api(projects.time)

    implementation(projects.logging)
    implementation(projects.loggingApi)
    implementation(projects.reportRendering)
    implementation(projects.serviceLookup)

    implementation(libs.guava)

    testImplementation(projects.internalTesting)

    integTestImplementation(libs.jsoup)

    integTestDistributionRuntimeOnly(projects.distributionsFull)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
