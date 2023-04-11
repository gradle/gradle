plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Version control integration (with git) for source dependencies"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":messaging"))
    implementation(project(":logging"))
    implementation(project(":files"))
    implementation(project(":functional"))
    implementation(project(":file-collections"))
    implementation(project(":persistent-cache"))
    implementation(project(":core-api"))
    implementation(project(":core"))
    implementation(project(":resources"))
    implementation(project(":dependency-management"))

    implementation(libs.guava)
    implementation(libs.inject)
    implementation(libs.jgit)
    implementation(libs.jsch)

    testImplementation(project(":native"))
    testImplementation(project(":snapshots"))
    testImplementation(project(":process-services"))
    testImplementation(testFixtures(project(":core")))

    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":internal-integ-testing"))

    testFixturesImplementation(libs.jgit)
    testFixturesImplementation(libs.commonsIo)
    testFixturesImplementation(libs.commonsHttpclient)
    testFixturesImplementation(libs.jsch)
    testFixturesImplementation(libs.guava)

    integTestImplementation(project(":enterprise-operations"))
    integTestImplementation(project(":launcher"))
    integTestDistributionRuntimeOnly(project(":distributions-basics"))
}
