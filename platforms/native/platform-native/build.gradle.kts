plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Plugins, tasks and compiler infrastructure for compiling/linking code"

dependencies {
    api(projects.baseCompilerWorker)
    api(projects.baseDiagnostics)
    api(projects.baseServices)
    api(projects.buildOperations)
    api(projects.core)
    api(projects.coreApi)
    api(projects.enterpriseLogging)
    api(projects.fileCollections)
    api(projects.files)
    api(projects.hashing)
    api(projects.logging)
    api(projects.modelCore)
    api(projects.native)
    api(projects.platformBase)
    api(projects.reportRendering)
    api(projects.serviceLookup)
    api(projects.serviceProvider)
    api(projects.softwareDiagnostics)
    api(projects.stdlibJavaExtensions)
    api(projects.workers)

    api(libs.guava)
    api(libs.jspecify)
    api(libs.inject)
    api(libs.nativePlatform)
    api(libs.slf4jApi)

    implementation(projects.daemonServerWorker)
    implementation(projects.dependencyManagement)
    implementation(projects.modelReflect)
    implementation(projects.io)
    implementation(projects.loggingApi)


    implementation(libs.commonsLang)
    implementation(libs.commonsIo)
    implementation(libs.gson)
    implementation(libs.snakeyaml)

    testFixturesApi(projects.resources)
    testFixturesApi(testFixtures(projects.ide))
    testFixturesImplementation(testFixtures(projects.core))
    testFixturesImplementation(testFixtures(projects.modelCore))
    testFixturesImplementation(testFixtures(projects.testingBase))
    testFixturesImplementation(projects.internalIntegTesting)
    testFixturesImplementation(projects.native)
    testFixturesImplementation(projects.platformBase)
    testFixturesImplementation(projects.fileCollections)
    testFixturesImplementation(projects.processServices)
    testFixturesImplementation(projects.snapshots)
    testFixturesImplementation(libs.guava)
    testFixturesImplementation(libs.nativePlatform)
    testFixturesImplementation(libs.groovyXml)
    testFixturesImplementation(libs.commonsLang)
    testFixturesImplementation(libs.commonsIo)

    testImplementation(testFixtures(projects.baseServices))
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.coreApi))
    testImplementation(testFixtures(projects.enterpriseLogging))
    testImplementation(testFixtures(projects.messaging))
    testImplementation(testFixtures(projects.modelCore))
    testImplementation(testFixtures(projects.platformBase))
    testImplementation(testFixtures(projects.snapshots))
    testImplementation(testFixtures(projects.time))

    testRuntimeOnly(projects.distributionsCore) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(projects.distributionsNative) {
        because("Required 'ideNative' to test visual studio project file generation for generated sources")
    }
}

packageCycles {
    excludePatterns.add("org/gradle/nativeplatform/plugins/**")
    excludePatterns.add("org/gradle/nativeplatform/tasks/**")
    excludePatterns.add("org/gradle/nativeplatform/internal/resolve/**")
    excludePatterns.add("org/gradle/nativeplatform/toolchain/internal/**")
    // platform-base allowed all kinds of cycles
    excludePatterns.add("org/gradle/**")
}

tasks.isolatedProjectsIntegTest {
    enabled = false
}
