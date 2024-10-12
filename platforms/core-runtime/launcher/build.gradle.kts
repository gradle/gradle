plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.launchable-jar")
}

description = "Implementation for launching, controlling and communicating with Gradle Daemon from CLI and TAPI"

dependencies {
    api(projects.baseServices)
    api(projects.buildEvents)
    api(projects.buildOperations)
    api(projects.buildOption)
    api(projects.buildState)
    api(projects.cli)
    api(projects.concurrent)
    api(projects.core)
    api(projects.coreApi)
    api(projects.daemonProtocol)
    api(projects.enterpriseLogging)
    api(projects.execution)
    api(projects.fileCollections)
    api(projects.fileWatching)
    api(projects.files)
    api(projects.hashing)
    api(projects.instrumentationAgentServices)
    api(projects.stdlibJavaExtensions)
    api(projects.jvmServices)
    api(projects.logging)
    api(projects.loggingApi)
    api(projects.messaging)
    api(projects.modelCore)
    api(projects.native)
    api(projects.processMemoryServices)
    api(projects.serialization)
    api(projects.serviceLookup)
    api(projects.serviceProvider)
    api(projects.snapshots)
    api(projects.time)
    api(projects.toolingApi)

    // This project contains the Gradle client, daemon and tooling API provider implementations.
    // It should be split up, but for now, add dependencies on both the client and daemon pieces
    api(projects.clientServices)
    api(projects.daemonServices)

    api(libs.guava)
    api(libs.jsr305)

    implementation(projects.enterpriseOperations)
    implementation(projects.functional)
    implementation(projects.io)
    implementation(projects.problemsApi)
    implementation(projects.serviceRegistryBuilder)

    implementation(libs.slf4jApi)

    runtimeOnly(projects.gradleCliMain)
    runtimeOnly(projects.declarativeDslProvider)
    runtimeOnly(projects.problems)

    runtimeOnly(libs.commonsIo)
    runtimeOnly(libs.commonsLang)
    runtimeOnly(libs.slf4jApi)

    // The wrapper expects the launcher Jar to have classpath entries that contain the main class and its runtime classpath
    manifestClasspath(projects.gradleCliMain)

    testImplementation(projects.internalIntegTesting)
    testImplementation(testFixtures(projects.serialization))
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.logging))
    testImplementation(testFixtures(projects.toolingApi))
    testImplementation(testFixtures(projects.daemonProtocol))

    integTestImplementation(projects.persistentCache)
    integTestImplementation(libs.slf4jApi)
    integTestImplementation(libs.guava)
    integTestImplementation(libs.commonsLang)
    integTestImplementation(libs.commonsIo)
    integTestImplementation(testFixtures(projects.buildConfiguration))

    integTestDistributionRuntimeOnly(projects.distributionsFull) {
        because("built-in options are required to be present at runtime for 'TaskOptionsSpec'")
    }
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
}

testFilesCleanup.reportOnly = true
tasks.isolatedProjectsIntegTest {
    enabled = false
}
