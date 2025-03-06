plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.instrumented-java-project")
}

description = """Base diagnostic reporting infrastructure and reports that are applicable to all Gradle builds.
These reports and types do not require dependency management to be present."""

dependencies {
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.enterpriseLogging)
    api(projects.fileCollections)

    // TODO: Remove this dependency when possible, it shouldn't actually be needed by this project
    api(projects.jvmServices) {
        because("This is a transitive dependency of the core project, and should be removed when possible.")
    }

    api(projects.logging)
    api(projects.modelCore)
    api(projects.reportRendering)
    api(projects.stdlibJavaExtensions)

    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.concurrent)
    implementation(projects.functional)
    implementation(projects.loggingApi)

    implementation(libs.commonsLang)
    implementation(libs.guava)

    testFixturesApi(testFixtures(projects.platformNative))
    testFixturesApi(testFixtures(projects.logging))

    testFixturesImplementation(projects.baseServices)
    testFixturesImplementation(projects.core)
    testFixturesImplementation(projects.internalIntegTesting)

    testFixturesImplementation(libs.guava)

    testImplementation(projects.processServices)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.logging))

    testRuntimeOnly(projects.distributionsCore) {
        because("ReportGeneratorTest tests load services like ClassLoaderRegistry.")
    }

    integTestImplementation(testFixtures(projects.declarativeDslProvider))

    integTestDistributionRuntimeOnly(projects.distributionsCore)
}

packageCycles {
    excludePatterns.add("org/gradle/api/reporting/model/internal/*")
}

tasks.isolatedProjectsIntegTest {
    enabled = false
}
