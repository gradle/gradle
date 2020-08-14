plugins {
    id("gradlebuild.distribution.api-java")
}

description = "JVM invocation and inspection abstractions"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":process-services"))

    testImplementation(project(":native"))
    testImplementation(project(":core-api"))
    testImplementation(project(":file-collections"))
    testImplementation(project(":snapshots"))
    testImplementation(project(":resources"))
    testImplementation(libs.slf4jApi)
    testImplementation(testFixtures(project(":core")))
}
