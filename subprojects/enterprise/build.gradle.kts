plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    api(project(":base-services")) // leaks BuildOperationNotificationListener on API

    implementation(libs.jsr305)
    implementation(libs.inject)
    implementation(project(":logging"))
    implementation(project(":core-api"))
    implementation(project(":core"))
    implementation(project(":launcher"))
    implementation(project(":snapshots"))

    integTestImplementation(project(":internal-testing"))
    integTestImplementation(project(":internal-integ-testing"))

    // Dependencies of the integ test fixtures
    integTestImplementation(project(":build-option"))
    integTestImplementation(project(":messaging"))
    integTestImplementation(project(":persistent-cache"))
    integTestImplementation(project(":native"))
    integTestImplementation(libs.guava)

    integTestDistributionRuntimeOnly(project(":distributions-full"))
}
