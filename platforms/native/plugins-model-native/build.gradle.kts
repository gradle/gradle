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
    api(projects.fileCollections)
    api(projects.files)
    api(projects.modelCore)
    api(projects.platformBase)
    api(projects.serviceLookup)
    api(projects.serviceProvider)
    api(projects.softwareDiagnostics)
    api(projects.stdlibJavaExtensions)
    api(projects.testSuitesBase)
    api(projects.workers)

    api(libs.jspecify)
    api(libs.inject)

    implementation(projects.baseCompilerWorker)
    implementation(projects.daemonServerWorker)
    implementation(projects.enterpriseLogging)
    implementation(projects.logging)
    implementation(projects.modelReflect)
    implementation(projects.testingNative)

    implementation(libs.commonsLang)
    implementation(libs.guava)

    runtimeOnly(projects.dependencyManagement)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.coreApi))
    testImplementation(testFixtures(projects.modelCore))
    testImplementation(testFixtures(projects.platformBase))
    testImplementation(testFixtures(projects.platformNative))

    testRuntimeOnly(projects.distributionsCore) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(projects.distributionsNative)
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
