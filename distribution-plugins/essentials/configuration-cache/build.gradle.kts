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

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:base-services-groovy")
    implementation("org.gradle:build-events")
    implementation("org.gradle:build-option")
    implementation("org.gradle:core")
    implementation("org.gradle:core-api")
    implementation("org.gradle:execution")
    implementation("org.gradle:file-collections")
    implementation("org.gradle:file-watching")
    implementation("org.gradle:launcher")
    implementation("org.gradle:logging")
    implementation("org.gradle:messaging")
    implementation("org.gradle:model-core")
    implementation("org.gradle:native")
    implementation("org.gradle:persistent-cache")
    implementation("org.gradle:resources")
    implementation("org.gradle:snapshots")

    implementation(project(":dependency-management"))
    implementation(project(":composite-builds"))
    implementation(project(":plugins"))
    implementation(project(":plugin-use"))
    implementation(project(":publish"))

    // TODO - move the isolatable serializer to model-core to live with the isolatable infrastructure
    implementation(project(":workers"))

    // TODO - it might be good to allow projects to contribute state to save and restore, rather than have this project know about everything
    implementation("org.gradle:tooling-api")

    implementation(libs.groovy)
    implementation(libs.groovyJson)
    implementation(libs.slf4jApi)
    implementation(libs.guava)

    implementation(libs.futureKotlin("stdlib-jdk8"))
    implementation(libs.futureKotlin("reflect"))

    testImplementation(testFixtures("org.gradle:core"))
    testImplementation(libs.mockitoKotlin2)
    testImplementation(libs.kotlinCoroutinesDebug)

    integTestImplementation("org.gradle:jvm-services")
    integTestImplementation("org.gradle:tooling-api")
    integTestImplementation("org.gradle:launcher")
    integTestImplementation(project(":platform-jvm"))
    integTestImplementation("org.gradle:test-kit")

    integTestImplementation(libs.guava)
    integTestImplementation(libs.ant)
    integTestImplementation(libs.inject)

    integTestImplementation(testFixtures(project(":model-core")))
    integTestImplementation(testFixtures(project(":dependency-management")))
    integTestImplementation(testFixtures("org.gradle:jacoco"))

    crossVersionTestImplementation("org.gradle:cli")

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly("org.gradle:distributions-jvm") {
        because("Includes tests for builds with TestKit involved; ConfigurationCacheJacocoIntegrationTest requires JVM distribution")
    }
    crossVersionTestDistributionRuntimeOnly(project(":distributions-core"))
}

classycle {
    excludePatterns.add("org/gradle/configurationcache/**")
}
