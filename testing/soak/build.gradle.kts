plugins {
    id("gradlebuild.internal.kotlin")
    id("gradlebuild.kotlin-dsl-plugin-bundle-integ-tests")
}

dependencies {
    testFixturesImplementation(projects.internalDistributionTesting)
    testFixturesImplementation(projects.stdlibJavaExtensions)

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

    testFixturesApi(projects.baseServices)
    testFixturesApi(projects.internalIntegTesting)
    testFixturesApi(projects.jvmServices)
}

tasks.register("soakTest") {
    description = "Run all soak tests defined in the :soak subproject"
    group = "CI Lifecycle"
    dependsOn(":soak:forkingIntegTest")
}


errorprone {
    nullawayEnabled = true
}
