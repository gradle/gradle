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
    api(project(":baseAnnotations"))
    api(project(":hashing"))

    implementation(libs.slf4j_api)
    implementation(libs.guava)
    implementation(libs.commons_lang)
    implementation(libs.commons_io)
    implementation(libs.asm)

    integTestImplementation(project(":logging"))

    testFixturesImplementation(libs.guava)
    testImplementation(testFixtures(project(":core")))

    integTestDistributionRuntimeOnly(project(":distributionsCore"))

    jmh("org.bouncycastle:bcprov-jdk15on:1.61")
    jmh("com.google.guava:guava:27.1-android")
}

jmh.include = listOf("HashingAlgorithmsBenchmark")

moduleIdentity.createBuildReceipt()
