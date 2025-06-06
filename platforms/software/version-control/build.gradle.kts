plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Version control integration (with git) for source dependencies"

dependencies {
    api(projects.baseServices)
    api(projects.concurrent)
    api(projects.core)
    api(projects.coreApi)
    api(projects.dependencyManagement)
    api(projects.fileCollections)
    api(projects.scopedPersistentCache)
    api(projects.serviceProvider)
    api(projects.stdlibJavaExtensions)

    api(libs.jgit)
    api(libs.inject)
    api(libs.jspecify)

    implementation(projects.files)
    implementation(projects.functional)
    implementation(projects.hashing)
    implementation(projects.loggingApi)
    implementation(projects.persistentCache)
    implementation(projects.serialization)

    implementation(libs.guava)
    implementation(libs.jgitSsh)
    implementation(libs.jsr305)

    runtimeOnly(libs.jgitSshAgent)

    testImplementation(projects.native)
    testImplementation(projects.snapshots)
    testImplementation(projects.processServices)
    testImplementation(testFixtures(projects.core))

    testFixturesImplementation(projects.baseServices)
    testFixturesImplementation(projects.internalIntegTesting)

    testFixturesImplementation(libs.jgit)
    testFixturesImplementation(libs.jgitSsh)
    testFixturesImplementation(libs.commonsIo)
    testFixturesImplementation(libs.commonsHttpclient)
    testFixturesImplementation(libs.guava)

    integTestImplementation(projects.enterpriseOperations)
    integTestImplementation(projects.launcher)
    integTestDistributionRuntimeOnly(projects.distributionsBasics)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
