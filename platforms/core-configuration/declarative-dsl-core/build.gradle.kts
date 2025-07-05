plugins {
    id("gradlebuild.distribution.implementation-kotlin")
    id("gradlebuild.publish-public-libraries")
    id("gradlebuild.kotlin-dsl-plugin-bundle-integ-tests")

    embeddedKotlin("plugin.serialization")
}

description = "Common shared classes used by the Declarative DSL"

dependencies {
    api(projects.declarativeDslToolingModels)

    api(libs.kotlinStdlib)
    api(libs.kotlinCompilerEmbeddable)

    implementation(projects.declarativeDslApi)
    implementation(libs.kotlinReflect)
    implementation(libs.kotlinxSerializationCore)
    implementation(libs.kotlinxSerializationJson)

    testImplementation(projects.coreApi)
    testImplementation("org.jetbrains:annotations:24.0.1")

    testFixturesImplementation(libs.kotlinReflect)

    integTestImplementation(testFixtures(projects.kotlinDsl))

    integTestDistributionRuntimeOnly(projects.distributionsFull)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
