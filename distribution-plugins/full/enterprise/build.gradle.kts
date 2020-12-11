plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    api("org.gradle:base-services") // leaks BuildOperationNotificationListener on API

    implementation(libs.jsr305)
    implementation(libs.inject)
    implementation("org.gradle:logging")
    implementation("org.gradle:core-api")
    implementation("org.gradle:core")
    implementation("org.gradle:launcher")
    implementation("org.gradle:snapshots")

    integTestImplementation("org.gradle:internal-testing")
    integTestImplementation("org.gradle:internal-integ-testing")

    // Dependencies of the integ test fixtures
    integTestImplementation("org.gradle:build-option")
    integTestImplementation("org.gradle:messaging")
    integTestImplementation("org.gradle:persistent-cache")
    integTestImplementation("org.gradle:native")
    integTestImplementation(libs.guava)

    integTestDistributionRuntimeOnly(project(":distributions-full"))
}
