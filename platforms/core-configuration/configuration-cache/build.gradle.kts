plugins {
    id("gradlebuild.distribution.implementation-kotlin")
    id("gradlebuild.kotlin-dsl-sam-with-receiver")
    id("gradlebuild.kotlin-experimental-contracts")
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
    from(zipTree(configurationCacheReportPath.elements.map { it.first().asFile })) {
        into("org/gradle/internal/cc/impl/problems")
        exclude("META-INF/**")
    }
}

// The integration tests in this project do not need to run in 'config cache' mode.
tasks.configCacheIntegTest {
    enabled = false
}

dependencies {
    api(project(":base-services"))
    api(project(":build-option"))
    api(projects.concurrent)
    api(projects.configurationCacheBase)
    api(projects.configurationProblemsBase)
    api(project(":core"))
    api(project(":core-api"))
    api(project(":dependency-management"))
    api(project(":file-temp"))
    api(projects.javaLanguageExtensions)
    api(project(":logging-api"))
    api(project(":messaging"))
    api(project(":model-core"))
    api(project(":native"))
    api(project(":plugin-use"))
    api(project(":resources"))
    api(projects.serviceProvider)
    api(project(":snapshots"))

    api(libs.groovy)
    api(libs.inject)
    api(libs.kotlinStdlib)

    // TODO - it might be good to allow projects to contribute state to save and restore, rather than have this project know about everything
    implementation(projects.beanSerializationServices)
    implementation(projects.buildEvents)
    implementation(projects.buildOperations)
    implementation(projects.coreKotlinExtensions)
    implementation(projects.coreSerializationCodecs)
    implementation(projects.dependencyManagementSerializationCodecs)
    implementation(projects.enterpriseOperations)
    implementation(projects.execution)
    implementation(projects.fileCollections)
    implementation(projects.fileWatching)
    implementation(projects.files)
    implementation(projects.flowServices)
    implementation(projects.functional)
    implementation(projects.graphSerialization)
    implementation(projects.guavaSerializationCodecs)
    implementation(projects.hashing)
    implementation(projects.inputTracking)
    implementation(projects.logging)
    implementation(projects.persistentCache)
    implementation(projects.problemsApi)
    implementation(projects.processServices)
    implementation(projects.serialization)
    implementation(projects.stdlibKotlinExtensions)
    implementation(projects.stdlibSerializationCodecs)
    implementation(projects.toolingApi)

    implementation(libs.fastutil)
    implementation(libs.groovyJson)
    implementation(libs.guava)
    implementation(libs.slf4jApi)

    runtimeOnly(project(":composite-builds"))
    runtimeOnly(project(":resources-http"))
    // TODO - move the isolatable serializer to model-core to live with the isolatable infrastructure
    runtimeOnly(project(":workers"))

    runtimeOnly(libs.kotlinReflect)

    testImplementation(projects.io)
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
    excludePatterns.add("org/gradle/internal/cc/**")
}
