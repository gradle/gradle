plugins {
    gradlebuild.distribution.`api-java`
}

description = "JVM invocation and inspection abstractions"

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":processServices"))

    testImplementation(project(":native"))
    testImplementation(project(":coreApi"))
    testImplementation(project(":fileCollections"))
    testImplementation(project(":snapshots"))
    testImplementation(project(":resources"))
    testImplementation(library("slf4j_api"))
    testImplementation(testFixtures(project(":core")))
}
