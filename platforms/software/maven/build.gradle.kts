plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.instrumented-java-project")
}

description = "Implementation of the Maven Publish Plugin that provides the ability to publish build artifacts to Maven repositories."

errorprone {
    disabledChecks.addAll(
        "DefaultCharset", // 1 occurrences
        "EqualsUnsafeCast", // 1 occurrences
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

    api(libs.guava)
    api(libs.inject)
    api(libs.jsr305)
    api(libs.maven3Model) {
        because("We use the metadata model classes to create POM metadata files for components")
    }
    api(libs.maven3RepositoryMetadata) {
        because("We use the metadata model classes to create repository metadata files")
    }

    implementation(projects.functional)
    implementation(projects.hashing)
    implementation(projects.loggingApi)
    implementation(projects.serviceLookup)

    implementation(libs.commonsLang)
    implementation(libs.plexusUtils)
    implementation(libs.slf4jApi)

    testImplementation(projects.native)
    testImplementation(projects.processServices)
    testImplementation(projects.snapshots)
    testImplementation(projects.resourcesHttp)

    testImplementation(libs.xmlunit)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.modelCore))
    testImplementation(testFixtures(projects.dependencyManagement))

    integTestImplementation(projects.enterpriseOperations)

    testFixturesApi(projects.baseServices) {
        because("Test fixtures export the Action class")
    }
    testFixturesImplementation(projects.logging)
    testFixturesImplementation(projects.coreApi)
    testFixturesImplementation(projects.internalIntegTesting)
    testFixturesImplementation(projects.dependencyManagement)

    testRuntimeOnly(projects.distributionsCore) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(projects.distributionsJvm)
    crossVersionTestDistributionRuntimeOnly(projects.distributionsJvm)
}

strictCompile {
    ignoreDeprecations() // old 'maven' publishing mechanism: types are deprecated
    ignoreRawTypes() // old 'maven' publishing mechanism: raw types used in public API
}

packageCycles {
    excludePatterns.add("org/gradle/api/publication/maven/internal/**")
    excludePatterns.add("org/gradle/api/artifacts/maven/**")
}

integTest.usesJavadocCodeSnippets = true
tasks.isolatedProjectsIntegTest {
    enabled = false
}
