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
    api(project(":base-services"))
    api(project(":java-language-extensions"))
    api(project(":logging"))
    api(project(":tooling-api"))

    api(libs.jsr305)

    implementation(project(":core"))
    implementation(project(":file-temp"))
    implementation(projects.io)
    implementation(project(":wrapper-shared"))
    implementation(project(":build-process-services"))

    implementation(libs.commonsIo)

    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(project(":launcher"))
    testFixturesImplementation(project(":tooling-api"))
    testFixturesImplementation(project(":wrapper-shared"))
    testFixturesImplementation(testFixtures(project(":core")))
    testFixturesImplementation(libs.guava)

    testImplementation(libs.guava)
    testImplementation(testFixtures(project(":core")))

    integTestImplementation(project(":native"))
    integTestImplementation(project(":logging"))
    integTestImplementation(project(":launcher"))
    integTestImplementation(project(":build-option"))
    integTestImplementation(project(":jvm-services"))
    integTestImplementation(libs.slf4jApi)
    integTestImplementation(libs.jetbrainsAnnotations)

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-basics"))
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

// Remove as part of fixing https://github.com/gradle/configuration-cache/issues/585
tasks.configCacheIntegTest {
    systemProperties["org.gradle.configuration-cache.internal.test-disable-load-after-store"] = "true"
}

tasks {
    withType<Test>().configureEach {
        if (project.isBundleGroovy4) {
            exclude("org/gradle/testkit/runner/enduser/GradleRunnerSamplesEndUserIntegrationTest*") // cannot be parameterized for both Groovy 3 and 4
        }
    }
}
