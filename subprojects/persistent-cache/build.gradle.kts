plugins {
    id("gradlebuild.distribution.api-java")
}

description = """Persistent caches on disk and cross process locking.
    | Mostly for persisting Maps to the disk.
    | Also contains implementations for in-memory caches in front of the disk cache.
""".trimMargin()

dependencies {
    implementation(project(":base-services"))
    implementation(project(":messaging"))
    implementation(project(":native"))
    implementation(project(":files"))
    implementation(project(":resources"))
    implementation(project(":logging"))

    implementation(libs.slf4jApi)
    implementation(libs.guava)
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
