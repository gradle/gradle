plugins {
    id("gradlebuild.distribution.api-java")
}

description = "A set of general-purpose resource abstractions"

errorprone {
    disabledChecks.addAll(
        "OperatorPrecedence", // 9 occurrences
        "UndefinedEquals", // 1 occurrences
    )
}

dependencies {
    api(projects.javaLanguageExtensions)
    api(project(":build-operations"))
    api(project(":hashing"))
    api(project(":base-services"))
    api(project(":messaging"))
    api(project(":native"))

    api(libs.jsr305)

    implementation(project(":files"))
    implementation(project(":logging"))

    implementation(libs.guava)
    implementation(libs.commonsIo)

    testImplementation(project(":process-services"))
    testImplementation(project(":core-api"))
    testImplementation(project(":file-collections"))
    testImplementation(project(":snapshots"))

    testImplementation(testFixtures(project(":core")))

    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
