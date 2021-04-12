plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.jmh")
}

description = "A set of generic services and utilities."

gradlebuildJava.usedInWorkers()

moduleIdentity.createBuildReceipt()

dependencies {
    api(project(":base-annotations"))
    api(project(":hashing"))
    api(project(":build-operations"))

    implementation(libs.asm)
    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.guava)
    implementation(libs.inject)
    implementation(libs.slf4jApi)

    integTestImplementation(project(":logging"))

    testFixturesImplementation(libs.guava)
    testImplementation(testFixtures(project(":core")))

    integTestDistributionRuntimeOnly(project(":distributions-core"))

    jmh(platform(project(":distributions-dependencies")))
    jmh(libs.bouncycastleProvider)
    jmh(libs.guava)
}

classycle {
    // Needed for the factory methods in the base class
    excludePatterns.add("org/gradle/util/GradleVersion**")
}

jmh.include = listOf("HashingAlgorithmsBenchmark")
