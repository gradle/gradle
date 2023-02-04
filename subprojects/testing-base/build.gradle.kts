plugins {
    id("gradlebuild.distribution.api-java")
}

gradlebuildJava.usedInWorkers()

description = """Basic testing related plugins, which establish conventions for testing output directories,
and setup basic testing-related features lik a testSuites container and the testing extension.  It provides most of the
testing-related abstract base types and interfaces for things like Test tasks, listeners and filters.

This project is a implementation dependency of many other testing-related subprojects in the Gradle build.
"""

dependencies {
    implementation(project(":base-services"))
    implementation(project(":messaging"))
    implementation(project(":native"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":worker-processes"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":base-services-groovy"))
    implementation(project(":reporting"))
    implementation(project(":platform-base"))

    implementation(libs.slf4jApi)
    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.kryo)
    implementation(libs.inject)
    implementation(libs.ant) // only used for DateUtils

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
