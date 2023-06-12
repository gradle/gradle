plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Utility code shared between the wrapper and the Gradle distribution"

gradlebuildJava.usedInWorkers()

dependencies {

    compileOnly(project(":base-annotations")) {
        because("Compile only because we want to keep the wrapper.jar small")
    }
    testImplementation(project(":base-services"))
    testImplementation(project(":core-api"))
    testImplementation(project(":native"))
    testImplementation(libs.commonsCompress)

    integTestImplementation(project(":dependency-management"))
    integTestImplementation(project(":logging"))

    integTestDistributionRuntimeOnly(project(":distributions-full"))
}
