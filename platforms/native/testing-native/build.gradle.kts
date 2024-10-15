plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Plugins, tasks and domain objects for testing native code"

errorprone {
    disabledChecks.addAll(
        "MixedMutabilityReturnType", // 1 occurrences
    )
}

dependencies {
    api(projects.serviceLookup)
    api(projects.serviceProvider)
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.diagnostics)
    api(projects.stdlibJavaExtensions)
    api(projects.languageNative)
    api(projects.modelCore)
    api(projects.native)
    api(projects.platformBase)
    api(projects.platformNative)
    api(projects.processServices)
    api(projects.testSuitesBase)
    api(projects.testingBase)
    api(projects.testingBaseInfrastructure)
    api(projects.time)

    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.io)
    implementation(projects.logging)
    implementation(projects.loggingApi)

    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.guava)

    testImplementation(projects.fileCollections)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.platformNative))
    testImplementation(testFixtures(projects.diagnostics))
    testImplementation(testFixtures(projects.platformBase))
    testImplementation(testFixtures(projects.testingBase))
    testImplementation(testFixtures(projects.languageNative))
    testImplementation(testFixtures(projects.ide))

    testRuntimeOnly(projects.distributionsCore) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(projects.distributionsNative)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
