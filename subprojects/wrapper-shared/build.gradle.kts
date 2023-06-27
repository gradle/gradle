plugins {
    id("gradlebuild.distribution.api-java")
    // TODO: Need to publish the ZipSlip helper class
    id("gradlebuild.publish-public-libraries")
}

description = "Utility code shared between the wrapper and the Gradle distribution"

gradlebuildJava.usedInWorkers()

dependencies {

    testImplementation(project(":base-services"))
    testImplementation(project(":core-api"))
    testImplementation(project(":native"))

    integTestImplementation(project(":dependency-management"))

    integTestDistributionRuntimeOnly(project(":distributions-full"))
}
