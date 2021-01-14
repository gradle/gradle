plugins {
    id("gradlebuild.distribution.implementation-kotlin")
    id("gradlebuild.kotlin-dsl-sam-with-receiver")
}

val configurationCacheReportPath by configurations.creating {
    isVisible = false
    isCanBeConsumed = false
    attributes { attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("configuration-cache-report")) }
}

dependencies {
    configurationCacheReportPath(project(":configuration-cache-report"))
}

tasks.processResources {
    from(configurationCacheReportPath) { into("org/gradle/configurationcache") }
}

// The integration tests in this project do not need to run in 'config cache' mode.
tasks.configCacheIntegTest {
    enabled = false
}

// This is a workaround for the validate plugins task trying to inspect classes which have changed but are NOT tasks.
// For the current project, we simply disable it since there are no tasks in there.
tasks.withType<ValidatePlugins>().configureEach {
    enabled = false
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":base-services-groovy"))
    implementation(project(":composite-builds"))
    implementation(project(":core"))
    implementation(project(":core-api"))
    implementation(project(":dependency-management"))
    implementation(project(":execution"))
    implementation(project(":file-collections"))
    implementation(project(":file-watching"))
    implementation(project(":launcher"))
    implementation(project(":logging"))
    implementation(project(":messaging"))
    implementation(project(":model-core"))
    implementation(project(":native"))
    implementation(project(":persistent-cache"))
    implementation(project(":plugin-use"))
    implementation(project(":plugins"))
    implementation(project(":publish"))
    implementation(project(":resources"))
    implementation(project(":snapshots"))

    // TODO - move the isolatable serializer to model-core to live with the isolatable infrastructure
    implementation(project(":workers"))

    // TODO - it might be good to allow projects to contribute state to save and restore, rather than have this project know about everything
    implementation(project(":tooling-api"))
    implementation(project(":build-events"))
    implementation(project(":native"))
    implementation(project(":build-option"))

    implementation(libs.groovy)
    implementation(libs.slf4jApi)
    implementation(libs.guava)

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

    integTestImplementation(libs.guava)
    integTestImplementation(libs.ant)
    integTestImplementation(libs.inject)

    integTestImplementation(testFixtures(project(":dependency-management")))
    integTestImplementation(testFixtures(project(":jacoco")))

    crossVersionTestImplementation(project(":cli"))

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm")) {
        because("Includes tests for builds with TestKit involved; ConfigurationCacheJacocoIntegrationTest requires JVM distribution")
    }
    crossVersionTestDistributionRuntimeOnly(project(":distributions-core"))
}

classycle {
    excludePatterns.add("org/gradle/configurationcache/**")
}
