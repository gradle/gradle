plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Infrastructure for starting and managing worker processes"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":messaging"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":worker-processes"))
    implementation(project(":persistent-cache"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":snapshots"))
    implementation(project(":file-collections"))
    implementation(project(":files"))
    implementation(project(":native"))
    implementation(project(":resources"))

    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.inject)

    testImplementation(project(":native"))
    testImplementation(project(":file-collections"))
    testImplementation(project(":resources"))
    testImplementation(project(":snapshots"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":logging")))

    integTestRuntimeOnly(project(":kotlin-dsl"))
    integTestRuntimeOnly(project(":kotlin-dsl-provider-plugins"))
    integTestRuntimeOnly(project(":api-metadata"))
    integTestRuntimeOnly(project(":test-kit"))

    integTestImplementation(project(":jvm-services"))
    integTestImplementation(project(":enterprise-operations"))

    testFixturesImplementation(libs.inject)
    testFixturesImplementation(libs.groovyJson)
    testFixturesImplementation(project(":base-services"))

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-core"))
}

// Remove as part of fixing https://github.com/gradle/configuration-cache/issues/585
tasks.configCacheIntegTest {
    systemProperties["org.gradle.configuration-cache.internal.test-disable-load-after-store"] = "true"
}
