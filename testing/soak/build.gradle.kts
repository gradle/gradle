plugins {
    id("gradlebuild.internal.kotlin")
    id("gradlebuild.kotlin-dsl-plugin-bundle-integ-tests")
}

dependencies {
    testFixturesImplementation(projects.internalIntegTesting)

    integTestImplementation(projects.core)
    integTestImplementation(projects.coreApi)
    integTestImplementation(projects.kotlinDsl)
    integTestImplementation(projects.launcher)
    integTestImplementation(testFixtures(projects.kotlinDsl))
    integTestImplementation(testFixtures(projects.toolchainsJvmShared))
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
