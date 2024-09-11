import gradlebuild.basics.isBundleGroovy4
import gradlebuild.basics.tasks.PackageListGenerator

plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "A library that aids in testing Gradle plugins and build logic in general"

errorprone {
    disabledChecks.addAll(
        "CatchAndPrintStackTrace", // 1 occurrences
        "ImmutableEnumChecker", // 1 occurrences
    )
}

dependencies {
    api(projects.baseServices)
    api(projects.stdlibJavaExtensions)
    api(projects.logging)
    api(projects.toolingApi)

    api(libs.jsr305)

    implementation(projects.core)
    implementation(projects.fileTemp)
    implementation(projects.io)
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
    integTestImplementation(libs.slf4jApi)
    integTestImplementation(libs.jetbrainsAnnotations)

    testRuntimeOnly(projects.distributionsCore) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(projects.distributionsBasics)
}

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

tasks {
    withType<Test>().configureEach {
        if (project.isBundleGroovy4) {
            exclude("org/gradle/testkit/runner/enduser/GradleRunnerSamplesEndUserIntegrationTest*") // cannot be parameterized for both Groovy 3 and 4
        }
    }
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
