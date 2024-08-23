plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.instrumented-java-project")
}

description = """Extends platform-base with base types and interfaces specific to the Java Virtual Machine, including tasks for obtaining a JDK via toolchains, and for compiling and launching Java applications."""

errorprone {
    disabledChecks.addAll(
        "StringCharset", // 1 occurrences
    )
}

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.serviceProvider)
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.fileCollections)
    api(projects.logging)
    api(projects.modelCore)
    api(projects.platformBase)

    api(libs.groovy)
    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.dependencyManagement)
    implementation(projects.execution)
    implementation(projects.functional)
    implementation(projects.jvmServices)
    implementation(projects.publish)
    implementation(projects.serviceLookup)

    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.commonsIo)

    testImplementation(projects.snapshots)
    testImplementation(libs.ant)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.diagnostics))
    testImplementation(testFixtures(projects.logging))
    testImplementation(testFixtures(projects.platformBase))
    testImplementation(testFixtures(projects.platformNative))

    integTestImplementation(projects.internalIntegTesting)

    integTestImplementation(libs.slf4jApi)

    testRuntimeOnly(projects.distributionsCore) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(projects.distributionsCore)
}

strictCompile {
    ignoreDeprecations() // most of this project has been deprecated
}

integTest.usesJavadocCodeSnippets = true
tasks.isolatedProjectsIntegTest {
    enabled = false
}
