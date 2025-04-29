plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Utility code shared between the wrapper and the Gradle distribution"

gradleModule {
    usedInWrapper = true
    usesIncompatibleDependencies = true // For test dependencies
}

dependencies {
    testImplementation(projects.baseServices)
    testImplementation(projects.coreApi)
    testImplementation(projects.native)
    testImplementation(libs.commonsCompress)

    integTestImplementation(projects.dependencyManagement)
    integTestImplementation(projects.logging)

    integTestDistributionRuntimeOnly(projects.distributionsFull)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
