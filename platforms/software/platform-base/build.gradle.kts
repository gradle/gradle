plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    api(projects.baseCompilerWorker)
    api(projects.coreApi)
    api(projects.files)
    api(projects.stdlibJavaExtensions)

    api(libs.guava)
    api(libs.jsr305)

    implementation(projects.execution)
    implementation(projects.baseServices)
    implementation(projects.core)
    implementation(projects.logging)
    implementation(projects.serviceLookup)

    implementation(libs.commonsLang)

    compileOnly(libs.jspecify)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.coreApi))
    testImplementation(projects.native)
    testImplementation(projects.snapshots)
    testImplementation(projects.processServices)

    testFixturesApi(projects.fileCollections)
    testFixturesApi(testFixtures(projects.modelCore))

    testFixturesImplementation(libs.guava)

    testRuntimeOnly(projects.distributionsCore) {
        because("RuntimeShadedJarCreatorTest requires a distribution to access the ...-relocated.txt metadata")
    }
    integTestDistributionRuntimeOnly(projects.distributionsCore)
}

description = """Provides general purpose base types and interfaces for modeling projects, and provides runtime and language support."""
tasks.isolatedProjectsIntegTest {
    enabled = false
}
