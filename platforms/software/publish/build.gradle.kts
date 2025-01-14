plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Base plugin for the maven and ivy publish plugins. Defines the publishing extension."

errorprone {
    disabledChecks.addAll(
        "InlineMeSuggester", // 7 occurrences
        "MixedMutabilityReturnType", // 5 occurrences
    )
}

dependencies {
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.dependencyManagement)
    api(projects.fileCollections)
    api(projects.hashing)
    api(projects.logging)
    api(projects.loggingApi)
    api(projects.modelCore)
    api(projects.serviceProvider)
    api(projects.stdlibJavaExtensions)

    api(libs.groovy)
    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.serviceLookup)
    implementation(projects.baseServicesGroovy) {
        because("Required for Specs")
    }
    implementation(projects.functional)

    implementation(libs.commonsLang)
    implementation(libs.gson)
    implementation(libs.guava)

    testImplementation(testFixtures(projects.core))

    testRuntimeOnly(projects.distributionsCore) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(projects.distributionsCore)
}

integTest.usesJavadocCodeSnippets = true
tasks.isolatedProjectsIntegTest {
    enabled = false
}
