plugins {
    id("gradlebuild.internal.java")
    id("gradlebuild.performance-testing")
    id("gradlebuild.performance-templates")
}

description = "Performance tests for the Gradle build tool"

dependencies {
    performanceTestImplementation(projects.baseServices)
    performanceTestImplementation(projects.internalIntegTesting)
    performanceTestImplementation(projects.startParameter)
    performanceTestImplementation(projects.stdlibJavaExtensions)

    performanceTestImplementation(libs.commonsIo)
    performanceTestImplementation(libs.commonsLang)
    performanceTestImplementation(libs.groovy)
    performanceTestImplementation(testLibs.gradleProfiler)
    performanceTestImplementation(testLibs.junit)
    performanceTestImplementation(testLibs.spock)

    performanceTestDistributionRuntimeOnly(projects.distributionsFull) {
        because("All Gradle features have to be available.")
    }
    performanceTestLocalRepository(projects.toolingApi) {
        because("IDE tests use the Tooling API.")
    }

    performanceTestRuntimeOnly(testFixtures(projects.toolingApi))
}

errorprone {
    nullawayEnabled = true
}
