plugins {
    id("gradlebuild.distribution.implementation-kotlin")
}

description = "Kotlin DSL Tooling Builders for IDEs"

dependencies {
    api(projects.coreApi)
    api(projects.core)
    api(libs.kotlinStdlib)

    implementation(projects.classloading)
    implementation(projects.serviceLookup)
    implementation(projects.stdlibJavaExtensions)
    implementation(projects.time)
    implementation(projects.kotlinDsl)
    implementation(projects.baseServices)
    implementation(projects.resources)
    implementation(projects.platformBase)
    implementation(projects.platformJvm)
    implementation(projects.pluginsJavaBase)
    implementation(projects.toolingApi)
    implementation(projects.logging)
    implementation(projects.kotlinDslToolingModels)
    implementation(projects.buildProcessServices)

    testImplementation(testFixtures(projects.kotlinDsl))
    integTestImplementation(testFixtures(projects.toolingApi))

    integTestImplementation(projects.internalTesting)
    testFixturesImplementation(projects.kotlinDsl)
    testFixturesImplementation(projects.toolingApi)
    testFixturesImplementation(projects.internalIntegTesting)

    crossVersionTestImplementation(projects.persistentCache)
    crossVersionTestImplementation(libs.slf4jApi)
    crossVersionTestImplementation(libs.guava)
    crossVersionTestImplementation(libs.ant)

    integTestDistributionRuntimeOnly(projects.distributionsBasics)
    crossVersionTestDistributionRuntimeOnly(projects.distributionsJvm) {
        because("Uses application plugin.")
    }
}

testFilesCleanup.reportOnly = true
tasks.isolatedProjectsIntegTest {
    enabled = false
}
