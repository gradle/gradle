plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    api(project(":base-services"))
    api(project(":enterprise-operations"))

    implementation(libs.jsr305)
    implementation(libs.inject)
    implementation(project(":logging"))
    implementation(project(":core-api"))
    implementation(project(":build-option"))
    implementation(project(":core"))
    implementation(project(":launcher"))
    implementation(project(":snapshots"))

    testImplementation(project(":resources"))

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
