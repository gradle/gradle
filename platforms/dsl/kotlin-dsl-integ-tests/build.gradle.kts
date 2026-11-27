plugins {
    id("gradlebuild.internal.kotlin")
    id("gradlebuild.kotlin-dsl-plugin-bundle-integ-tests")
    id("gradlebuild.cross-version-tests")
}

description = "Kotlin DSL Integration Tests"

dependencies {
    integTestImplementation(projects.kotlinDsl)
    integTestImplementation(projects.languageJvm)
    integTestImplementation(testFixtures(projects.jacoco))
    integTestImplementation(testFixtures(projects.kotlinDsl))
    integTestImplementation(testFixtures(projects.testingBase))
    integTestImplementation(testLibs.mockwebserver)
    integTestImplementation(testLibs.mockitoKotlin)
    integTestImplementation(libs.kotlinReflect) {
        because("mockito-kotlin 1.6 requires kotlin-reflect in 1.0.7, we want to overrule that")
    }

    integTestDistributionRuntimeOnly(projects.distributionsFull)

    crossVersionTestImplementation(projects.internalIntegTesting)

    crossVersionTestDistributionRuntimeOnly(projects.distributionsFull)

    crossVersionTestLocalRepository(projects.kotlinDslPlugins)

    testRuntimeOnly(projects.classpath)
    testRuntimeOnly(projects.core)
    testRuntimeOnly(projects.coreApi)
    testRuntimeOnly(projects.credentialsApi)
    testRuntimeOnly(projects.domainObjectCollections)
    testRuntimeOnly(projects.logging)
    testRuntimeOnly(projects.normalization)
    testRuntimeOnly(projects.processServices)
}

testFilesCleanup.reportOnly = true


errorprone {
    nullawayEnabled = true
}
