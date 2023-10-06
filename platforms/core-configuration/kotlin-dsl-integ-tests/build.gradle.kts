plugins {
    id("gradlebuild.internal.kotlin")
    id("gradlebuild.kotlin-dsl-plugin-bundle-integ-tests")
}

description = "Kotlin DSL Integration Tests"

dependencies {
    testImplementation(testFixtures(project(":kotlin-dsl")))

    integTestImplementation(project(":base-services"))
    integTestImplementation(project(":core-api"))
    integTestImplementation(project(":core"))
    integTestImplementation(project(":model-core"))
    integTestImplementation(project(":internal-testing"))
    integTestImplementation(project(":logging"))
    integTestImplementation("com.squareup.okhttp3:mockwebserver:3.9.1")

    integTestDistributionRuntimeOnly(project(":distributions-full"))

    crossVersionTestImplementation(project(":core-api"))
    crossVersionTestImplementation(project(":logging"))

    crossVersionTestDistributionRuntimeOnly(project(":distributions-full"))
    crossVersionTestLocalRepository(project(":kotlin-dsl-plugins"))
}

testFilesCleanup.reportOnly = true
