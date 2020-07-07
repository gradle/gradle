plugins {
    id("gradlebuild.distribution.api-java")
}

description = "A set of general-purpose resource abstractions"

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":files"))
    implementation(project(":messaging"))
    implementation(project(":native"))

    implementation(libs.slf4j_api)
    implementation(libs.guava)
    implementation(libs.commons_io)

    testImplementation(project(":processServices"))
    testImplementation(project(":coreApi"))
    testImplementation(project(":fileCollections"))
    testImplementation(project(":snapshots"))

    testImplementation(testFixtures(project(":core")))

    integTestDistributionRuntimeOnly(project(":distributionsCore"))
}
