plugins {
    id("gradlebuild.distribution.api-java")
}

description = """Contains some base and shared classes for JVM language support, like AbstractCompile class and BaseForkOptions classes,
JVM-specific dependencies blocks and JVM test suite interfaces."""

errorprone {
    disabledChecks.addAll(
        "OverridesJavaxInjectableMethod", // 1 occurrences
    )
}

dependencies {
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.daemonServerWorker)
    api(projects.files)
    api(projects.platformBase)
    api(projects.platformJvm)
    api(projects.serviceProvider)
    api(projects.stdlibJavaExtensions)
    api(projects.workers)

    api(libs.groovy)
    api(libs.inject)
    api(libs.jspecify)

    implementation(projects.classloaders)
    implementation(projects.dependencyManagement)
    implementation(projects.logging)
    implementation(projects.testSuitesBase)

    implementation(libs.commonsLang3)
    implementation(libs.guava)

    testImplementation(projects.native)
    testImplementation(projects.resources)
    testImplementation(projects.snapshots)
    testImplementation(testFixtures(projects.core))

    integTestImplementation(testFixtures(projects.modelReflect))
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
