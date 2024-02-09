plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Process execution abstractions."

gradlebuildJava.usedInWorkers()

errorprone {
    disabledChecks.addAll(
        "FutureReturnValueIgnored", // 1 occurrences
    )
}

dependencies {
    api(project(":base-annotations"))
    api(project(":base-services"))
    api(project(":messaging"))

    api(libs.jsr305)

    implementation(project(":native"))

    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.nativePlatform)

    testImplementation(testFixtures(project(":core")))

    integTestDistributionRuntimeOnly(project(":distributions-core"))
}

packageCycles {
    excludePatterns.add("org/gradle/process/internal/**")
}
