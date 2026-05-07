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
    api(projects.enterpriseLogging)
    api(projects.fileCollections)
    api(projects.modelCore)
    api(projects.platformBase)
    api(projects.reportRendering)
    api(projects.serviceLookup)
    api(projects.serviceProvider)
    api(projects.softwareDiagnostics)
    api(projects.stdlibJavaExtensions)
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

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.coreApi))
    testImplementation(testFixtures(projects.modelCore))
    testImplementation(testFixtures(projects.platformBase))
    testImplementation(testFixtures(projects.platformNative))

    testFixturesImplementation(projects.modelCore)
    testFixturesImplementation(testFixtures(projects.modelCore))
    testFixturesImplementation(testFixtures(projects.core))
    testFixturesImplementation(testFixtures(projects.coreApi))

    testRuntimeOnly(projects.distributionsCore) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }

    integTestImplementation(testFixtures(projects.ideNative))
    integTestImplementation(testFixtures(projects.languageNative))
    integTestImplementation(testFixtures(projects.platformNative))

    integTestDistributionRuntimeOnly(projects.distributionsFull) {
        because("ModelReportIntegrationTest verifies the full set of tasks contributed by a Gradle distribution.")
    }
}

gradleModule {
    requiredRuntimes {
        daemon = true
    }
    computedRuntimes {
        daemon = true
    }
}

packageCycles {
    excludePatterns.add("org/gradle/language/nativeplatform/internal/**")
    excludePatterns.add("org/gradle/nativeplatform/internal/**")
}

tasks.isolatedProjectsIntegTest {
    enabled = false
}
