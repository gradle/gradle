plugins {
    id("gradlebuild.internal.kotlin")
}

description = "Kotlin DSL Integration Tests"

dependencies {
    testImplementation(testFixtures("org.gradle:kotlin-dsl"))

    integTestImplementation("org.gradle:base-services")
    integTestImplementation("org.gradle:core-api")
    integTestImplementation("org.gradle:core")
    integTestImplementation("org.gradle:internal-testing")
    integTestImplementation("com.squareup.okhttp3:mockwebserver:3.9.1")

    integTestRuntimeOnly("org.gradle.kotlin:kotlin-dsl-plugins") {
        because("Tests require 'future-plugin-versions.properties' on the test classpath")
    }

    integTestDistributionRuntimeOnly("org.gradle:distributions-full")

    integTestLocalRepository("org.gradle.kotlin:kotlin-dsl-plugins")
}

testFilesCleanup.reportOnly.set(true)
