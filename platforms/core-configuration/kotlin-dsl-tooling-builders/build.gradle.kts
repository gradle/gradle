plugins {
    id("gradlebuild.distribution.implementation-kotlin")
}

description = "Kotlin DSL Tooling Builders for IDEs"

dependencies {
    api(projects.baseServices)
    api(projects.coreApi)
    api(projects.core)
    api(projects.serviceProvider)
    api(libs.kotlinStdlib)

    implementation(projects.classloaders)
    implementation(projects.serviceLookup)
    implementation(projects.stdlibJavaExtensions)
    implementation(projects.time)
    implementation(projects.kotlinDsl)
    implementation(projects.logging)
    implementation(projects.resources)
    implementation(projects.platformBase)
    implementation(projects.platformJvm)
    implementation(projects.pluginsJavaBase)
    implementation(projects.toolingApi)
    implementation(projects.kotlinDslToolingModels)
    implementation(projects.buildProcessServices)

    compileOnly(libs.jspecify)

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

// Kotlin DSL tooling builders should not be part of the public API
// TODO Find a way to not register this and the task instead
configurations.remove(configurations.apiStubElements.get())
