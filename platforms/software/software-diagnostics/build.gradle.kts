plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.instrumented-java-project")
}

description = """Reports related to the dependency management functionality used
in the Gradle builds of software projects.  Any reports or reporting tasks related to or dependent upon
dependency management types should be included here."""

errorprone {
    disabledChecks.addAll(
        "InlineMeInliner", // 1 occurrences
        "MixedMutabilityReturnType", // 1 occurrences
        "NonApiType" // 1 occurrences
    )
}

dependencies {
    api(projects.baseDiagnostics)
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.dependencyManagement)
    api(projects.enterpriseLogging)
    api(projects.fileCollections)
    api(projects.internalInstrumentationApi)
    api(projects.jvmServices)
    api(projects.logging)
    api(projects.modelCore)
    api(projects.platformBase)
    api(projects.reporting)
    api(projects.reportRendering)
    api(projects.serviceProvider)
    api(projects.stdlibJavaExtensions)

    api(libs.groovy)
    api(libs.jspecify)
    api(libs.inject)

    implementation(projects.functional)
    implementation(projects.loggingApi)

    implementation(libs.commonsLang)
    implementation(libs.groovyJson)
    implementation(libs.guava)
    implementation(libs.jatl)

    testFixturesApi(testFixtures(projects.platformNative))
    testFixturesApi(testFixtures(projects.logging))
    testFixturesImplementation(projects.baseServices)
    testFixturesImplementation(projects.core)
    testFixturesImplementation(projects.internalIntegTesting)
    testFixturesImplementation(libs.guava)

    testImplementation(projects.processServices)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.dependencyManagement))
    testImplementation(testFixtures(projects.logging))

    testRuntimeOnly(projects.distributionsFull) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }

    integTestImplementation(testFixtures(projects.baseDiagnostics))
    integTestImplementation(testFixtures(projects.platformNative))

    integTestImplementation(libs.jsoup)
    integTestImplementation(libs.jetty)

    integTestDistributionRuntimeOnly(projects.distributionsFull)
}

packageCycles {
    excludePatterns.add("org/gradle/api/reporting/dependencies/internal/*")
    excludePatterns.add("org/gradle/api/plugins/internal/*")
}

tasks.isolatedProjectsIntegTest {
    enabled = false
}
