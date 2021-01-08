plugins {
    id("gradlebuild.distribution.api-java")
}

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
    testImplementation(testFixtures(project(":core")))

    testRuntimeOnly(project(":distributions-core")) {
        because("DefaultPersistentDirectoryCacheTest instantiates DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
