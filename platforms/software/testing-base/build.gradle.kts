plugins {
    id("gradlebuild.distribution.api-java")
}

gradlebuildJava.usedInWorkers()

description = """Basic testing related plugins, which establish conventions for testing output directories,
and setup basic testing-related features lik a testSuites container and the testing extension.  It provides most of the
testing-related abstract base types and interfaces for things like Test tasks, listeners and filters.

This project is a implementation dependency of many other testing-related subprojects in the Gradle build.
"""

errorprone {
    disabledChecks.addAll(
        "EmptyBlockTag", // 3 occurrences
        "InlineMeInliner", // 2 occurrences
        "MissingCasesInEnumSwitch", // 1 occurrences
        "OperatorPrecedence", // 1 occurrences
        "UnusedMethod", // 4 occurrences
    )
}

dependencies {
    api(projects.concurrent)
    api(projects.javaLanguageExtensions)
    api(project(":base-services"))
    api(project(":build-operations"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":enterprise-logging"))
    api(project(":logging"))
    api(project(":logging-api"))
    api(project(":messaging"))
    api(project(":native"))
    api(project(":reporting"))
    api(project(":worker-processes"))

    api(libs.groovy)
    api(libs.guava)
    api(libs.jsr305)
    api(libs.inject)

    implementation(project(":base-services-groovy"))
    implementation(project(":model-core"))
    implementation(project(":process-services"))

    implementation(libs.ant) {
        because("only used for DateUtils")
    }
    implementation(libs.commonsLang)
    implementation(libs.kryo)
    implementation(libs.slf4jApi)

    testImplementation(project(":file-collections"))
    testImplementation(project(":enterprise-operations"))
    testImplementation(libs.commonsIo)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":messaging")))
    testImplementation(testFixtures(project(":platform-base")))
    testImplementation(testFixtures(project(":logging")))
    testImplementation(testFixtures(project(":base-services")))

    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":model-core"))
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(project(":logging"))
    testFixturesImplementation(libs.guava)
    testFixturesImplementation(libs.jsoup)

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-core"))
}

strictCompile {
    ignoreRawTypes() // raw types used in public API (org.gradle.api.tasks.testing.AbstractTestTask)
}

packageCycles {
    excludePatterns.add("org/gradle/api/internal/tasks/testing/**")
}

integTest.usesJavadocCodeSnippets = true
