plugins {
    id("gradlebuild.internal.java")
    id("gradlebuild.performance-test")
    id("gradlebuild.performance-templates")
}

description = "Performance tests for the Gradle build tool"

dependencies {
    performanceTestImplementation(project(":base-services"))
    performanceTestImplementation(project(":enterprise-operations"))
    performanceTestImplementation(project(":core"))
    performanceTestImplementation(project(":model-core"))
    performanceTestImplementation(project(":core-api"))
    performanceTestImplementation(project(":build-option"))
    performanceTestImplementation(libs.slf4jApi)
    performanceTestImplementation(libs.commonsIo)
    performanceTestImplementation(libs.commonsCompress)
    performanceTestImplementation(libs.jetty)
    performanceTestImplementation(testFixtures(project(":tooling-api")))

    performanceTestDistributionRuntimeOnly(project(":distributions-full")) {
        because("All Gradle features have to be available.")
    }
    performanceTestLocalRepository(project(":tooling-api")) {
        because("IDE tests use the Tooling API.")
    }
}
