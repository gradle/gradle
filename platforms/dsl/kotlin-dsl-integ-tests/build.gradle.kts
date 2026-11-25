plugins {
    id("gradlebuild.internal.kotlin")
    id("gradlebuild.kotlin-dsl-plugin-bundle-integ-tests")
    id("gradlebuild.cross-version-tests")
}

description = "Kotlin DSL Integration Tests"

dependencies {
    testImplementation(testFixtures(projects.kotlinDsl))

    integTestImplementation(projects.languageJvm)
    integTestImplementation(testFixtures(projects.jacoco))
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
}

testFilesCleanup.reportOnly = true


errorprone {
    nullawayEnabled = true
}
