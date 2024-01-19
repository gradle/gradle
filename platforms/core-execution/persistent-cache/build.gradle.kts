plugins {
    id("gradlebuild.distribution.api-java")
}

description = """Persistent caches on disk and cross process locking.
    | Mostly for persisting Maps to the disk.
    | Also contains implementations for in-memory caches in front of the disk cache.
""".trimMargin()

dependencies {
    api(project(":base-annotations"))
    api(project(":enterprise-logging"))
    api(project(":base-services"))
    api(project(":messaging"))
    api(project(":native"))
    api(project(":files"))
    api(project(":logging"))

    api(libs.jsr305)
    api(libs.guava)

    implementation(project(":build-operations"))

    implementation(libs.slf4jApi)
    implementation(libs.commonsIo)
    implementation(libs.commonsLang)

    testImplementation(project(":core-api"))
    testImplementation(project(":functional"))
    testImplementation(testFixtures(project(":core")))

    testRuntimeOnly(project(":distributions-core")) {
        because("DefaultPersistentDirectoryCacheTest instantiates DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
