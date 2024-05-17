plugins {
    id("gradlebuild.distribution.api-java")
}

description = "JVM invocation and inspection abstractions"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":core-api"))
    implementation(project(":file-temp"))
    implementation(project(":file-collections"))
    implementation(project(":functional"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(libs.inject)
    implementation(libs.nativePlatform)
    implementation(libs.guava)
    implementation(libs.asm)
    implementation(libs.xmlApis)

    testImplementation(project(":native"))
    testImplementation(project(":file-collections"))
    testImplementation(project(":snapshots"))
    testImplementation(project(":resources"))
    testImplementation(libs.slf4jApi)
    testImplementation(testFixtures(project(":core")))

    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
