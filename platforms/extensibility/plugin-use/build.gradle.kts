plugins {
    id("gradlebuild.distribution.api-java")
}

errorprone {
    disabledChecks.addAll(
        "UnusedMethod", // 7 occurrences
    )
}

dependencies {
    api(projects.serviceProvider)
    api(projects.baseServices)
    api(projects.coreApi)
    api(projects.core)
    api(projects.dependencyManagement)
    api(projects.fileCollections)
    api(projects.stdlibJavaExtensions)
    api(projects.logging)
    api(projects.messaging)
    api(projects.modelCore)

    api(libs.guava)
    api(libs.jsr305)

    implementation(projects.functional)

    implementation(projects.jvmServices)
    implementation(projects.problemsApi)

    testImplementation(testFixtures(projects.resourcesHttp))

    integTestImplementation(projects.baseServicesGroovy)
    integTestImplementation(libs.jetbrainsAnnotations)
    integTestImplementation(libs.groovyTest)

    integTestDistributionRuntimeOnly(projects.distributionsBasics) {
        because("Requires test-kit: 'java-gradle-plugin' is used in integration tests which always adds the test-kit dependency.")
    }
}

testFilesCleanup.reportOnly = true

description = """Provides functionality for resolving and managing plugins during their application to projects."""

// Remove as part of fixing https://github.com/gradle/configuration-cache/issues/585
tasks.configCacheIntegTest {
    systemProperties["org.gradle.configuration-cache.internal.test-disable-load-after-store"] = "true"
}
