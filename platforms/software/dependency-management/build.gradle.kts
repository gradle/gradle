plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = """This project contains most of the dependency management logic of Gradle:
    |* the resolution engine,
    |* how to retrieve and process dependencies and their metadata,
    |* the dependency locking and verification implementations.
    |
    |DSL facing APIs are to be found in 'core-api'""".trimMargin()

errorprone {
    disabledChecks.addAll(
        "AmbiguousMethodReference", // 1 occurrences
        "ClassCanBeStatic",
        "DefaultCharset", // 3 occurrences
        "Finally", // 4 occurrences
        "IdentityHashMapUsage", // 2 occurrences
        "InlineFormatString", // 5 occurrences
        "InvalidParam", // 1 occurrences
        "MutablePublicArray", // 1 occurrences
        "NonApiType", // 3 occurrences
        "NonCanonicalType", // 3 occurrences
        "ReferenceEquality", // 10 occurrences
        "StringCharset", // 1 occurrences
        "TypeParameterShadowing", // 4 occurrences
        "TypeParameterUnusedInFormals", // 2 occurrences
        "UndefinedEquals", // 1 occurrences
        "UnusedMethod", // 34 occurrences
    )
}


dependencies {
    api(projects.baseServices)
    api(projects.buildOperations)
    api(projects.buildOption)
    api(projects.buildProcessServices)
    api(projects.classloaders)
    api(projects.concurrent)
    api(projects.core)
    api(projects.coreApi)
    api(projects.enterpriseLogging)
    api(projects.enterpriseOperations)
    api(projects.execution)
    api(projects.fileCollections)
    api(projects.fileTemp)
    api(projects.files)
    api(projects.functional)
    api(projects.hashing)
    api(projects.logging)
    api(projects.messaging)
    api(projects.modelCore)
    api(projects.modelReflect)
    api(projects.persistentCache)
    api(projects.problemsApi)
    api(projects.resources)
    api(projects.scopedPersistentCache)
    api(projects.security)
    api(projects.serialization)
    api(projects.serviceLookup)
    api(projects.serviceProvider)
    api(projects.snapshots)
    api(projects.stdlibJavaExtensions)
    api(projects.versionedCache)

    api(libs.bouncycastlePgp)
    api(libs.groovy)
    api(libs.guava)
    api(libs.inject)
    api(libs.ivy)
    api(libs.jspecify)
    api(libs.jsr305)
    api(libs.maven3Settings)
    api(libs.maven3SettingsBuilder)
    api(libs.slf4jApi)

    implementation(projects.fileOperations)
    implementation(projects.time)
    implementation(projects.baseAsm)
    implementation(projects.baseServicesGroovy)
    implementation(projects.loggingApi)
    implementation(projects.resourcesHttp)
    implementation(projects.serviceRegistryBuilder)

    implementation(libs.asm)
    implementation(libs.asmCommons)
    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.fastutil)
    implementation(libs.gson)
    implementation(libs.httpcore)

    testImplementation(projects.buildCachePackaging)
    testImplementation(projects.processServices)
    testImplementation(projects.softwareDiagnostics)
    testImplementation(projects.unitTestFixtures)
    testImplementation(testFixtures(projects.baseServices))
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.coreApi))
    testImplementation(testFixtures(projects.execution))
    testImplementation(testFixtures(projects.messaging))
    testImplementation(testFixtures(projects.resourcesHttp))
    testImplementation(testFixtures(projects.serialization))
    testImplementation(testFixtures(projects.snapshots))
    testImplementation(testFixtures(projects.toolingApi))
    testImplementation(testFixtures(projects.unitTestFixtures))
    testImplementation(testFixtures(projects.versionControl))
    testImplementation(libs.asmUtil)
    testImplementation(libs.commonsHttpclient)
    testImplementation(libs.groovyXml)
    testImplementation(libs.jsoup)

    testRuntimeOnly(projects.distributionsCore) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }

    integTestImplementation(projects.buildOption)
    integTestImplementation(projects.launcher) {
        because("Daemon fixtures need DaemonRegistry")
    }
    integTestImplementation(libs.jansi)
    integTestImplementation(libs.ansiControlSequenceUtil)
    integTestImplementation(libs.groovyJson)
    integTestImplementation(libs.socksProxy) {
        because("SOCKS proxy not part of internal-integ-testing api, since it has limited usefulness, so must be explicitly depended upon")
    }
    integTestImplementation(testFixtures(projects.core))
    integTestImplementation(testFixtures(projects.signing))
    integTestImplementation(testFixtures(projects.modelReflect))
    integTestImplementation(testFixtures(projects.unitTestFixtures))

    testFixturesApi(projects.baseServices) {
        because("Test fixtures export the Action class")
    }
    testFixturesApi(projects.persistentCache) {
        because("Test fixtures export the CacheAccess class")
    }

    testFixturesApi(libs.jetty)
    testFixturesApi(libs.testcontainersSpock) {
        because("API because of Groovy compiler bug leaking internals")
    }

    testFixturesImplementation(projects.core)
    testFixturesImplementation(projects.coreApi)
    testFixturesImplementation(projects.internalIntegTesting)
    testFixturesImplementation(projects.jvmServices) {
        because("Groovy compiler bug leaks internals")
    }
    testFixturesImplementation(projects.messaging)
    testFixturesImplementation(projects.unitTestFixtures)
    testFixturesImplementation(testFixtures(projects.core))
    testFixturesImplementation(testFixtures(projects.resourcesHttp))
    testFixturesImplementation(libs.bouncycastlePgp)
    testFixturesImplementation(libs.groovyJson)
    testFixturesImplementation(libs.guava) {
        because("Groovy compiler reflects on private field on TextUtil")
    }
    testFixturesImplementation(libs.inject)
    testFixturesImplementation(libs.jettyWebApp) {
        because("Groovy compiler bug leaks internals")
    }
    testFixturesImplementation(libs.slf4jApi)

    integTestDistributionRuntimeOnly(projects.distributionsJvm) {
        because("Need access to java platforms")
    }
    crossVersionTestDistributionRuntimeOnly(projects.distributionsCore)
    crossVersionTestImplementation(libs.jettyWebApp)
}

packageCycles {
    excludePatterns.add("org/gradle/**")
}

testFilesCleanup.reportOnly = true

tasks.clean {
    val testFiles = layout.buildDirectory.dir("tmp/teŝt files")
    doFirst {
        // On daemon crash, read-only cache tests can leave read-only files around.
        // clean now takes care of those files as well
        testFiles.get().asFileTree.matching {
            include("**/read-only-cache/**")
        }.visit { this.file.setWritable(true) }
    }
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
