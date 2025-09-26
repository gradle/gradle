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

    implementation(libs.jetbrainsAnnotations)
    implementation(libs.kotlinReflect)
    implementation(libs.kotlinxSerializationCore)
    implementation(libs.kotlinxSerializationJson)
    implementation(projects.declarativeDslApi)

    testImplementation(libs.jetbrainsAnnotations)
    testImplementation(projects.coreApi)

    testFixturesImplementation(libs.kotlinReflect)

    integTestImplementation(testFixtures(projects.kotlinDsl))

    integTestDistributionRuntimeOnly(projects.distributionsFull)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
