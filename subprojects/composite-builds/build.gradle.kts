plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Included build controller and composite build infrastructure"

errorprone {
    disabledChecks.addAll(
        "FutureReturnValueIgnored", // 1 occurrences
        "SameNameButDifferent", // 11 occurrences
        "ThreadLocalUsage", // 1 occurrences
        "UnusedMethod", // 4 occurrences
    )
}

dependencies {
    api(project(":base-annotations"))
    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":dependency-management"))
    api(project(":messaging"))
    api(project(":model-core"))
    api(project(":plugin-use"))

    api(libs.inject)
    api(libs.jsr305)

    implementation(project(":build-operations"))
    implementation(project(":enterprise-logging"))
    implementation(project(":enterprise-operations"))
    implementation(project(":logging"))

    implementation(libs.slf4jApi)
    implementation(libs.guava)

    testImplementation(project(":file-watching"))
    testImplementation(project(":build-option"))
    testImplementation(testFixtures(project(":dependency-management")))
    testImplementation(testFixtures(project(":core")))

    integTestImplementation(project(":build-option"))
    integTestImplementation(project(":launcher"))

    integTestDistributionRuntimeOnly(project(":distributions-jvm")) {
        because("Requires test-kit: 'java-gradle-plugin' is used in some integration tests which always adds the test-kit dependency.  The 'java-platform' plugin from the JVM platform is used in some tests.")
    }
}

testFilesCleanup.reportOnly = true
