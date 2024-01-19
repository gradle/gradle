plugins {
    id("gradlebuild.distribution.api-java")
}

description = "JVM invocation and inspection abstractions"

dependencies {
    api(project(":logging-api"))
    api(project(":base-annotations"))
    api(project(":base-services"))
    api(project(":core-api"))
    api(project(":enterprise-logging"))
    api(project(":file-temp"))
    api(project(":file-collections"))
    api(project(":process-services"))

    api(libs.inject)
    api(libs.jsr305)
    api(libs.nativePlatform)

    implementation(project(":build-operations"))
    implementation(project(":functional"))

    implementation(libs.guava)
    implementation(libs.asm)
    implementation(libs.xmlApis)
    implementation(libs.slf4jApi)

    testImplementation(project(":native"))
    testImplementation(project(":file-collections"))
    testImplementation(project(":snapshots"))
    testImplementation(project(":resources"))
    testImplementation(libs.slf4jApi)
    testImplementation(testFixtures(project(":core")))

    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
