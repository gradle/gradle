plugins {
    id("gradlebuild.internal.java")
    id("gradlebuild.performance-test")
    id("gradlebuild.performance-templates")
}

dependencies {
    performanceTestImplementation("org.gradle:base-services")
    performanceTestImplementation("org.gradle:core")
    performanceTestImplementation("org.gradle:model-core")
    performanceTestImplementation("org.gradle:core-api")
    performanceTestImplementation("org.gradle:build-option")
    performanceTestImplementation("org.gradle:internal-android-performance-testing")
    performanceTestImplementation(libs.slf4jApi)
    performanceTestImplementation(libs.commonsIo)
    performanceTestImplementation(libs.commonsCompress)
    performanceTestImplementation(libs.jetty)
    performanceTestImplementation(testFixtures("org.gradle:tooling-api"))

    performanceTestDistributionRuntimeOnly("org.gradle:distributions-full") {
        because("All Gradle features have to be available.")
    }
    performanceTestLocalRepository("org.gradle:tooling-api") {
        because("IDE tests use the Tooling API.")
    }
}
