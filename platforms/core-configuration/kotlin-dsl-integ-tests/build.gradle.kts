plugins {
    id("gradlebuild.internal.kotlin")
    id("gradlebuild.kotlin-dsl-plugin-bundle-integ-tests")
}

description = "Kotlin DSL Integration Tests"

dependencies {
    testImplementation(testFixtures(projects.kotlinDsl))

    integTestImplementation(projects.messaging)
    integTestImplementation(projects.baseServices)
    integTestImplementation(projects.coreApi)
    integTestImplementation(projects.core)
    integTestImplementation(projects.modelCore)
    integTestImplementation(projects.internalTesting)
    integTestImplementation(projects.logging)
    integTestImplementation(projects.languageJvm)
    integTestImplementation(projects.platformJvm)
    integTestImplementation(libs.mockwebserver) {
        exclude(group = "org.bouncycastle").because("MockWebServer uses a different version of BouncyCastle")
    }
    integTestImplementation(libs.kotlinCompilerEmbeddable)
    integTestImplementation(libs.mockitoKotlin)

    integTestDistributionRuntimeOnly(projects.distributionsFull)

    crossVersionTestImplementation(projects.coreApi)
    crossVersionTestImplementation(projects.logging)

    crossVersionTestDistributionRuntimeOnly(projects.distributionsFull)
    crossVersionTestLocalRepository(projects.kotlinDslPlugins)
}

testFilesCleanup.reportOnly = true
