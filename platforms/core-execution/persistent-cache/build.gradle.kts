plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = """Persistent caches on disk and cross process locking.
    | Mostly for persisting Maps to the disk.
    | Also contains implementations for in-memory caches in front of the disk cache.
""".trimMargin()

dependencies {
    api(projects.buildOperations)
    api(projects.concurrent)
    api(projects.files)
    api(projects.logging) {
        because("Because GradleVersion temporarily lives in the logging project until 9.0")
    }
    api(projects.messaging)
    api(projects.serialization)
    api(projects.stdlibJavaExtensions)

    api(libs.jspecify)

    implementation(projects.classloaders)
    implementation(projects.buildProcessServices)
    implementation(projects.functional)
    implementation(projects.io)
    implementation(projects.time)

    implementation(libs.guava)
    implementation(libs.jsr305)
    implementation(libs.slf4jApi)
    implementation(libs.commonsIo)
    implementation(libs.commonsLang)

    testImplementation(projects.messaging)
    testImplementation(projects.coreApi)
    testImplementation(projects.functional)
    testImplementation(testFixtures(projects.core))

    testRuntimeOnly(projects.distributionsCore) {
        because("DefaultPersistentDirectoryCacheTest instantiates DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }

    integTestImplementation(projects.messaging)

    integTestDistributionRuntimeOnly(projects.distributionsCore)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
