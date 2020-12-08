plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":base-services-groovy"))
    implementation(project(":core-api"))
    implementation(project(":files"))
    implementation(project(":model-core"))
    implementation(project(":logging"))
    implementation(project(":native"))

    implementation(libs.slf4jApi)
    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.inject)

    testImplementation(project(":process-services"))
    testImplementation(project(":resources"))
    testImplementation(project(":snapshots"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":core-api")))
    testImplementation(testFixtures(project(":model-core")))

    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":core-api"))
    testFixturesImplementation(project(":native"))

    testFixturesImplementation(libs.guava)

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-core"))
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
}

classycle {
    // Some cycles have been inherited from the time these classes were in :core
    excludePatterns.add("org/gradle/api/internal/file/collections/")
}
