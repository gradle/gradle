plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Plugin and integration with JaCoCo code coverage"

errorprone {
    disabledChecks.addAll(
        "ReferenceEquality", // 3 occurrences
        "UnusedMethod", // 1 occurrences
    )
}

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.fileOperations)
    api(projects.platformJvm)
    api(projects.reporting)
    api(projects.workers)

    api(libs.groovy)
    api(libs.inject)
    api(libs.jspecify)

    implementation(projects.daemonServerWorker)
    implementation(projects.loggingApi)
    implementation(projects.modelCore)
    implementation(projects.platformBase)
    implementation(projects.pluginsJava)
    implementation(projects.pluginsJavaBase)
    implementation(projects.pluginsJvmTestSuite)
    implementation(projects.serviceLookup)
    implementation(projects.testSuitesBase)
    implementation(projects.testingJvm)

    implementation(libs.commonsLang)
    implementation(libs.guava)

    testFixturesImplementation(projects.baseServices)
    testFixturesImplementation(projects.coreApi)
    testFixturesImplementation(projects.core)
    testFixturesImplementation(projects.internalIntegTesting)

    testFixturesImplementation(libs.jsoup)
    testFixturesImplementation(libs.groovyXml)

    testImplementation(projects.internalTesting)
    testImplementation(projects.resources)
    testImplementation(projects.internalIntegTesting)
    testImplementation(projects.languageJava)
    testImplementation(testFixtures(projects.core))

    testRuntimeOnly(projects.distributionsCore) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(projects.distributionsJvm)
}

strictCompile {
    ignoreRawTypes()
}

packageCycles {
    excludePatterns.add("org/gradle/internal/jacoco/*")
    excludePatterns.add("org/gradle/testing/jacoco/plugins/*")
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
