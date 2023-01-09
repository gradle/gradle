plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Utility code shared between the wrapper and the Gradle distribution"

gradlebuildJava.usedInWorkers()

dependencies {

    testImplementation(project(":base-services"))
    testImplementation(project(":core-api"))
    testImplementation(project(":native"))
    testImplementation("org.apache.commons:commons-compress:1.21")

    integTestImplementation(project(":dependency-management"))

    integTestDistributionRuntimeOnly(project(":distributions-full"))
}
