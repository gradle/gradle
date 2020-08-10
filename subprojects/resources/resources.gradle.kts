plugins {
    id("gradlebuild.distribution.api-java")
}

description = "A set of general-purpose resource abstractions"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":files"))
    implementation(project(":messaging"))
    implementation(project(":native"))

    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.commonsIo)

    testImplementation(project(":process-services"))
    testImplementation(project(":coreApi"))
    testImplementation(project(":fileCollections"))
    testImplementation(project(":snapshots"))

    testImplementation(testFixtures(project(":core")))

    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
