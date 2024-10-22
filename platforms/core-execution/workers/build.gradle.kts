plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Infrastructure for starting and managing worker processes"

dependencies {
    api(projects.baseServices)
    api(projects.buildOperations)
    api(projects.concurrent)
    api(projects.core)
    api(projects.coreApi)
    api(projects.hashing)
    api(projects.stdlibJavaExtensions)
    api(projects.logging)
    api(projects.loggingApi)
    api(projects.messaging)
    api(projects.modelCore)
    api(projects.processMemoryServices)
    api(projects.serialization)
    api(projects.serviceLookup)
    api(projects.serviceProvider)
    api(projects.snapshots)
    api(projects.workerMain)
    api(projects.buildProcessServices)

    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.buildLifecycleApi)
    implementation(projects.fileCollections)
    implementation(projects.fileOperations)
    implementation(projects.time)
    implementation(projects.serviceRegistryBuilder)

    implementation(libs.slf4jApi)
    implementation(libs.guava)

    testImplementation(projects.native)
    testImplementation(projects.fileCollections)
    testImplementation(projects.resources)
    testImplementation(projects.snapshots)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.logging))

    integTestRuntimeOnly(projects.kotlinDsl)
    integTestRuntimeOnly(projects.kotlinDslProviderPlugins)
    integTestRuntimeOnly(projects.apiMetadata)
    integTestRuntimeOnly(projects.testKit)

    integTestImplementation(projects.jvmServices)
    integTestImplementation(projects.enterpriseOperations)

    testFixturesImplementation(libs.inject)
    testFixturesImplementation(libs.groovyJson)
    testFixturesImplementation(projects.baseServices)

    testRuntimeOnly(projects.distributionsCore) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(projects.distributionsJvm) {
        because("Uses application plugin.")
    }
}

integTest.usesJavadocCodeSnippets = true
tasks.isolatedProjectsIntegTest {
    enabled = false
}
