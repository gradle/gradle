plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.update-init-template-versions")
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
        "StringCaseLocaleUsage", // 5 occurrences
        "UnusedMethod", // 1 occurrences
    )
}

dependencies {
    api(libs.inject)
    api(libs.jsr305)
    api(libs.maven3Settings)

    api(projects.javaLanguageExtensions)
    api(projects.serviceProvider)
    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":dependency-management"))
    api(project(":file-collections"))
    api(project(":logging"))
    api(project(":platform-jvm"))
    api(project(":toolchains-jvm-shared"))
    api(project(":workers"))
    api(project(":daemon-services"))

    implementation(project(":logging-api"))
    implementation(project(":platform-native"))
    implementation(project(":plugins-application")) {
        because("Needs access to StartScriptGenerator.")
    }
    implementation(project(":plugins-jvm-test-suite"))
    implementation(project(":wrapper-main"))
    implementation(project(":wrapper-shared"))

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

    compileOnly(project(":platform-base"))

    testRuntimeOnly(libs.maven3Compat)
    testRuntimeOnly(libs.maven3PluginApi)

    testImplementation(project(":cli"))
    testImplementation(project(":base-services-groovy"))
    testImplementation(project(":native"))
    testImplementation(project(":snapshots"))
    testImplementation(project(":process-services"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":platform-native")))

    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":platform-base"))
    testFixturesImplementation(project(":core-api"))
    testFixturesImplementation(project(":logging"))
    testFixturesImplementation(project(":plugins-java"))
    testFixturesImplementation(project(":testing-base"))
    testFixturesImplementation(project(":test-suites-base"))
    testFixturesImplementation(project(":plugins-jvm-test-suite"))

    integTestImplementation(project(":native"))
    integTestImplementation(libs.jetty)

    testRuntimeOnly(project(":distributions-jvm")) {
        because("ProjectBuilder tests load services from a Gradle distribution.  Toolchain usage requires JVM distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-full"))
}

packageCycles {
    excludePatterns.add("org/gradle/api/tasks/wrapper/internal/*")
}

integTest.testJvmXmx = "1g"

tasks.isolatedProjectsIntegTest {
    enabled = true
}
