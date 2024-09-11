plugins {
    id("gradlebuild.internal.kotlin")
    id("gradlebuild.kotlin-dsl-plugin-bundle-integ-tests")
}

dependencies {
    testFixturesImplementation(projects.baseServices)
    testFixturesImplementation(projects.core)
    testFixturesImplementation(projects.logging)
    testFixturesImplementation(projects.internalIntegTesting)
    testFixturesImplementation(projects.jvmServices)

    testImplementation(testFixtures(projects.kotlinDsl))
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.workers))
    testImplementation(testFixtures(projects.toolchainsJvm))

    integTestImplementation(projects.fileWatching)
    integTestImplementation(projects.jvmServices)
    integTestImplementation(projects.launcher)
    integTestImplementation(projects.logging)
    integTestImplementation(projects.persistentCache)
    integTestImplementation(libs.commonsCompress)
    integTestImplementation(libs.slf4jApi)
    integTestImplementation(libs.jetty)
    integTestImplementation(libs.assertj) {
        because("Kotlin soak tests use AssertJ")
    }

    integTestDistributionRuntimeOnly(projects.distributionsFull)
}

tasks.register("soakTest") {
    description = "Run all soak tests defined in the :soak subproject"
    group = "CI Lifecycle"
    dependsOn(":soak:forkingIntegTest")
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
