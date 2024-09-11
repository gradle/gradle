plugins {
    id("gradlebuild.internal.java")
    id("gradlebuild.performance-test")
    id("gradlebuild.performance-templates")
}

description = "Performance tests for the Gradle build tool"

dependencies {
    performanceTestImplementation(projects.baseServices)
    performanceTestImplementation(projects.core)
    performanceTestImplementation(projects.internalTesting)
    performanceTestImplementation(projects.stdlibJavaExtensions)
    performanceTestImplementation(projects.toolingApi)

    performanceTestImplementation(testFixtures(projects.toolingApi))

    performanceTestImplementation(libs.commonsLang3)
    performanceTestImplementation(libs.commonsIo)
    performanceTestImplementation(libs.gradleProfiler)
    performanceTestImplementation(libs.jettyServer)
    performanceTestImplementation(libs.jettyWebApp)
    performanceTestImplementation(libs.junit)
    performanceTestImplementation(libs.servletApi)

    performanceTestRuntimeOnly(projects.coreApi)
    performanceTestRuntimeOnly(libs.jetty)

    performanceTestDistributionRuntimeOnly(projects.distributionsFull) {
        because("All Gradle features have to be available.")
    }
    performanceTestLocalRepository(projects.toolingApi) {
        because("IDE tests use the Tooling API.")
    }
}
