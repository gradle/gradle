plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.cross-version-tests")
}

description = "Public and internal 'core' Gradle APIs with implementation"

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
    api(projects.ant)
    api(projects.antApi)
    api(projects.baseServices)
    api(projects.baseServicesGroovy)
    api(projects.buildCache)
    api(projects.buildCacheBase)
    api(projects.buildCacheCore)
    api(projects.buildDiscovery)
    api(projects.buildDiscoveryImpl)
    api(projects.buildInitSpecs)
    api(projects.buildOperations)
    api(projects.buildOption)
    api(projects.buildProcessServices)
    api(projects.classpath)
    api(projects.classloaders)
    api(projects.cli)
    api(projects.collections)
    api(projects.concurrent)
    api(projects.configurationProblemsBase)
    api(projects.coreApi)
    api(projects.credentialsApi)
    api(projects.daemonMessaging)
    api(projects.declarativeDslApi)
    api(projects.domainObjectCollections)
    api(projects.enterpriseLogging)
    api(projects.enterpriseOperations)
    api(projects.execution)
    api(projects.fileCollections)
    api(projects.fileOperations)
    api(projects.fileTemp)
    api(projects.fileWatching)
    api(projects.files)
    api(projects.functional)
    api(projects.hashing)
    api(projects.hashingServices)
    api(projects.instrumentationAgentServices)
    api(projects.instrumentationReporting)
    api(projects.jvmServices)
    api(projects.logging)
    api(projects.loggingApi)
    api(projects.messaging)
    api(projects.modelCore)
    api(projects.modelReflect)
    api(projects.native)
    api(projects.normalization)
    api(projects.normalizationApi)
    api(projects.normalizationJava)
    api(projects.persistentCache)
    api(projects.problemsApi)
    api(projects.processServices)
    api(projects.processServicesApi)
    api(projects.processServicesBase)
    api(projects.resources)
    api(projects.scopedPersistentCache)
    api(projects.serialization)
    api(projects.serviceLookup)
    api(projects.serviceProvider)
    api(projects.snapshots)
    api(projects.projectFeatures)
    api(projects.startParameter)
    api(projects.time)
    api(projects.toolingApi)
    api(projects.versionedCache)
    api(projects.workerShared)

    api(libs.asm)
    api(libs.asmTree)
    api(libs.groovy)
    api(libs.guava)
    api(libs.inject)
    api(libs.jspecify)
    api(libs.jsr305)

    implementation(projects.buildCachePackaging)
    implementation(projects.buildCacheSpi)
    implementation(projects.buildDiscoveryReporting)
    implementation(projects.buildOperationsTrace)
    implementation(projects.daemonLogging)
    implementation(projects.modelGroovy)
    implementation(projects.problemsRendering)
    implementation(projects.processMemoryServices)
    implementation(projects.serviceRegistryBuilder)
    implementation(projects.coreFlowServicesApi) {
        because("DefaultBuildServicesRegistry has ordering dependency with FlowScope")
    }
    implementation(projects.projectFeaturesApi)
    implementation(projects.workerProcessServices)

    implementation(libs.ant)
    implementation(libs.commonsCompress)
    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.errorProneAnnotations)
    implementation(libs.fastutil)
    implementation(libs.groovyJson)
    implementation(libs.groovyXml)
    implementation(libs.nativePlatform)
    implementation(libs.slf4jApi)
    implementation(libs.tomlj) {
        // Used for its nullability annotations, not needed at runtime
        exclude("org.checkerframework", "checker-qual")
    }


    compileOnly(libs.kotlinStdlib) {
        because("it needs to forward calls from instrumented code to the Kotlin standard library")
    }

    // Libraries that are not used in this project but required in the distribution
    runtimeOnly(libs.groovyAstbuilder)
    runtimeOnly(libs.groovyDateUtil)
    runtimeOnly(libs.groovyDatetime)
    runtimeOnly(libs.groovyDoc)
    runtimeOnly(libs.groovyNio)
    runtimeOnly(projects.groovyLoader)

    testImplementation(projects.baseDiagnostics)
    testImplementation(projects.buildInit)
    testImplementation(projects.internalIntegTesting)
    testImplementation(projects.io)
    testImplementation(projects.platformJvm)
    testImplementation(projects.stdlibJavaExtensions)
    testImplementation(projects.testingBase)
    testImplementation(libs.jsoup)

    testFixturesApi(projects.antApi)
    testFixturesApi(projects.baseServices) {
        because("test fixtures expose Action")
    }
    testFixturesApi(projects.buildOperations)
    testFixturesApi(projects.classloaders)
    testFixturesApi(projects.concurrent)
    testFixturesApi(projects.coreApi) {
        because("test fixtures expose Task")
    }
    testFixturesApi(projects.domainObjectCollections)
    testFixturesApi(projects.enterpriseLogging)
    testFixturesApi(projects.logging) {
        because("test fixtures expose Logger")
    }
    testFixturesApi(projects.modelCore) {
        because("test fixtures expose IConventionAware")
    }
    testFixturesApi(projects.execution) {
        because("test fixtures expose OutputChangeListener")
    }
    testFixturesApi(projects.native) {
        because("test fixtures expose FileSystem")
    }
    testFixturesApi(projects.fileCollections) {
        because("test fixtures expose file collection types")
    }
    testFixturesApi(projects.fileOperations)
    testFixturesApi(projects.fileTemp) {
        because("test fixtures expose temp file types")
    }
    testFixturesApi(projects.files)
    testFixturesApi(projects.hashing)
    testFixturesApi(projects.persistentCache)
    testFixturesApi(projects.problemsApi)
    testFixturesApi(projects.resources) {
        because("test fixtures expose file resource types")
    }
    testFixturesApi(testFixtures(projects.buildOperations)) {
        because("test fixtures expose test build operations runner")
    }
    testFixturesImplementation(testFixtures(projects.persistentCache)) {
        because("test fixtures use cross-build cache factory")
    }
    testFixturesApi(projects.processServices) {
        because("test fixtures expose exec handler types")
    }
    testFixturesApi(projects.processServicesBase)
    testFixturesApi(projects.serialization)
    testFixturesApi(projects.serviceLookup)
    testFixturesApi(projects.serviceProvider)
    testFixturesApi(projects.serviceRegistryImpl)
    testFixturesApi(projects.snapshots)
    testFixturesApi(projects.time)
    testFixturesImplementation(testFixtures(projects.hashing)) {
        because("test fixtures use test hash codes")
    }
    testFixturesApi(testFixtures(projects.snapshots)) {
        because("test fixtures expose file snapshot related functionality")
    }
    testFixturesApi(projects.unitTestFixtures) {
        because("test fixtures expose ProjectBuilder")
    }
    testFixturesApi(libs.ant)
    testFixturesApi(libs.guava)
    testFixturesApi(libs.jspecify)
    testFixturesApi(libs.jsr305)
    testFixturesApi(testLibs.hamcrest)
    testFixturesImplementation(projects.problemsReporting)
    testFixturesImplementation(projects.ant)
    testFixturesApi(projects.dependencyManagement) {
        because("Used in VersionCatalogErrorMessages for org.gradle.api.internal.catalog.DefaultVersionCatalogBuilder.getExcludedNames")
    }
    testFixturesImplementation(projects.stdlibJavaExtensions)

    testFixturesRuntimeOnly(projects.pluginUse) {
        because("This is a core extension module (see DynamicModulesClassPathProvider.GRADLE_EXTENSION_MODULES)")
    }
    testFixturesRuntimeOnly(projects.workers) {
        because("This is a core extension module (see DynamicModulesClassPathProvider.GRADLE_EXTENSION_MODULES)")
    }
    testFixturesRuntimeOnly(projects.compositeBuilds) {
        because("We always need a BuildStateRegistry service implementation")
    }

    testImplementation(testFixtures(projects.domainObjectCollections))
    testImplementation(testFixtures(projects.modelReflect))
    testImplementation(testFixtures(projects.baseServices))
    testImplementation(testFixtures(projects.hashing))
    testImplementation(testFixtures(projects.logging))
    testImplementation(testFixtures(projects.persistentCache))
    testImplementation(testFixtures(projects.snapshots))
    testImplementation(testFixtures(projects.time))
    testImplementation(testLibs.hamcrest)

    testRuntimeOnly(projects.distributionsCore) {
        because("This is required by ProjectBuilder, but ProjectBuilder cannot declare :distributions-core as a dependency due to conflicts with other distributions.")
    }
    testRuntimeOnly(libs.jclToSlf4j)

    integTestImplementation(projects.groovyDsl) {
        because("Tests import DefaultScriptCompilationHandler.ScriptClassLoader")
    }
    integTestImplementation(projects.daemonServices)
    integTestImplementation(projects.languageJvm)
    integTestImplementation(projects.testingBaseInfrastructure)
    integTestImplementation(testFixtures(projects.domainObjectCollections))
    integTestImplementation(testFixtures(projects.native))
    integTestImplementation(testFixtures(projects.launcher))
    integTestImplementation(testFixtures(projects.processServices))
    integTestImplementation(testFixtures(projects.testingBase))

    integTestDistributionRuntimeOnly(projects.distributionsJvm) {
        because("Some tests utilise the 'java-gradle-plugin' and with that TestKit, some also use the 'war' plugin")
    }

    crossVersionTestImplementation(projects.internalIntegTesting)
    crossVersionTestImplementation(testLibs.spockJUnit4) {
        because("Required for @org.junit.Rule")
    }

    crossVersionTestDistributionRuntimeOnly(projects.distributionsCore)

    annotationProcessor(projects.internalInstrumentationProcessor)
    annotationProcessor(platform(projects.distributionsDependencies))

    // Javadoc-only: downstream modules whose types are referenced by {@link ...} in this module's docs.
    javadocReferences(projects.credentials)
    javadocReferences(projects.buildState)

    integTestRuntimeOnly(projects.war)
}

gradleModule {
    computedRuntimes {
        // Auto-generated by `:checkTargetRuntimes --fix`
        client = true
        daemon = true
    }
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
    ignoreAnnotationProcessing() // Without this, javac will complain about unclaimed annotations
}

tasks.test {
    setForkEvery(200)
}

integTest.testJvmXmx = "1g"

tasks.compileTestGroovy {
    groovyOptions.isFork = true
    groovyOptions.forkOptions.run {
        memoryInitialSize = "128M"
        memoryMaximumSize = "1G"
    }
}

testFilesCleanup.reportOnly = true
