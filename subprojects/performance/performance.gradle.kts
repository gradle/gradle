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
    performanceTestImplementation(project(":internalIntegTesting"))
    performanceTestImplementation(library("slf4j_api"))
    performanceTestImplementation(library("commons_io"))
    performanceTestImplementation(library("commons_compress"))
    performanceTestImplementation(testLibrary("jetty"))
    performanceTestImplementation(testFixtures(project(":toolingApi")))

    performanceTestRuntimeOnly(project(":distributionsFull")) {
        because("so that all Gradle features are available")
    }
}
