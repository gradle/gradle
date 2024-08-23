plugins {
    id("gradlebuild.distribution.implementation-kotlin")
    id("gradlebuild.kotlin-dsl-sam-with-receiver")
    id("gradlebuild.kotlin-experimental-contracts")
}

description = "Configuration cache implementation"

// The integration tests in this project do not need to run in 'config cache' mode.
tasks.configCacheIntegTest {
    enabled = false
}

dependencies {
    api(projects.baseServices)
    api(projects.concurrent)
    api(projects.configurationCacheBase)
    api(projects.configurationProblemsBase)
    api(projects.core)
    api(projects.coreApi)
    api(projects.dependencyManagement)
    api(projects.fileTemp)
    api(projects.loggingApi)
    api(projects.messaging)
    api(projects.native)
    api(projects.pluginUse)
    api(projects.resources)
    api(projects.serviceLookup)
    api(projects.serviceProvider)
    api(projects.stdlibJavaExtensions)
    api(projects.snapshots)

    api(libs.groovy)
    api(libs.inject)
    api(libs.kotlinStdlib)

    // TODO - it might be good to allow projects to contribute state to save and restore, rather than have this project know about everything
    implementation(projects.buildEvents)
    implementation(projects.buildOperations)
    implementation(projects.buildOption)
    implementation(projects.coreKotlinExtensions)
    implementation(projects.coreSerializationCodecs)
    implementation(projects.dependencyManagementSerializationCodecs)
    implementation(projects.encryptionServices)
    implementation(projects.enterpriseOperations)
    implementation(projects.execution)
    implementation(projects.fileCollections)
    implementation(projects.fileWatching)
    implementation(projects.files)
    implementation(projects.flowServices)
    implementation(projects.functional)
    implementation(projects.graphSerialization)
    implementation(projects.guavaSerializationCodecs)
    implementation(projects.hashing)
    implementation(projects.inputTracking)
    implementation(projects.instrumentationAgentServices)
    implementation(projects.logging)
    implementation(projects.modelCore)
    implementation(projects.persistentCache)
    implementation(projects.problemsApi)
    implementation(projects.processServices)
    implementation(projects.serialization)
    implementation(projects.stdlibKotlinExtensions)
    implementation(projects.stdlibSerializationCodecs)
    implementation(projects.toolingApi)

    implementation(libs.fastutil)
    implementation(libs.guava)
    implementation(libs.slf4jApi)

    runtimeOnly(projects.beanSerializationServices)
    runtimeOnly(projects.compositeBuilds)
    runtimeOnly(projects.resourcesHttp)
    // TODO - move the isolatable serializer to model-core to live with the isolatable infrastructure
    runtimeOnly(projects.workers)

    runtimeOnly(libs.kotlinReflect)

    testImplementation(projects.beanSerializationServices)
    testImplementation(projects.io)
    testImplementation(testFixtures(projects.core))
    testImplementation(libs.mockitoKotlin2)
    testImplementation(libs.kotlinCoroutinesDebug)

    integTestImplementation(projects.jvmServices)
    integTestImplementation(projects.toolingApi)
    integTestImplementation(projects.platformJvm)
    integTestImplementation(projects.testKit)
    integTestImplementation(projects.launcher)
    integTestImplementation(projects.cli)
    integTestImplementation(projects.workers)

    integTestImplementation(libs.guava)
    integTestImplementation(libs.ant)
    integTestImplementation(libs.inject)
    integTestImplementation("com.microsoft.playwright:playwright:1.20.1")

    integTestImplementation(testFixtures(projects.toolingApi))
    integTestImplementation(testFixtures(projects.dependencyManagement))
    integTestImplementation(testFixtures(projects.jacoco))
    integTestImplementation(testFixtures(projects.modelCore))

    crossVersionTestImplementation(projects.cli)

    testRuntimeOnly(projects.distributionsCore) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(projects.distributionsFull) {
        because("Includes tests for builds with the enterprise plugin and TestKit involved; ConfigurationCacheJacocoIntegrationTest requires JVM distribution")
    }
    crossVersionTestDistributionRuntimeOnly(projects.distributionsCore)
}

packageCycles {
    excludePatterns.add("org/gradle/internal/cc/**")
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
