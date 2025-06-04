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
    integTestImplementation(project(":logging"))
    integTestImplementation(libs.mockwebserver) {
        exclude(group = "org.bouncycastle").because("MockWebServer uses a different version of BouncyCastle")
    }
    integTestRuntimeOnly(project(":kotlin-dsl-plugins")) {
        because("Tests require 'future-plugin-versions.properties' on the test classpath")
    }

    integTestDistributionRuntimeOnly(project(":distributions-full"))

    integTestLocalRepository(project(":kotlin-dsl-plugins"))
}

testFilesCleanup.reportOnly.set(true)
