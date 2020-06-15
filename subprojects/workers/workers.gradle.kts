plugins {
    gradlebuild.distribution.`api-java`
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":messaging"))
    implementation(project(":logging"))
    implementation(project(":processServices"))
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

    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("inject"))

    testImplementation(project(":native"))
    testImplementation(project(":fileCollections"))
    testImplementation(project(":resources"))
    testImplementation(project(":snapshots"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":logging")))

    integTestRuntimeOnly(project(":kotlinDsl"))
    integTestRuntimeOnly(project(":kotlinDslProviderPlugins"))
    integTestRuntimeOnly(project(":apiMetadata"))
    integTestRuntimeOnly(project(":testKit"))

    integTestImplementation(project(":jvmServices"))

    testFixturesImplementation(library("inject"))
    testFixturesImplementation(project(":baseServices"))

    testRuntimeOnly(project(":distributionsCore")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributionsCore"))
}
