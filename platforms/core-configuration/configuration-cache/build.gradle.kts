plugins {
    id("gradlebuild.distribution.implementation-kotlin")
    id("gradlebuild.kotlin-dsl-sam-with-receiver")
    id("gradlebuild.kotlin-experimental-contracts")
    id("gradlebuild.cross-version-tests")
}

description = "Configuration cache implementation"

// The integration tests in this project do not need to run in 'config cache' mode.
tasks.configCacheIntegTest {
    enabled = false
}

// The integration tests in this project do not need to run in 'isolated projects' mode.
tasks.isolatedProjectsIntegTest {
    enabled = false
}

dependencyAnalysis {
    issues {
        sourceSet("integTest") {
            onRuntimeOnly {
                // DAGP does not see Groovy imports of nested classes
                exclude(":groovy-dsl")
            }
        }
    }
}

dependencies {
    api(projects.baseServices)
    api(projects.buildOperations)
    api(projects.buildOption)
    api(projects.concurrent)
    api(projects.configurationCacheBase)
    api(projects.configurationProblemsBase)
    api(projects.coreSerializationCodecs)
    api(projects.core)
    api(projects.coreApi)
    api(projects.processServices)
    api(projects.dependencyManagement)
    api(projects.dependencyManagementResources)
    api(projects.fileTemp)
    api(projects.graphSerialization)
    api(projects.loggingApi)
    api(projects.native)
    api(projects.resources)
    api(projects.serviceLookup)
    api(projects.serviceProvider)
    api(projects.snapshots)
    api(projects.startParameter)
    api(projects.stdlibJavaExtensions)

    api(libs.groovy)
    api(libs.inject)
    api(libs.kotlinStdlib)

    // TODO - it might be good to allow projects to contribute state to save and restore, rather than have this project know about everything
    implementation(projects.buildCacheCore)
    implementation(projects.buildDiscovery)
    implementation(projects.buildDiscoveryImpl)
    implementation(projects.buildProcessServices)
    implementation(projects.classloaders)
    implementation(projects.classpath)
    implementation(projects.coreFlowServicesApi)
    implementation(projects.coreKotlinExtensions)
    implementation(projects.daemonServices)
    implementation(projects.dependencyManagementSerializationCodecs)
    implementation(projects.encryptionServices)
    implementation(projects.enterpriseOperations)
    implementation(projects.execution)
    implementation(projects.fileCollections)
    implementation(projects.fileOperations)
    implementation(projects.fileWatching)
    implementation(projects.files)
    implementation(projects.flowServices)
    implementation(projects.functional)
    implementation(projects.hashing)
    implementation(projects.inputTracking)
    implementation(projects.instrumentationAgentServices)
    implementation(projects.reflectionServices)
    implementation(projects.logging)
    implementation(projects.messaging)
    implementation(projects.modelCore)
    implementation(projects.normalization)
    implementation(projects.persistentCache)
    implementation(projects.pluginUse)
    implementation(projects.problemsApi)
    implementation(projects.scopedPersistentCache)
    implementation(projects.serialization)
    implementation(projects.stdlibKotlinExtensions)
    implementation(projects.stdlibSerializationCodecs)
    implementation(projects.toolingApi)

    implementation(libs.configurationCacheReport) {
        because("The configuration cache report summary is handed to the report writer here")
    }
    implementation(libs.guava)
    implementation(libs.fastutil)
    implementation(libs.kryo)
    implementation(libs.slf4jApi)

    compileOnly(libs.jspecify)

    runtimeOnly(projects.beanSerializationServices)
    runtimeOnly(projects.compositeBuilds)
    // TODO - move the isolatable serializer to model-core to live with the isolatable infrastructure
    runtimeOnly(projects.workers)

    testImplementation(testFixtures(projects.beanSerializationServices))
    testImplementation(projects.io)
    testImplementation(testFixtures(projects.hashing))
    testImplementation(libs.jspecify)
    testImplementation(testLibs.hamcrest)
    testImplementation(testLibs.junit5JupiterApi)
    testImplementation(testLibs.mockitoKotlin)

    testRuntimeOnly(projects.distributionsCore) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    testRuntimeOnly(testLibs.kotlinxCoroutinesDebug)

    integTestImplementation(projects.buildOperationsTrace)
    integTestImplementation(projects.groovyDsl) {
        because("Tests import DefaultScriptCompilationHandler.ScriptClassLoader")
    }
    integTestImplementation(projects.daemonServerWorker)
    integTestImplementation(projects.ide)
    integTestImplementation(projects.launcher)
    integTestImplementation(projects.platformJvm)
    integTestImplementation(projects.testKit)
    integTestImplementation(testFixtures(projects.core))
    integTestImplementation(testFixtures(projects.dependencyManagement))

    integTestImplementation(testLibs.playwright)

    integTestImplementation(testFixtures(projects.toolingApi))
    integTestImplementation(testFixtures(projects.kotlinDslToolingBuilders))
    integTestImplementation(testFixtures(projects.jacoco))
    integTestImplementation(testFixtures(projects.modelReflect))
    integTestImplementation(testFixtures(projects.processServices))
    integTestImplementation(libs.ant)
    integTestImplementation(libs.groovyJson)
    integTestImplementation(libs.jsr305)
    integTestImplementation(testLibs.spock)

    integTestDistributionRuntimeOnly(projects.distributionsFull) {
        because("Includes tests for builds with the enterprise plugin and TestKit involved; ConfigurationCacheJacocoIntegrationTest requires JVM distribution")
    }

    crossVersionTestImplementation(projects.internalIntegTesting)

    crossVersionTestDistributionRuntimeOnly(projects.distributionsCore)

}

gradleModule {
    computedRuntimes {
        // Auto-generated by `:checkTargetRuntimes --fix`
        daemon = true
    }
}
