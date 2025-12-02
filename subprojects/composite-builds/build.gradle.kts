plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Included build controller and composite build infrastructure"

dependencies {
    api(projects.baseServices)
    api(projects.buildOperations)
    api(projects.buildState)
    api(projects.concurrent)
    api(projects.core)
    api(projects.coreApi)
    api(projects.dependencyManagement)
    api(projects.messaging)
    api(projects.modelCore)
    api(projects.pluginUse)
    api(projects.serviceProvider)
    api(projects.stdlibJavaExtensions)

    api(libs.inject)
    api(libs.jspecify)

    implementation(projects.buildDiscoveryImpl)
    implementation(projects.classloaders)
    implementation(projects.time)
    implementation(projects.enterpriseLogging)
    implementation(projects.enterpriseOperations)
    implementation(projects.daemonServices)
    implementation(projects.problemsApi)
    implementation(projects.serviceLookup)
    implementation(projects.functional)

    implementation(libs.slf4jApi)
    implementation(libs.guava)

    testImplementation(projects.fileWatching)
    testImplementation(projects.buildOption)
    testImplementation(testFixtures(projects.buildOperations))
    testImplementation(testFixtures(projects.dependencyManagement))
    testImplementation(testFixtures(projects.core))

    integTestImplementation(projects.buildOption)
    integTestImplementation(projects.launcher)

    integTestDistributionRuntimeOnly(projects.distributionsFull) {
        because("""
          1. Requires test-kit: 'java-gradle-plugin' is used in some integration tests which always adds the test-kit dependency. The 'java-platform' plugin from the JVM platform is used in some tests.
          2. Has tests with the enterprise plugin
        """)
    }
}

testFilesCleanup.reportOnly = true
tasks.isolatedProjectsIntegTest {
    enabled = false
}
