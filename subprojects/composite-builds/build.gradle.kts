plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Included build controller and composite build infrastructure"

errorprone {
    disabledChecks.addAll(
        "FutureReturnValueIgnored", // 1 occurrences
        "SameNameButDifferent", // 11 occurrences
        "ThreadLocalUsage", // 1 occurrences
    )
}

dependencies {
    api(projects.concurrent)
    api(projects.stdlibJavaExtensions)
    api(projects.serialization)
    api(projects.serviceProvider)
    api(projects.buildOperations)
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.dependencyManagement)
    api(projects.messaging)
    api(projects.modelCore)
    api(projects.pluginUse)
    api(projects.buildState)

    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.buildLifecycleApi)
    implementation(projects.time)
    implementation(projects.enterpriseLogging)
    implementation(projects.enterpriseOperations)
    implementation(projects.daemonServices)
    implementation(projects.logging)
    implementation(projects.serviceLookup)

    implementation(libs.slf4jApi)
    implementation(libs.guava)

    testImplementation(projects.fileWatching)
    testImplementation(projects.buildOption)
    testImplementation(testFixtures(projects.buildOperations))
    testImplementation(testFixtures(projects.dependencyManagement))
    testImplementation(testFixtures(projects.core))

    integTestImplementation(projects.buildOption)
    integTestImplementation(projects.launcher)

    integTestDistributionRuntimeOnly(projects.distributionsJvm) {
        because("Requires test-kit: 'java-gradle-plugin' is used in some integration tests which always adds the test-kit dependency.  The 'java-platform' plugin from the JVM platform is used in some tests.")
    }
}

testFilesCleanup.reportOnly = true
tasks.isolatedProjectsIntegTest {
    enabled = false
}
