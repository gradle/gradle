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
        "EmptyBlockTag", // 2 occurrences
        "Finally", // 4 occurrences
        "HidingField", // 1 occurrences
        "IdentityHashMapUsage", // 2 occurrences
        "ImmutableEnumChecker", // 2 occurrences
        "InconsistentCapitalization", // 2 occurrences
        "InlineFormatString", // 5 occurrences
        "InlineMeSuggester", // 2 occurrences
        "InvalidParam", // 1 occurrences
        "LoopOverCharArray", // 1 occurrences
        "MathAbsoluteNegative",
        "MissingCasesInEnumSwitch", // 7 occurrences
        "MixedMutabilityReturnType", // 5 occurrences
        "ModifiedButNotUsed", // 1 occurrences
        "MutablePublicArray", // 1 occurrences
        "NonApiType", // 3 occurrences
        "NonCanonicalType", // 3 occurrences
        "ObjectEqualsForPrimitives", // 3 occurrences
        "OperatorPrecedence", // 2 occurrences
        "ReferenceEquality", // 10 occurrences
        "SameNameButDifferent", // 4 occurrences
        "StreamResourceLeak", // 1 occurrences
        "StringCharset", // 1 occurrences
        "TypeParameterShadowing", // 4 occurrences
        "TypeParameterUnusedInFormals", // 2 occurrences
        "UndefinedEquals", // 1 occurrences
        "UnusedMethod", // 34 occurrences
        "UnusedTypeParameter", // 1 occurrences
        "UnusedVariable", // 6 occurrences
    )
}


dependencies {
    api(projects.concurrent)
    api(projects.stdlibJavaExtensions)
    api(projects.serialization)
    api(projects.serviceLookup)
    api(projects.serviceProvider)
    api(projects.baseServices)
    api(projects.buildOperations)
    api(projects.buildOption)
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
    api(projects.persistentCache)
    api(projects.problemsApi)
    api(projects.resources)
    api(projects.security)
    api(projects.snapshots)
    api(projects.buildProcessServices)

    api(libs.bouncycastlePgp)
    api(libs.groovy)
    api(libs.guava)
    api(libs.inject)
    api(libs.ivy)
    api(libs.jsr305)
    api(libs.maven3Settings)
    api(libs.maven3SettingsBuilder)
    api(libs.slf4jApi)

    implementation(projects.time)
    implementation(projects.baseAsm)
    implementation(projects.baseServicesGroovy)
    implementation(projects.loggingApi)
    implementation(projects.resourcesHttp)
    implementation(projects.serviceRegistryBuilder)

    implementation(libs.ant)
    implementation(libs.asm)
    implementation(libs.asmCommons)
    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.fastutil)
    implementation(libs.gson)
    implementation(libs.httpcore)

    testImplementation(projects.buildCachePackaging)
    testImplementation(projects.diagnostics)
    testImplementation(projects.processServices)
    testImplementation(libs.asmUtil)
    testImplementation(libs.commonsHttpclient)
    testImplementation(libs.groovyXml)
    testImplementation(libs.jsoup)
    testImplementation(testFixtures(projects.serialization))
    testImplementation(testFixtures(projects.baseServices))
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.coreApi))
    testImplementation(testFixtures(projects.execution))
    testImplementation(testFixtures(projects.messaging))
    testImplementation(testFixtures(projects.resourcesHttp))
    testImplementation(testFixtures(projects.snapshots))
    testImplementation(testFixtures(projects.versionControl))

    integTestImplementation(projects.buildOption)
    integTestImplementation(libs.jansi)
    integTestImplementation(libs.ansiControlSequenceUtil)
    integTestImplementation(libs.groovyJson)
    integTestImplementation(libs.socksProxy) {
        because("SOCKS proxy not part of internal-integ-testing api, since it has limited usefulness, so must be explicitly depended upon")
    }
    integTestImplementation(testFixtures(projects.security))
    integTestImplementation(testFixtures(projects.modelCore))

    testFixturesApi(projects.baseServices) {
        because("Test fixtures export the Action class")
    }
    testFixturesApi(projects.persistentCache) {
        because("Test fixtures export the CacheAccess class")
    }

    testFixturesApi(libs.jetty)
    testFixturesImplementation(projects.core)
    testFixturesImplementation(testFixtures(projects.core))
    testFixturesImplementation(testFixtures(projects.resourcesHttp))
    testFixturesImplementation(projects.coreApi)
    testFixturesImplementation(projects.messaging)
    testFixturesImplementation(projects.internalIntegTesting)
    testFixturesImplementation(libs.slf4jApi)
    testFixturesImplementation(libs.inject)
    testFixturesImplementation(libs.groovyJson)
    testFixturesImplementation(libs.guava) {
        because("Groovy compiler reflects on private field on TextUtil")
    }
    testFixturesImplementation(libs.bouncycastlePgp)
    testFixturesApi(libs.testcontainersSpock) {
        because("API because of Groovy compiler bug leaking internals")
    }
    testFixturesImplementation(projects.jvmServices) {
        because("Groovy compiler bug leaks internals")
    }
    testFixturesImplementation(libs.jettyWebApp) {
        because("Groovy compiler bug leaks internals")
    }

    testRuntimeOnly(projects.distributionsCore) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestImplementation(projects.launcher) {
        because("Daemon fixtures need DaemonRegistry")
    }
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
