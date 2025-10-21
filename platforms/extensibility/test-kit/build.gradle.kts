import gradlebuild.basics.tasks.PackageListGenerator

plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "A library that aids in testing Gradle plugins and build logic in general"

dependencies {
    api(projects.baseServices)
    api(projects.classloaders)
    api(projects.stdlibJavaExtensions)
    api(projects.toolingApi)

    api(libs.jspecify)

    implementation(projects.core)
    implementation(projects.fileTemp)
    api(libs.guava)
    implementation(projects.logging)
    implementation(projects.wrapperShared)
    implementation(projects.buildProcessServices)

    implementation(libs.commonsIo)

    testFixturesImplementation(projects.internalIntegTesting)
    testFixturesImplementation(projects.launcher)
    testFixturesImplementation(projects.toolingApi)
    testFixturesImplementation(projects.wrapperShared)
    testFixturesImplementation(testFixtures(projects.core))
    testFixturesImplementation(libs.guava)

    testImplementation(libs.guava)
    testImplementation(testFixtures(projects.core))

    integTestImplementation(projects.native)
    integTestImplementation(projects.logging)
    integTestImplementation(projects.launcher)
    integTestImplementation(projects.buildOption)
    integTestImplementation(projects.jvmServices)
    integTestImplementation(testFixtures(projects.buildConfiguration))
    integTestImplementation(testFixtures(projects.buildProcessServices))
    integTestImplementation(libs.slf4jApi)
    integTestImplementation(libs.jetbrainsAnnotations)

    testRuntimeOnly(projects.distributionsCore) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(projects.distributionsBasics)
}

// Test kit should not be part of the public API
// TODO Find a way to not register this and the task instead
configurations.remove(configurations.apiStubElements.get())

val generateTestKitPackageList by tasks.registering(PackageListGenerator::class) {
    classpath.from(sourceSets.main.map { it.runtimeClasspath })
    outputFile = layout.buildDirectory.file("runtime-api-info/test-kit-relocated.txt")
}
tasks.jar {
    into("org/gradle/api/internal/runtimeshaded") {
        from(generateTestKitPackageList)
    }
}

packageCycles {
    excludePatterns.add("org/gradle/testkit/runner/internal/**")
}

tasks.integMultiVersionTest {
    systemProperty("org.gradle.integtest.testkit.compatibility", "all")
}

tasks.isolatedProjectsIntegTest {
    enabled = false
}
