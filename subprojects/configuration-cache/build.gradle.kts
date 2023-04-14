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

kotlin.sourceSets.all {
    languageSettings.progressiveMode = true
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.contracts.ExperimentalContracts",
        )
    }
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":base-services-groovy"))
    implementation(project(":composite-builds"))
    implementation(project(":core"))
    implementation(project(":core-api"))
    implementation(project(":dependency-management"))
    implementation(project(":enterprise-operations"))
    implementation(project(":execution"))
    implementation(project(":file-collections"))
    implementation(project(":file-temp"))
    implementation(project(":file-watching"))
    implementation(project(":functional"))
    implementation(project(":hashing"))
    implementation(project(":launcher"))
    implementation(project(":logging"))
    implementation(project(":messaging"))
    implementation(project(":model-core"))
    implementation(project(":native"))
    implementation(project(":persistent-cache"))
    implementation(project(":plugin-use"))
    implementation(project(":platform-jvm"))
    implementation(project(":process-services"))
    implementation(project(":publish"))
    implementation(project(":resources"))
    implementation(project(":resources-http"))
    implementation(project(":snapshots"))

    // TODO - move the isolatable serializer to model-core to live with the isolatable infrastructure
    implementation(project(":workers"))

    // TODO - it might be good to allow projects to contribute state to save and restore, rather than have this project know about everything
    implementation(project(":tooling-api"))
    implementation(project(":build-events"))
    implementation(project(":native"))
    implementation(project(":build-option"))

    implementation(libs.asm)
    implementation(libs.capsule)
    implementation(libs.fastutil)
    implementation(libs.groovy)
    implementation(libs.groovyJson)
    implementation(libs.guava)
    implementation(libs.inject)
    implementation(libs.slf4jApi)

    implementation(libs.futureKotlin("stdlib-jdk8"))
    implementation(libs.futureKotlin("reflect"))

    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.mockitoKotlin2)
    testImplementation(libs.kotlinCoroutinesDebug)

    integTestImplementation(project(":jvm-services"))
    integTestImplementation(project(":tooling-api"))
    integTestImplementation(project(":platform-jvm"))
    integTestImplementation(project(":test-kit"))
    integTestImplementation(project(":launcher"))
    integTestImplementation(project(":cli"))

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
