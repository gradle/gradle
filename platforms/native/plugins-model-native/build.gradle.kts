plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Plugins, tasks and domain objects for the legacy software-model based native (C/C++/Objective-C/Assembler/Windows-resources/CUnit/Google Test) plugins"

dependencies {
    api(projects.platformNative)
    api(projects.languageNative)
    api(projects.testingNative)

    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.modelCore)
    api(projects.platformBase)
    api(projects.softwareDiagnostics)
    api(projects.stdlibJavaExtensions)

    api(libs.jspecify)
    api(libs.inject)

    implementation(projects.dependencyManagement)
    implementation(projects.fileCollections)
    implementation(projects.logging)
    implementation(projects.serviceLookup)
    implementation(projects.serviceProvider)

    implementation(libs.guava)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.modelCore))
    testImplementation(testFixtures(projects.platformBase))
    testImplementation(testFixtures(projects.platformNative))

    testRuntimeOnly(projects.distributionsCore) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(projects.distributionsNative)
}

tasks.isolatedProjectsIntegTest {
    enabled = false
}
