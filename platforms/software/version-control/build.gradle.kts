plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.instrumented-java-project")
}

description = "Version control integration (with git) for source dependencies"

errorprone {
    disabledChecks.addAll(
        "UnusedVariable", // 3 occurrences
    )
}

dependencies {
    api(projects.concurrent)
    api(projects.serviceProvider)
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.dependencyManagement)
    api(projects.fileCollections)

    api(libs.jgit)
    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.stdlibJavaExtensions)
    implementation(projects.persistentCache)
    implementation(projects.serialization)
    implementation(projects.files)
    implementation(projects.functional)
    implementation(projects.hashing)
    implementation(projects.loggingApi)

    implementation(libs.guava)
    implementation(libs.jgitSsh) {
        exclude("org.apache.sshd", "sshd-osgi") // Because it duplicates sshd-core and sshd-commons contents
    }

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
