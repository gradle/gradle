plugins {
    id("gradlebuild.distribution.implementation-java")
    id("gradlebuild.dependency-scanner")
}

description = "Infrastructure that bootstraps a worker process"

gradlebuildJava.usedInWorkers()

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":messaging"))
    implementation(project(":native"))
    implementation(project(":process-services"))

    testImplementation(testFixtures(project(":core")))
}
