plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Plugins, tasks and domain objects for the legacy software-model based native (C/C++/Objective-C/Assembler/Windows-resources/CUnit/Google Test) plugins"

dependencies {
    api(projects.platformNative)
    api(projects.languageNative)
    api(projects.ideNative)

    api(projects.baseDiagnostics)
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.domainObjectCollections)
    api(projects.enterpriseLogging)
    api(projects.fileCollections)
    api(projects.modelCore)
    api(projects.platformBase)
    api(projects.reportRendering)
    api(projects.serviceLookup)
    api(projects.serviceProvider)
    api(projects.softwareDiagnostics)
    api(projects.workers)

    api(libs.jspecify)
    api(libs.inject)
    api(libs.guava)

    api(projects.logging)

    implementation(projects.daemonServerWorker)
    implementation(projects.modelReflect)
    implementation(projects.publish)
    implementation(projects.testingNative)

    implementation(libs.commonsLang)

    runtimeOnly(projects.dependencyManagement)

    testImplementation(projects.baseCompilerWorker)
    testImplementation(projects.stdlibJavaExtensions)
    testImplementation(projects.unitTestFixtures)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.coreApi))
    testImplementation(testFixtures(projects.logging))
    testImplementation(testFixtures(projects.modelCore))
    testImplementation(libs.commonsIo)

    testFixturesImplementation(testFixtures(projects.modelCore))

    testRuntimeOnly(projects.distributionsCore) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }

    integTestImplementation(projects.baseIdePlugins)
    integTestImplementation(projects.enterpriseOperations)
    integTestImplementation(projects.native)
    integTestImplementation(testFixtures(projects.baseDiagnostics))
    integTestImplementation(testFixtures(projects.ideNative))
    integTestImplementation(testFixtures(projects.languageNative))
    integTestImplementation(libs.groovyXml)
    integTestImplementation(libs.nativePlatform)
    integTestImplementation(testLibs.hamcrest)

    integTestDistributionRuntimeOnly(projects.distributionsFull) {
        because("ModelReportIntegrationTest verifies the full set of tasks contributed by a Gradle distribution.")
    }

    testFixturesApi(projects.coreApi)
    testFixturesApi(projects.modelCore)
    testFixturesApi(projects.platformBase)
}

gradleModule {
    requiredRuntimes {
        daemon = true
    }
    computedRuntimes {
        daemon = true
    }
}

strictCompile {
    // This project is going to be deleted in the next major.
    ignoreDeprecations()
}
