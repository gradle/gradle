plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.update-init-template-versions")
    id("gradlebuild.instrumented-java-project")
}

description = """This project contains the Build Init plugin, which is automatically applied to the root project of every build, and provides the init and wrapper tasks.

This project should NOT be used as an implementation dependency anywhere (except when building a Gradle distribution)."""

errorprone {
    disabledChecks.addAll(
        "DefaultCharset", // 6 occurrences
        "GetClassOnEnum", // 1 occurrences
        "HidingField", // 2 occurrences
        "ImmutableEnumChecker", // 2 occurrences
        "InconsistentCapitalization", // 1 occurrences
        "ReferenceEquality", // 1 occurrences
        "UnusedMethod", // 1 occurrences
    )
}

dependencies {
    api(libs.inject)
    api(libs.jsr305)
    api(libs.maven3Settings)

    api(projects.stdlibJavaExtensions)
    api(projects.serviceProvider)
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.dependencyManagement)
    api(projects.fileCollections)
    api(projects.logging)
    api(projects.platformJvm)
    api(projects.jvmServices)
    api(projects.workers)
    api(projects.daemonServices)

    implementation(projects.loggingApi)
    implementation(projects.platformNative)
    implementation(projects.pluginsApplication) {
        because("Needs access to StartScriptGenerator.")
    }
    implementation(projects.pluginsJvmTestSuite)
    implementation(projects.serviceLookup)
    implementation(projects.wrapperMain)
    implementation(projects.wrapperShared)

    implementation(libs.groovy)
    implementation(libs.groovyTemplates)
    implementation(libs.guava)
    implementation(libs.gson)
    implementation(libs.commonsLang)
    implementation(libs.maven3SettingsBuilder)
    implementation(libs.maven3Model)
    implementation(libs.slf4jApi)
    implementation(libs.plexusUtils)

    // We need to handle the Maven dependencies specially otherwise it breaks some cross version tests
    // TODO Figure out why and fix it - Move the two deps below to implementation and api and run ProjectTheExtensionCrossVersionSpec
    compileOnly(libs.eclipseSisuPlexus) {
        exclude(module = "cdi-api") // To respect the Maven exclusion
    }
    compileOnly(libs.maven3Compat)

    // 3 dependencies below are recommended as implementation but doing so adds them to the distribution
    // TODO Check why we reference them and if so, why they don't need to be in the distribution
    compileOnly(libs.maven3Artifact)
    compileOnly(libs.mavenResolverApi)
    compileOnly(libs.plexusClassworlds)

    compileOnly(libs.maven3Core)
    compileOnly(libs.maven3PluginApi)

    compileOnly(projects.platformBase)

    testRuntimeOnly(libs.maven3Compat)
    testRuntimeOnly(libs.maven3PluginApi)

    testImplementation(projects.cli)
    testImplementation(projects.baseServicesGroovy)
    testImplementation(projects.native)
    testImplementation(projects.snapshots)
    testImplementation(projects.processServices)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.platformNative))

    testFixturesImplementation(projects.baseServices)
    testFixturesImplementation(projects.platformBase)
    testFixturesImplementation(projects.coreApi)
    testFixturesImplementation(projects.logging)
    testFixturesImplementation(projects.pluginsJava)
    testFixturesImplementation(projects.testingBase)
    testFixturesImplementation(projects.testSuitesBase)
    testFixturesImplementation(projects.pluginsJvmTestSuite)

    integTestImplementation(projects.native)
    integTestImplementation(libs.jetty)

    integTestRuntimeOnly(libs.maven3Compat)

    testRuntimeOnly(projects.distributionsJvm) {
        because("ProjectBuilder tests load services from a Gradle distribution.  Toolchain usage requires JVM distribution.")
    }
    integTestDistributionRuntimeOnly(projects.distributionsFull)
}

packageCycles {
    excludePatterns.add("org/gradle/api/tasks/wrapper/internal/*")
}

integTest.testJvmXmx = "1g"
