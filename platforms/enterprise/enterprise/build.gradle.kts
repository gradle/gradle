plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Services and utilities needed by Develocity plugin"

errorprone {
    disabledChecks.addAll(
        "SameNameButDifferent", // 4 occurrences
    )
}

dependencies {
    api(projects.buildOperations)
    api(projects.baseServices)
    api(projects.configurationCache)
    api(projects.core)
    api(projects.coreApi)
    api(projects.daemonServices)
    api(projects.enterpriseLogging)
    api(projects.fileCollections)
    api(projects.stdlibJavaExtensions)
    api(projects.jvmServices)
    api(projects.launcher)
    api(projects.modelCore)
    api(projects.serviceLookup)
    api(projects.serviceProvider)
    api(projects.snapshots)
    api(projects.testingJvm)
    api(projects.time)
    api(projects.problemsApi)

    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.concurrent)
    implementation(projects.dependencyManagement)
    implementation(projects.files)
    implementation(projects.hashing)
    implementation(projects.logging)
    implementation(projects.processServices)
    implementation(projects.serialization)
    implementation(projects.testingBase)

    implementation(libs.guava)

    compileOnly(libs.groovy) {
        because("some used APIs (e.g. FileTree.visit) provide methods taking Groovy closures which causes compile errors")
    }

    testImplementation(projects.resources)

    integTestImplementation(projects.internalTesting)
    integTestImplementation(projects.internalIntegTesting)
    integTestImplementation(testFixtures(projects.core))

    // Dependencies of the integ test fixtures
    integTestImplementation(projects.buildOption)
    integTestImplementation(projects.messaging)
    integTestImplementation(projects.persistentCache)
    integTestImplementation(projects.native)
    integTestImplementation(testFixtures(projects.problemsApi))
    integTestImplementation(libs.guava)

    integTestDistributionRuntimeOnly(projects.distributionsFull)
}
