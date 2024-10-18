plugins {
    id("gradlebuild.distribution.api-java")
}

description = "A set of general-purpose resource abstractions"

errorprone {
    disabledChecks.addAll(
        "UndefinedEquals", // 1 occurrences
    )
}

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.buildOperations)
    api(projects.hashing)
    api(projects.baseServices)
    api(projects.messaging)
    api(projects.native)

    api(libs.jsr305)

    implementation(projects.files)
    implementation(projects.logging)

    implementation(libs.guava)
    implementation(libs.commonsIo)

    testImplementation(projects.processServices)
    testImplementation(projects.coreApi)
    testImplementation(projects.fileCollections)
    testImplementation(projects.snapshots)

    testImplementation(testFixtures(projects.core))

    integTestDistributionRuntimeOnly(projects.distributionsCore)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
