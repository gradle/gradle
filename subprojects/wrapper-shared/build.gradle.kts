plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Utility code shared between the wrapper and the Gradle distribution"

gradlebuildJava.usedInWorkers()

dependencies {

    testImplementation(project(":base-services"))
    testImplementation(project(":core-api"))
    testImplementation(project(":native"))
    testImplementation(libs.commonsCompress)

    integTestImplementation(project(":dependency-management"))

    integTestDistributionRuntimeOnly(project(":distributions-full"))
}
