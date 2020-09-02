/**
 * Process execution abstractions.
 */
plugins {
    id("gradlebuild.distribution.api-java")
}

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

classycle {
    excludePatterns.set(listOf("org/gradle/process/internal/**"))
}
