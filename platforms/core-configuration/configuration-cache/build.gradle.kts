plugins {
    id("gradlebuild.distribution.implementation-kotlin")
    id("gradlebuild.kotlin-dsl-sam-with-receiver")
}

description = "Configuration cache implementation"

val configurationCacheReportPath by configurations.creating {
    isVisible = false
    isCanBeConsumed = false
    attributes { attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("configuration-cache-report")) }
}

// You can have a faster feedback loop by running `configuration-cache-report` as an included build
// See https://github.com/gradle/configuration-cache-report#development-with-gradlegradle-and-composite-build
dependencies {
    configurationCacheReportPath(libs.configurationCacheReport)
}

tasks.processResources {
    from(zipTree(provider { configurationCacheReportPath.files.first() })) {
        into("org/gradle/configurationcache/problems")
        exclude("META-INF/**")
    }
}

// The integration tests in this project do not need to run in 'config cache' mode.
tasks.configCacheIntegTest {
    enabled = false
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.contracts.ExperimentalContracts",
        )
    }
}

dependencies {
    api(project(":base-annotations"))
    api(project(":base-services"))
    // TODO - it might be good to allow projects to contribute state to save and restore, rather than have this project know about everything
    api(project(":build-option"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":dependency-management"))
    api(project(":enterprise-operations"))
    api(project(":file-collections"))
    api(project(":file-temp"))
    api(project(":files"))
    api(project(":functional"))
    api(project(":hashing"))
    api(project(":logging"))
    api(project(":logging-api"))
    api(project(":messaging"))
    api(project(":model-core"))
    // TODO - it might be good to allow projects to contribute state to save and restore, rather than have this project know about everything
    api(project(":native"))
    api(project(":persistent-cache"))
    api(project(":plugin-use"))
    api(project(":problems-api"))
    api(project(":resources"))
    api(project(":snapshots"))

    api(libs.groovy)
    api(libs.guava)
    api(libs.inject)
    api(libs.futureKotlin("stdlib"))

    implementation(project(":base-services-groovy"))
    // TODO - it might be good to allow projects to contribute state to save and restore, rather than have this project know about everything
    implementation(project(":build-events"))
    implementation(project(":build-operations"))
    implementation(project(":execution"))
    implementation(project(":file-watching"))
    implementation(project(":input-tracking"))
    implementation(project(":platform-jvm"))
    implementation(project(":process-services"))
    implementation(project(":publish"))
    // TODO - it might be good to allow projects to contribute state to save and restore, rather than have this project know about everything
    implementation(project(":tooling-api"))

    implementation(libs.asm)
    implementation(libs.capsule)
    implementation(libs.fastutil)
    implementation(libs.groovyJson)
    implementation(libs.jsr305)
    implementation(libs.slf4jApi)

    runtimeOnly(project(":composite-builds"))
    runtimeOnly(project(":resources-http"))
    // TODO - move the isolatable serializer to model-core to live with the isolatable infrastructure
    runtimeOnly(project(":workers"))

    runtimeOnly(libs.futureKotlin("reflect"))

    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.mockitoKotlin2)
    testImplementation(libs.kotlinCoroutinesDebug)

    integTestImplementation(project(":jvm-services"))
    integTestImplementation(project(":tooling-api"))
    integTestImplementation(project(":platform-jvm"))
    integTestImplementation(project(":test-kit"))
    integTestImplementation(project(":launcher"))
    integTestImplementation(project(":cli"))
    integTestImplementation(project(":workers"))

    integTestImplementation(libs.guava)
    integTestImplementation(libs.ant)
    integTestImplementation(libs.inject)
    integTestImplementation("com.microsoft.playwright:playwright:1.20.1")

    integTestImplementation(testFixtures(project(":tooling-api")))
    integTestImplementation(testFixtures(project(":dependency-management")))
    integTestImplementation(testFixtures(project(":jacoco")))
    integTestImplementation(testFixtures(project(":model-core")))

    crossVersionTestImplementation(project(":cli"))

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-full")) {
        because("Includes tests for builds with the enterprise plugin and TestKit involved; ConfigurationCacheJacocoIntegrationTest requires JVM distribution")
    }
    crossVersionTestDistributionRuntimeOnly(project(":distributions-core"))
}

packageCycles {
    excludePatterns.add("org/gradle/configurationcache/**")
}
