plugins {
    id("gradlebuild.internal.kotlin")
    id("gradlebuild.kotlin-dsl-plugin-bundle-integ-tests")
    id("gradlebuild.cross-version-tests")
}

description = "Kotlin DSL Integration Tests"

dependencies {
    integTestImplementation(projects.baseServices)
    integTestImplementation(projects.core)
    integTestImplementation(projects.coreApi)
    integTestImplementation(projects.domainObjectCollections)
    integTestImplementation(projects.jvmServices)
    integTestImplementation(projects.kotlinDsl)
    integTestImplementation(projects.languageJvm)
    integTestImplementation(projects.logging)
    integTestImplementation(projects.projectFeaturesApi)
    integTestImplementation(projects.serialization)
    integTestImplementation(projects.stdlibJavaExtensions)
    integTestImplementation(projects.testingBaseInfrastructure)
    integTestImplementation(testFixtures(projects.jacoco))
    integTestImplementation(testFixtures(projects.kotlinDsl))
    integTestImplementation(testFixtures(projects.testingBase))
    integTestImplementation(libs.asm)
    integTestImplementation(libs.commonsIo)
    integTestImplementation(libs.groovy)
    integTestImplementation(libs.kotlinCompilerEmbeddable)
    integTestImplementation(testLibs.mockwebserver)
    integTestImplementation(testLibs.mockitoKotlin)
    integTestImplementation(libs.kotlinReflect) {
        because("mockito-kotlin 1.6 requires kotlin-reflect in 1.0.7, we want to overrule that")
    }
    integTestImplementation(testLibs.hamcrest)
    integTestImplementation(testLibs.junit5JupiterApi)
    integTestImplementation(testLibs.spock)
    integTestImplementation(testLibs.okhttp)

    integTestDistributionRuntimeOnly(projects.distributionsFull)

    crossVersionTestImplementation(projects.baseServices)
    crossVersionTestImplementation(projects.internalIntegTesting)
    crossVersionTestImplementation(projects.stdlibJavaExtensions)
    crossVersionTestImplementation(libs.groovy)
    crossVersionTestImplementation(testLibs.spock)

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
