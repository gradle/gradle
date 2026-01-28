plugins {
    id("gradlebuild.internal.java")
}

description = "Collection of test fixtures for integration tests, internal use only"

jvmCompile {
    compilations {
        named("main") {
            // These test fixtures used by many test fixtures, many of which still require JVM 8
            targetJvmVersion = 8
        }
    }
}

sourceSets {
    main {
        // Incremental Groovy joint-compilation doesn't work with the Error Prone annotation processor
        errorprone.enabled = false
    }
}

dependencies {
    api(projects.baseServices) {
        because("Part of the public API, used by spock AST transformer")
    }
    api(projects.buildCacheBase)
    api(projects.buildOperations)
    api(projects.buildOperationsTrace)
    api(projects.concurrent)
    api(projects.coreApi)
    api(projects.hashing)
    api(projects.internalTesting) {
        because("Part of the public API")
    }
    api(projects.stdlibJavaExtensions)
    api(projects.jvmServices) {
        because("Exposing jvm metadata via AvailableJavaHomes")
    }
    api(projects.internalDistributionTesting)
    api(projects.logging)
    api(projects.loggingApi)
    api(projects.native)
    api(projects.problemsApi)
    api(projects.processServices)
    api(projects.serviceLookup)

    api(libs.groovy)
    api(libs.groovyXml)
    api(libs.hamcrest)
    api(libs.jettyWebApp) {
        because("Part of the public API via HttpServer")
    }
    api(libs.jansi)
    api(libs.jettyServer)
    api(libs.jettyUtil)
    api(libs.jgit) {
        because("Some tests require a git reportitory - see AbstractIntegrationSpec.initGitDir(")
    }
    api(libs.jspecify)
    api(libs.jsr305)
    api(libs.junit) {
        because("Part of the public API, used by spock AST transformer")
    }
    api(libs.mavenResolverApi) {
        because("For ApiMavenResolver. API we interact with to resolve Maven graphs & artifacts")
    }
    api(libs.samplesCheck) {
        exclude(module = "groovy-all")
    }
    api(libs.samplesDiscovery)
    api(libs.servletApi)
    api(libs.slf4jApi)
    api(libs.spock) {
        because("Part of the public API")
    }

    implementation(projects.baseServicesGroovy)
    implementation(projects.buildCache)
    implementation(projects.buildDiscovery)
    implementation(projects.buildDiscoveryImpl)
    implementation(projects.buildEvents)
    implementation(projects.buildOption)
    implementation(projects.buildProcessServices)
    implementation(projects.buildState)
    implementation(projects.classloaders)
    implementation(projects.cli)
    implementation(projects.clientServices)
    implementation(projects.core)
    implementation(projects.daemonProtocol)
    implementation(projects.daemonServices)
    implementation(projects.dependencyManagement)
    implementation(projects.enterpriseOperations)
    implementation(projects.fileCollections)
    implementation(projects.fileTemp)
    implementation(projects.gradleCli)
    implementation(projects.instrumentationAgentServices)
    implementation(projects.io)
    implementation(projects.launcher)
    implementation(projects.messaging)
    implementation(projects.modelCore)
    implementation(projects.modelReflect)
    implementation(projects.persistentCache)
    implementation(projects.scopedPersistentCache)
    implementation(projects.serialization)
    implementation(projects.serviceProvider)
    implementation(projects.serviceRegistryBuilder)
    implementation(projects.time)

    implementation(testFixtures(projects.buildProcessServices))
    implementation(testFixtures(projects.core))

    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.groovyJson)
    implementation(libs.guava)
    implementation(libs.inject)
    implementation(libs.jettyServlet)
    implementation(libs.littleproxy)
    implementation(libs.mavenResolverSupplier) {
        because("For ApiMavenResolver. Wires together implementation for maven-resolver-api")
    }
    implementation(libs.maven3ResolverProvider) {
        because("For ApiMavenResolver. Provides MavenRepositorySystemUtils")
    }
    implementation(libs.netty)
    implementation(libs.opentest4j)
    implementation(libs.socksProxy)
    // we depend on both: sshd platforms and libraries
    implementation(libs.sshdCore)
    implementation(platform(libs.sshdCore))
    implementation(libs.sshdScp)
    implementation(platform(libs.sshdScp))
    implementation(libs.sshdSftp)
    implementation(platform(libs.sshdSftp))

    compileOnly(libs.kotlinStdlib) {
        because("""Fixes:
            compiler message file broken: key=compiler.misc.msg.bug arguments=11.0.21, {1}, {2}, {3}, {4}, {5}, {6}, {7}
            java.lang.AssertionError: typeSig ERROR""")
    }

    runtimeOnly(libs.mavenResolverImpl) {
        because("For ApiMavenResolver. Implements maven-resolver-api")
    }
    runtimeOnly(libs.mavenResolverConnectorBasic) {
        because("For ApiMavenResolver. To use resolver transporters")
    }
    runtimeOnly(libs.mavenResolverTransportFile) {
        because("For ApiMavenResolver. To resolve file:// URLs")
    }
    runtimeOnly(libs.mavenResolverTransportHttp) {
        because("For ApiMavenResolver. To resolve http:// URLs")
    }

    testRuntimeOnly(projects.distributionsCore) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }

    integTestDistributionRuntimeOnly(projects.distributionsCore)
}

packageCycles {
    excludePatterns.add("org/gradle/**")
}
