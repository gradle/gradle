plugins {
    id("gradlebuild.internal.kotlin")
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

    integTestRuntimeOnly(project(":kotlin-dsl-plugins")) {
        because("Tests require 'future-plugin-versions.properties' on the test classpath")
    }

    integTestDistributionRuntimeOnly(project(":distributions-full"))
    integTestLocalRepository(project(":kotlin-dsl-plugins"))

    crossVersionTestImplementation(project(":core-api"))
    crossVersionTestImplementation(project(":logging"))

    crossVersionTestDistributionRuntimeOnly(project(":distributions-full"))
    crossVersionTestLocalRepository(project(":kotlin-dsl-plugins"))
}

testFilesCleanup.reportOnly = true

// Remove as part of fixing https://github.com/gradle/configuration-cache/issues/585
tasks.configCacheIntegTest {
    systemProperties["org.gradle.configuration-cache.internal.test-disable-load-after-store"] = "true"
}
