plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.instrumented-java-project")
}

description = "Public and internal 'core' Gradle APIs with implementation"

configurations {
    register("reports")
}

tasks.classpathManifest {
    optionalProjects.add("gradle-kotlin-dsl")
    // The gradle-runtime-api-info.jar is added by a 'distributions-...' project if it is on the (integration test) runtime classpath.
    // It contains information services in ':core' need to reason about the complete Gradle distribution.
    // To allow parts of ':core' code to be instantiated in unit tests without relying on this functionality, the dependency is optional.
    optionalProjects.add("gradle-runtime-api-info")
}

// Instrumentation interceptors for tests
// Separated from the test source set since we don't support incremental annotation processor with Java/Groovy joint compilation
sourceSets {
    val testInterceptors = create("testInterceptors") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
    getByName("test") {
        compileClasspath += testInterceptors.output
        runtimeClasspath += testInterceptors.output
    }
}
val testInterceptorsImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.implementation.get())
}

errorprone {
    disabledChecks.addAll(
        "DefaultCharset", // 4 occurrences
        "EmptyBlockTag", // 4 occurrences
        "Finally", // 1 occurrences
        "HidingField", // 1 occurrences
        "IdentityHashMapUsage", // 1 occurrences
        "ImmutableEnumChecker", // 2 occurrences
        "InconsistentCapitalization", // 2 occurrences
        "InlineFormatString", // 2 occurrences
        "InlineMeSuggester", // 1 occurrences
        "InvalidBlockTag", // 1 occurrences
        "InvalidInlineTag", // 1 occurrences
        "MissingCasesInEnumSwitch", // 1 occurrences
        "MixedMutabilityReturnType", // 1 occurrences
        "ModifyCollectionInEnhancedForLoop", // 1 occurrences
        "MutablePublicArray", // 2 occurrences
        "NonApiType", // 1 occurrences
        "NonCanonicalType", // 16 occurrences
        "NotJavadoc", // 1 occurrences
        "OptionalMapUnusedValue", // 1 occurrences
        "ProtectedMembersInFinalClass", // 1 occurrences
        "ReferenceEquality", // 2 occurrences
        "ReturnValueIgnored", // 1 occurrences
        "SameNameButDifferent", // 11 occurrences
        "StreamResourceLeak", // 6 occurrences
        "TypeParameterShadowing", // 1 occurrences
        "TypeParameterUnusedInFormals", // 2 occurrences
        "UndefinedEquals", // 1 occurrences
        "UnrecognisedJavadocTag", // 1 occurrences
        "UnusedMethod", // 18 occurrences
        "UnusedVariable", // 8 occurrences
    )
}

