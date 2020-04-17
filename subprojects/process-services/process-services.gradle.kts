/**
 * Process execution abstractions.
 */
plugins {
    gradlebuild.distribution.`core-api-java`
}

gradlebuildJava.usedInWorkers()

dependencies {
    implementation(project(":baseServices"))

    implementation(project(":messaging"))
    implementation(project(":native"))

    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("nativePlatform"))

    testImplementation(testFixtures(project(":core")))

    integTestRuntimeOnly(project(":runtimeApiInfo"))
}
