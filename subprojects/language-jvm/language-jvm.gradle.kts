
plugins {
    gradlebuild.distribution.`api-java`
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":files"))
    implementation(project(":logging"))
    implementation(project(":processServices"))
    implementation(project(":fileCollections"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":workers"))
    implementation(project(":platformBase"))
    implementation(project(":platformJvm"))

    implementation(library("groovy")) // for 'Task.property(String propertyName) throws groovy.lang.MissingPropertyException'
    implementation(library("guava"))
    implementation(library("inject"))

    testImplementation(project(":native"))
    testImplementation(project(":resources"))
    testImplementation(project(":snapshots"))
    testImplementation(testFixtures(project(":core")))

    testFixturesImplementation(library("commons_lang"))
    testFixturesImplementation(library("guava"))
    testFixturesImplementation(project(":internalIntegTesting"))
    testFixturesImplementation(testFixtures(project(":core")))

    testRuntimeOnly(project(":distributionsCore")) {
        because("AbstractOptionsTest instantiates DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributionsCore"))
}
