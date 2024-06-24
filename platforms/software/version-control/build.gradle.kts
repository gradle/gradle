plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Version control integration (with git) for source dependencies"

errorprone {
    disabledChecks.addAll(
        "UnusedMethod", // 13 occurrences
        "UnusedVariable", // 3 occurrences
    )
}

dependencies {
    api(projects.concurrent)
    api(projects.serviceProvider)
    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":dependency-management"))
    api(project(":file-collections"))

    api(libs.jgit)
    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.javaLanguageExtensions)
    implementation(projects.persistentCache)
    implementation(projects.serialization)
    implementation(project(":files"))
    implementation(project(":functional"))
    implementation(project(":hashing"))
    implementation(project(":logging-api"))

    implementation(libs.guava)
    implementation(libs.jgitSsh) {
        exclude("org.apache.sshd", "sshd-osgi") // Because it duplicates sshd-core and sshd-commons contents
    }

    testImplementation(project(":native"))
    testImplementation(project(":snapshots"))
    testImplementation(project(":process-services"))
    testImplementation(testFixtures(project(":core")))

    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":internal-integ-testing"))

    testFixturesImplementation(libs.jgit)
    testFixturesImplementation(libs.jgitSsh) {
        exclude("org.apache.sshd", "sshd-osgi") // Because it duplicates sshd-core and sshd-commons contents
    }
    testFixturesImplementation(libs.commonsIo)
    testFixturesImplementation(libs.commonsHttpclient)
    testFixturesImplementation(libs.guava)

    integTestImplementation(project(":enterprise-operations"))
    integTestImplementation(project(":launcher"))
    integTestDistributionRuntimeOnly(project(":distributions-basics"))
}
