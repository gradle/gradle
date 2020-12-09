plugins {
    id("gradlebuild.distribution.api-java")
}

description = "JVM invocation and inspection abstractions"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":core-api"))
    implementation(project(":file-temp"))
    implementation(project(":process-services"))
    implementation(project(":logging"))
    implementation(libs.inject)
    implementation(libs.nativePlatform)
    implementation(libs.guava)
    implementation(libs.asm)

    testImplementation(project(":native"))
    testImplementation(project(":file-collections"))
    testImplementation(project(":snapshots"))
    testImplementation(project(":resources"))
    testImplementation(libs.slf4jApi)
    testImplementation(testFixtures(project(":core")))

    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
