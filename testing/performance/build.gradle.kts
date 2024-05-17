plugins {
    id("gradlebuild.internal.java")
    id("gradlebuild.performance-test")
    id("gradlebuild.performance-templates")
}

description = "Performance tests for the Gradle build tool"

dependencies {
    performanceTestImplementation(project(":base-services"))
    performanceTestImplementation(project(":core"))
    performanceTestImplementation(project(":internal-testing"))
    performanceTestImplementation(project(":java-language-extensions"))
    performanceTestImplementation(project(":tooling-api"))

    performanceTestImplementation(testFixtures(project(":tooling-api")))

    performanceTestImplementation(libs.commonsLang3)
    performanceTestImplementation(libs.commonsIo)
    performanceTestImplementation(libs.gradleProfiler)
    performanceTestImplementation(libs.jettyServer)
    performanceTestImplementation(libs.jettyWebApp)
    performanceTestImplementation(libs.junit)
    performanceTestImplementation(libs.servletApi)

    performanceTestRuntimeOnly(project(":core-api"))
    performanceTestRuntimeOnly(libs.jetty)

    performanceTestDistributionRuntimeOnly(project(":distributions-full")) {
        because("All Gradle features have to be available.")
    }
    performanceTestLocalRepository(project(":tooling-api")) {
        because("IDE tests use the Tooling API.")
    }
}