dependencies {
    api(projects.baseAsm)
    api(projects.buildLifecycleApi)
    api(projects.concurrent)
    api(projects.instrumentationAgentServices)
    api(projects.serialization)
    api(projects.serviceLookup)
    api(projects.serviceProvider)
    api(projects.stdlibJavaExtensions)
    api(projects.time)
    api(projects.baseServices)
    api(projects.baseServicesGroovy)
    api(projects.buildCache)
    api(projects.buildCacheBase)
    api(projects.buildCacheLocal)
    api(projects.buildCachePackaging)
    api(projects.buildCacheSpi)
    api(projects.buildOperations)
    api(projects.buildOption)
    api(projects.cli)
    api(projects.coreApi)
    api(projects.declarativeDslApi)
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
    api(projects.internalInstrumentationApi)
    api(projects.jvmServices)
    api(projects.logging)
    api(projects.loggingApi)
    api(projects.messaging)
    api(projects.modelCore)
    api(projects.native)
    api(projects.normalizationJava)
    api(projects.persistentCache)
    api(projects.problemsApi)
    api(projects.processMemoryServices)
    api(projects.processServices)
    api(projects.resources)
    api(projects.snapshots)
    api(projects.workerMain)
    api(projects.buildProcessServices)
    api(projects.instrumentationReporting)

    api(libs.ant)
    api(libs.asm)
    api(libs.asmTree)
    api(libs.groovy)
    api(libs.guava)
    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.buildOperationsTrace)
    implementation(projects.io)
    implementation(projects.inputTracking)
    implementation(projects.modelGroovy)
    implementation(projects.serviceRegistryBuilder)

    implementation(libs.nativePlatform)
    implementation(libs.asmCommons)
    implementation(libs.commonsCompress)
    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.commonsLang3)
    implementation(libs.errorProneAnnotations)
    implementation(libs.fastutil)
    implementation(libs.groovyAnt)
    implementation(libs.groovyJson)
    implementation(libs.groovyXml)
    implementation(libs.slf4jApi)
    implementation(libs.tomlj) {
        // Used for its nullability annotations, not needed at runtime
        exclude("org.checkerframework", "checker-qual")
    }
    implementation(libs.xmlApis)

    compileOnly(libs.kotlinStdlib) {
        because("it needs to forward calls from instrumented code to the Kotlin standard library")
    }

    // Libraries that are not used in this project but required in the distribution
    runtimeOnly(libs.groovyAstbuilder)
    runtimeOnly(libs.groovyConsole)
    runtimeOnly(libs.groovyDateUtil)
    runtimeOnly(libs.groovyDatetime)
    runtimeOnly(libs.groovyDoc)
    runtimeOnly(libs.groovyNio)
    runtimeOnly(libs.groovySql)
    runtimeOnly(libs.groovyTest)

    // The bump to SSHD 2.10.0 causes a global exclusion for `groovy-ant` -> `ant-junit`, so forcing it back in here
    // TODO investigate why we depend on SSHD as a platform for internal-integ-testing
    runtimeOnly(libs.antJunit)

    testImplementation(projects.platformJvm)
    testImplementation(projects.platformNative)
    testImplementation(projects.testingBase)
    testImplementation(libs.jsoup)
    testImplementation(libs.log4jToSlf4j)
    testImplementation(libs.jclToSlf4j)

    testFixturesCompileOnly(libs.jetbrainsAnnotations)

    testFixturesApi(projects.baseServices) {
        because("test fixtures expose Action")
    }
    testFixturesApi(projects.baseServicesGroovy) {
        because("test fixtures expose AndSpec")
    }
    testFixturesApi(projects.coreApi) {
        because("test fixtures expose Task")
    }
    testFixturesApi(projects.logging) {
        because("test fixtures expose Logger")
    }
    testFixturesApi(projects.modelCore) {
        because("test fixtures expose IConventionAware")
    }
    testFixturesApi(projects.buildCache) {
        because("test fixtures expose BuildCacheController")
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
    testFixturesApi(projects.fileTemp) {
        because("test fixtures expose temp file types")
    }
    testFixturesApi(projects.resources) {
        because("test fixtures expose file resource types")
    }
    testFixturesApi(testFixtures(projects.buildOperations)) {
        because("test fixtures expose test build operations runner")
    }
    testFixturesApi(testFixtures(projects.persistentCache)) {
        because("test fixtures expose cross-build cache factory")
    }
    testFixturesApi(projects.processServices) {
        because("test fixtures expose exec handler types")
    }
    testFixturesApi(testFixtures(projects.hashing)) {
        because("test fixtures expose test hash codes")
    }
    testFixturesApi(testFixtures(projects.snapshots)) {
        because("test fixtures expose file snapshot related functionality")
    }
    testFixturesApi(testFixtures(projects.serviceRegistryImpl)) {
        because("test fixtures expose DefaultServiceRegistry")
    }
    testFixturesApi(projects.unitTestFixtures) {
        because("test fixtures expose ProjectBuilder")
    }
    testFixturesImplementation(projects.buildOption)
    testFixturesImplementation(projects.enterpriseOperations)
    testFixturesImplementation(projects.messaging)
    testFixturesImplementation(projects.normalizationJava)
    testFixturesImplementation(projects.persistentCache)
    testFixturesImplementation(projects.snapshots)
    testFixturesImplementation(libs.ant)
    testFixturesImplementation(libs.asm)
    testFixturesImplementation(libs.groovyAnt)
    testFixturesImplementation(libs.guava)
    testFixturesImplementation(projects.internalInstrumentationApi)
    testFixturesImplementation(libs.ivy)
    testFixturesImplementation(libs.slf4jApi)
    testFixturesImplementation(projects.dependencyManagement) {
        because("Used in VersionCatalogErrorMessages for org.gradle.api.internal.catalog.DefaultVersionCatalogBuilder.getExcludedNames")
    }

    testFixturesRuntimeOnly(projects.pluginUse) {
        because("This is a core extension module (see DynamicModulesClassPathProvider.GRADLE_EXTENSION_MODULES)")
    }
    testFixturesRuntimeOnly(projects.workers) {
        because("This is a core extension module (see DynamicModulesClassPathProvider.GRADLE_EXTENSION_MODULES)")
    }
    testFixturesRuntimeOnly(projects.compositeBuilds) {
        because("We always need a BuildStateRegistry service implementation")
    }

    testImplementation(projects.dependencyManagement)

    testImplementation(testFixtures(projects.serialization))
    testImplementation(testFixtures(projects.coreApi))
    testImplementation(testFixtures(projects.messaging))
    testImplementation(testFixtures(projects.modelCore))
    testImplementation(testFixtures(projects.logging))
    testImplementation(testFixtures(projects.baseServices))
    testImplementation(testFixtures(projects.diagnostics))
    testImplementation(testFixtures(projects.snapshots))
    testImplementation(testFixtures(projects.execution))

    integTestImplementation(projects.workers)
    integTestImplementation(projects.dependencyManagement)
    integTestImplementation(projects.launcher)
    integTestImplementation(projects.war)
    integTestImplementation(projects.daemonServices)
    integTestImplementation(libs.jansi)
    integTestImplementation(libs.jetbrainsAnnotations)
    integTestImplementation(libs.jetty)
    integTestImplementation(libs.littleproxy)
    integTestImplementation(testFixtures(projects.native))
    integTestImplementation(testFixtures(projects.fileTemp))

    testRuntimeOnly(projects.distributionsCore) {
        because("This is required by ProjectBuilder, but ProjectBuilder cannot declare :distributions-core as a dependency due to conflicts with other distributions.")
    }

    integTestDistributionRuntimeOnly(projects.distributionsJvm) {
        because("Some tests utilise the 'java-gradle-plugin' and with that TestKit, some also use the 'war' plugin")
    }
    crossVersionTestDistributionRuntimeOnly(projects.distributionsCore)

    annotationProcessor(projects.internalInstrumentationProcessor)
    annotationProcessor(platform(projects.distributionsDependencies))

    testInterceptorsImplementation(platform(projects.distributionsDependencies))
    "testInterceptorsAnnotationProcessor"(projects.internalInstrumentationProcessor)
    "testInterceptorsAnnotationProcessor"(platform(projects.distributionsDependencies))
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
    ignoreAnnotationProcessing() // Without this, javac will complain about unclaimed annotations
}

packageCycles {
    excludePatterns.add("org/gradle/**")
}

tasks.test {
    setForkEvery(200)
}

tasks.compileTestGroovy {
    groovyOptions.isFork = true
    groovyOptions.forkOptions.run {
        memoryInitialSize = "128M"
        memoryMaximumSize = "1G"
    }
}

integTest.usesJavadocCodeSnippets = true
testFilesCleanup.reportOnly = true
