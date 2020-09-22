/*
 * A set of generic services and utilities.
 *
 * Should have a very small set of dependencies, and should be appropriate to embed in an external
 * application (eg as part of the tooling API).
 */

plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.jmh")
}

gradlebuildJava.usedInWorkers()

dependencies {
    api(project(":base-annotations"))
    api(project(":hashing"))
    api(project(":build-operations"))

    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.commonsIo)
    implementation(libs.asm)

    integTestImplementation(project(":logging"))

    testFixturesImplementation(libs.guava)
    testImplementation(testFixtures(project(":core")))

    integTestDistributionRuntimeOnly(project(":distributions-core"))

    jmh(platform(project(":distributions-dependencies")))
    jmh(libs.bouncycastleProvider)
    jmh(libs.guava)
}

jmh.include = listOf("HashingAlgorithmsBenchmark")

moduleIdentity.createBuildReceipt()
