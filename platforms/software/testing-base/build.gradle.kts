plugins {
    id("gradlebuild.distribution.api-java")
}

description = """Basic testing related plugins, which establish conventions for testing output directories,
and setup basic testing-related features like a testSuites container and the testing extension.  It provides most of the
testing-related abstract base types and interfaces for things like Test tasks, listeners and filters.

This project is a implementation dependency of many other testing-related subprojects in the Gradle build.
"""

errorprone {
    disabledChecks.addAll(
        "EmptyBlockTag", // 3 occurrences
        "InlineMeInliner", // 2 occurrences
        "MissingCasesInEnumSwitch", // 1 occurrences
        "OperatorPrecedence", // 1 occurrences
    )
}

dependencies {
    api(projects.baseServices)
    api(projects.buildOperations)
    api(projects.core)
    api(projects.coreApi)
    api(projects.enterpriseLogging)
    api(projects.javaLanguageExtensions)
    api(projects.logging)
    api(projects.loggingApi)
    api(projects.messaging)
    api(projects.native)
    api(projects.reporting)
    api(projects.serviceProvider)
    api(projects.testingBaseInfrastructure)
    api(projects.serviceProvider)
    api(projects.time)

    api(libs.groovy)
    api(libs.guava)
    api(libs.jsr305)
    api(libs.inject)

    implementation(projects.baseServicesGroovy)
    implementation(projects.concurrent)
    implementation(projects.files)
    implementation(projects.modelCore)
    implementation(projects.processServices)
    implementation(projects.serialization)

    implementation(libs.ant) {
        because("only used for DateUtils")
    }
    implementation(libs.commonsLang)
    implementation(libs.kryo)
    implementation(libs.slf4jApi)

    testImplementation(projects.fileCollections)
    testImplementation(projects.enterpriseOperations)
    testImplementation(testFixtures(projects.baseServices))
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.logging))
    testImplementation(testFixtures(projects.messaging))
    testImplementation(testFixtures(projects.platformBase))
    testImplementation(testFixtures(projects.serialization))

    testImplementation(libs.commonsIo)

    testFixturesImplementation(projects.baseServices)
    testFixturesImplementation(projects.internalIntegTesting)
    testFixturesImplementation(projects.logging)
    testFixturesImplementation(projects.modelCore)

    testFixturesImplementation(libs.guava)
    testFixturesImplementation(libs.jsoup)

    testRuntimeOnly(projects.distributionsCore) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(projects.distributionsCore)
}

strictCompile {
    ignoreRawTypes() // raw types used in public API (org.gradle.api.tasks.testing.AbstractTestTask)
}

packageCycles {
    excludePatterns.add("org/gradle/api/internal/tasks/testing/**")
}

integTest.usesJavadocCodeSnippets = true
