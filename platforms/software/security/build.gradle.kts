plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.instrumented-java-project")
}

description = "Shared classes for projects requiring GPG support"

errorprone {
    disabledChecks.addAll(
        "DefaultCharset", // 1 occurrences
    )
}

dependencies {
    api(projects.coreApi)
    api(projects.resources)

    api(libs.bouncycastlePgp)
    api(libs.jsr305)

    implementation(projects.stdlibJavaExtensions)
    implementation(projects.time)
    implementation(projects.baseServices)
    implementation(projects.functional)
    implementation(projects.loggingApi)
    implementation(projects.processServices)

    implementation(libs.bouncycastleProvider)
    implementation(libs.guava)
    implementation(libs.inject)

    testImplementation(testFixtures(projects.core))

    testFixturesImplementation(projects.baseServices)
    testFixturesImplementation(libs.slf4jApi)
    testFixturesImplementation(libs.jetty)
    testFixturesImplementation(libs.jettyWebApp)
    testFixturesImplementation(testFixtures(projects.core))
    testFixturesImplementation(projects.internalIntegTesting)

    testRuntimeOnly(projects.distributionsCore) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
}

packageCycles {
    excludePatterns.add("org/gradle/plugins/signing/type/pgp/**")
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
