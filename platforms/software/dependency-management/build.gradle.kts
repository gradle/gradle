plugins {
    id("gradlebuild.distribution.implementation-java")
    id("gradlebuild.cross-version-tests")
}

description = """This project contains most of the dependency management logic of Gradle:
    |* the resolution engine,
    |* how to retrieve and process dependencies and their metadata,
    |* the dependency locking and verification implementations.
    |
    |DSL facing APIs are to be found in 'core-api'""".trimMargin()

dependencies {
    api(projects.baseServices)
    api(projects.buildOperations)
    api(projects.buildOption)
    api(projects.buildProcessServices)
    api(projects.classpath)
    api(projects.classloaders)
    api(projects.collections)
    api(projects.concurrent)
    api(projects.core)
    api(projects.coreApi)
    api(projects.credentialsApi)
    api(projects.credentials)
    api(projects.daemonServices)
    api(projects.domainObjectCollections)
    api(projects.enterpriseLogging)
    api(projects.enterpriseOperations)
    api(projects.execution)
    api(projects.fileCollections)
    api(projects.fileTemp)
    api(projects.files)
    api(projects.functional)
    api(projects.hashing)
    api(projects.logging)
    api(projects.loggingApi)
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
    api(projects.startParameter)
    api(projects.stdlibJavaExtensions)
    api(projects.versionedCache)
    api(projects.dependencyManagementResources)

    api(libs.bouncycastlePgp)
    api(libs.fastutil)
    api(libs.groovy)
    api(libs.guava)
    api(libs.inject)
    api(libs.ivy)
    api(libs.jspecify)
    api(libs.jsr305)
    api(libs.maven3Settings)
    api(libs.maven3SettingsBuilder)

    implementation(projects.buildDiscoveryImpl)
    implementation(projects.credentialsImpl)
    implementation(projects.fileOperations)
    implementation(projects.time)
    implementation(projects.baseAsm)
    implementation(projects.baseServicesGroovy)
    implementation(projects.resourcesHttp)
    implementation(projects.serviceRegistryBuilder)

    implementation(libs.asm)
    implementation(libs.asmCommons)
    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.gson)
    implementation(libs.httpcore)
    implementation(libs.plexusCipher)
    implementation(libs.plexusSecDispatcher)
    implementation(libs.plexusUtils)
    implementation(libs.slf4jApi)

    testImplementation(projects.buildCacheSpi)
    testImplementation(projects.serviceRegistryImpl)
    testImplementation(projects.unitTestFixtures)

    testImplementation(testLibs.asmUtil)
    testImplementation(libs.groovyXml)
    testImplementation(libs.jsoup)

    testImplementation(testFixtures(projects.serialization))
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.coreApi))
    testImplementation(testFixtures(projects.dependencyManagementResources))
    testImplementation(testFixtures(projects.logging))
    testImplementation(libs.ant)
    testImplementation(libs.asmTree)
    testImplementation(libs.commonsHttpclient)
    testImplementation(testLibs.hamcrest)

    testRuntimeOnly(projects.distributionsCore) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    testRuntimeOnly(projects.softwareDiagnostics)
    testRuntimeOnly(testFixtures(projects.toolingApi))

    integTestImplementation(projects.buildOperationsTrace)
    integTestImplementation(libs.groovyJson)
    integTestImplementation(testFixtures(projects.core))
    integTestImplementation(testFixtures(projects.signing))
    integTestImplementation(testFixtures(projects.modelReflect))

    testFixturesApi(projects.coreApi)
    testFixturesApi(projects.internalDistributionTesting)
    testFixturesApi(projects.internalIntegTesting)
    testFixturesApi(testFixtures(projects.core))
    testFixturesApi(testFixtures(projects.resourcesHttp))
    testFixturesApi(testLibs.testcontainers)

    testFixturesImplementation(projects.baseServices)
    testFixturesImplementation(libs.groovyJson)
    testFixturesImplementation(libs.guava) {
        because("Groovy compiler reflects on private field on TextUtil")
    }
    testImplementation(projects.jvmServices) {
        because("Groovy compiler bug leaks internals")
    }
    testImplementation(testFixtures(projects.hashing))
    testFixturesImplementation(testLibs.dockerJavaApi)

    integTestRuntimeOnly(projects.launcher) {
        because("Daemon fixtures need DaemonRegistry")
    }
    integTestImplementation(testFixtures(projects.resourcesHttp))
    integTestDistributionRuntimeOnly(projects.distributionsJvm) {
        because("Need access to java platforms")
    }

    crossVersionTestImplementation(projects.internalIntegTesting)

    crossVersionTestDistributionRuntimeOnly(projects.distributionsCore)

    // Javadoc-only: downstream modules whose types are referenced by {@link ...} in this module's docs.
    javadocReferences(projects.coreApi)

    runtimeOnly(libs.commonsHttpclient)
}

gradleModule {
    computedRuntimes {
        // Auto-generated by `:checkTargetRuntimes --fix`
        daemon = true
    }
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
