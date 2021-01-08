plugins {
    id("gradlebuild.internal.kotlin")
}

description = "Kotlin DSL Integration Tests"

dependencies {
    testImplementation(testFixtures(project(":kotlin-dsl")))

    integTestImplementation(project(":base-services"))
    integTestImplementation(project(":core-api"))
    integTestImplementation(project(":core"))
    integTestImplementation(project(":internal-testing"))
    integTestImplementation("com.squareup.okhttp3:mockwebserver:3.9.1")

    integTestRuntimeOnly(project(":kotlin-dsl-plugins")) {
        because("Tests require 'future-plugin-versions.properties' on the test classpath")
    }

    integTestDistributionRuntimeOnly(project(":distributions-full"))

    integTestLocalRepository(project(":kotlin-dsl-plugins"))
}

testFilesCleanup.reportOnly.set(true)
