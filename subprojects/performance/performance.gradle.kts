plugins {
    gradlebuild.internal.java
    gradlebuild.`performance-templates`
}

dependencies {
    performanceTestImplementation(project(":baseServices"))
    performanceTestImplementation(project(":core"))
    performanceTestImplementation(project(":modelCore"))
    performanceTestImplementation(project(":coreApi"))
    performanceTestImplementation(project(":buildOption"))
    performanceTestImplementation(library("slf4j_api"))
    performanceTestImplementation(library("commons_io"))
    performanceTestImplementation(library("commons_compress"))
    performanceTestImplementation(testLibrary("jetty"))
    performanceTestImplementation(testFixtures(project(":toolingApi")))

    performanceTestDistributionRuntimeOnly(project(":distributionsFull")) {
        because("All Gradle features have to be available.")
    }
    performanceTestLocalRepository(project(":toolingApi")) {
        because("IDE tests use the Tooling API.")
    }
}
