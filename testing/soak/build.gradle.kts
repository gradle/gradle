plugins {
    id("gradlebuild.internal.kotlin")
    id("gradlebuild.kotlin-dsl-plugin-bundle-integ-tests")
}

dependencies {
    testFixturesImplementation(projects.internalDistributionTesting)
    testFixturesImplementation(projects.stdlibJavaExtensions)

    integTestImplementation(projects.coreApi)
    integTestImplementation(projects.fileCollections)
    integTestImplementation(projects.fileWatching)
    integTestImplementation(projects.kotlinDsl)
    integTestImplementation(projects.messaging)
    integTestImplementation(projects.persistentCache)
    integTestImplementation(testFixtures(projects.kotlinDsl))
    integTestImplementation(testFixtures(projects.toolchainsJvmShared))
    integTestImplementation(testFixtures(projects.workers))
    integTestImplementation(libs.groovy)
    integTestImplementation(libs.kotlinCompilerEmbeddable)
    integTestImplementation(testLibs.assertj) {
        because("Kotlin soak tests use AssertJ")
    }
    integTestImplementation(testLibs.opentest4j)
    integTestImplementation(testLibs.spock)

    integTestDistributionRuntimeOnly(projects.distributionsFull)

    testFixturesApi(projects.baseServices)
    testFixturesApi(projects.internalIntegTesting)
    testFixturesApi(projects.jvmServices)

    integTestRuntimeOnly(projects.core)
    integTestRuntimeOnly(projects.launcher)
}

tasks.register("soakTest") {
    description = "Run all soak tests defined in the :soak subproject"
    group = "CI Lifecycle"
    dependsOn(":soak:forkingIntegTest")
}

errorprone {
    nullawayEnabled = true
}
