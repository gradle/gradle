plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":messaging"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":workerProcesses"))
    implementation(project(":persistentCache"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":snapshots"))
    implementation(project(":fileCollections"))
    implementation(project(":files"))
    implementation(project(":native"))
    implementation(project(":resources"))

    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.inject)

    testImplementation(project(":native"))
    testImplementation(project(":fileCollections"))
    testImplementation(project(":resources"))
    testImplementation(project(":snapshots"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":logging")))

    integTestRuntimeOnly(project(":kotlinDsl"))
    integTestRuntimeOnly(project(":kotlin-dsl-provider-plugins"))
    integTestRuntimeOnly(project(":api-metadata"))
    integTestRuntimeOnly(project(":testKit"))

    integTestImplementation(project(":jvm-services"))

    testFixturesImplementation(libs.inject)
    testFixturesImplementation(project(":base-services"))

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
