plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.instrumented-java-project")
}

description = """Contains some base and shared classes for JVM language support, like AbstractCompile class and BaseForkOptions classes,
JVM-specific dependencies blocks and JVM test suite interfaces."""

errorprone {
    disabledChecks.addAll(
        "OverridesJavaxInjectableMethod", // 1 occurrences
    )
}

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.serviceProvider)
    api(projects.baseServices)
    api(projects.classloading)
    api(projects.core)
    api(projects.coreApi)
    api(projects.files)
    api(projects.platformBase)
    api(projects.platformJvm)
    api(projects.processServices)
    api(projects.workers)

    api(libs.groovy)
    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.dependencyManagement)
    implementation(projects.logging)
    implementation(projects.modelCore)
    implementation(projects.testSuitesBase)

    implementation(libs.guava)

    testImplementation(projects.native)
    testImplementation(projects.resources)
    testImplementation(projects.snapshots)
    testImplementation(testFixtures(projects.core))

    integTestImplementation(testFixtures(projects.modelCore))
    integTestImplementation(testFixtures(projects.resourcesHttp))

    testFixturesImplementation(libs.commonsLang)
    testFixturesImplementation(libs.guava)
    testFixturesImplementation(projects.internalIntegTesting)
    testFixturesImplementation(testFixtures(projects.core))

    testRuntimeOnly(projects.distributionsCore) {
        because("AbstractOptionsTest instantiates DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(projects.distributionsJvm)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
