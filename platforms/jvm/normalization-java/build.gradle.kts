plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "API extraction for Java"

errorprone {
    disabledChecks.addAll(
        "EmptyBlockTag", // 2 occurrences
        "NonApiType", // 1 occurrences
        "ProtectedMembersInFinalClass", // 1 occurrences
    )
}

dependencies {
    api(projects.hashing)
    api(projects.files)
    api(projects.snapshots)

    api(libs.jsr305)
    api(libs.guava)
    api("org.gradle:java-api-extractor")

    implementation(projects.functional)

    implementation(libs.slf4jApi)
    implementation(libs.commonsIo)

    testImplementation(projects.baseServices)
    testImplementation(projects.internalTesting)
    testImplementation(testFixtures(projects.snapshots))
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
