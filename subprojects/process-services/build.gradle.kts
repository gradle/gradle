plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Process execution abstractions."

gradlebuildJava.usedInWorkers()

dependencies {
    implementation(project(":base-services"))

    implementation(project(":messaging"))
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

