plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.instrumented-java-project")
}

description = "Publishing plugin for Ivy repositories"

errorprone {
    disabledChecks.addAll(
        "UnusedMethod", // 2 occurrences
        "UnusedVariable", // 1 occurrences
    )
}

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.serviceProvider)
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.dependencyManagement)
    api(projects.fileCollections)
    api(projects.logging)
    api(projects.modelCore)
    api(projects.publish)
    api(projects.resources)

    api(libs.jsr305)
    api(libs.inject)

    implementation(projects.functional)
    implementation(projects.loggingApi)
    implementation(projects.serviceLookup)

    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.ivy)

    testImplementation(projects.native)
    testImplementation(projects.processServices)
    testImplementation(projects.snapshots)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.modelCore))
    testImplementation(testFixtures(projects.platformBase))
    testImplementation(testFixtures(projects.dependencyManagement))

    integTestImplementation(libs.slf4jApi)

    integTestRuntimeOnly(projects.resourcesS3)
    integTestRuntimeOnly(projects.resourcesSftp)
    integTestRuntimeOnly(projects.apiMetadata)

    testFixturesApi(projects.baseServices) {
        because("Test fixtures export the Action class")
    }
    testFixturesApi(projects.coreApi) {
        because("Test fixtures export the RepositoryHandler class")
    }
    testFixturesImplementation(projects.logging)
    testFixturesImplementation(projects.dependencyManagement)
    testFixturesImplementation(projects.internalIntegTesting)
    testFixturesImplementation(libs.slf4jApi)
    testFixturesImplementation(libs.sshdCore)
    testFixturesImplementation(libs.sshdScp)
    testFixturesImplementation(libs.sshdSftp)

    testRuntimeOnly(projects.distributionsCore) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(projects.distributionsJvm) {
        because("SamplesIvyPublishIntegrationTest test applies the java-library plugin.")
    }
    crossVersionTestDistributionRuntimeOnly(projects.distributionsJvm) {
        because("IvyPublishCrossVersionIntegrationTest test applies the war plugin.")
    }
}

integTest.usesJavadocCodeSnippets = true
tasks.isolatedProjectsIntegTest {
    enabled = false
}
