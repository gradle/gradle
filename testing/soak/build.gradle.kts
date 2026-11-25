plugins {
    id("gradlebuild.internal.kotlin")
    id("gradlebuild.kotlin-dsl-plugin-bundle-integ-tests")
}

dependencies {
    testFixturesImplementation(projects.internalIntegTesting)

    testImplementation(testFixtures(projects.kotlinDsl))
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.workers))
    testImplementation(testFixtures(projects.toolchainsJvm))
    testImplementation(testFixtures(projects.toolchainsJvmShared))

    integTestImplementation(projects.launcher)
    integTestImplementation(testFixtures(projects.workers))
    integTestImplementation(testLibs.assertj) {
        because("Kotlin soak tests use AssertJ")
    }

    integTestDistributionRuntimeOnly(projects.distributionsFull)
}

tasks.register("soakTest") {
    description = "Run all soak tests defined in the :soak subproject"
    group = "CI Lifecycle"
    dependsOn(":soak:forkingIntegTest")
}


errorprone {
    nullawayEnabled = true
}
