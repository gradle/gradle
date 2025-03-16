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
    api(projects.persistentCache)
    api(projects.serviceProvider)
    api(projects.stdlibJavaExtensions)

    api(libs.jgit)
    api(libs.inject)
    api(libs.jspecify)

    implementation(projects.serialization)
    implementation(projects.files)
    implementation(projects.functional)
    implementation(projects.hashing)
    implementation(projects.loggingApi)

    implementation(libs.guava)
    implementation(libs.jgitSsh) {
        exclude("org.apache.sshd", "sshd-osgi") // Because it duplicates sshd-core and sshd-commons contents
    }
    implementation(libs.jsr305)

    testImplementation(projects.native)
    testImplementation(projects.snapshots)
    testImplementation(projects.processServices)
    testImplementation(testFixtures(projects.core))

    testFixturesImplementation(projects.baseServices)
    testFixturesImplementation(projects.internalIntegTesting)

    testFixturesImplementation(libs.jgit)
    testFixturesImplementation(libs.jgitSsh) {
        exclude("org.apache.sshd", "sshd-osgi") // Because it duplicates sshd-core and sshd-commons contents
    }
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
