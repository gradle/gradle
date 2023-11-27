plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Infrastructure that bootstraps a worker process"

gradlebuildJava.usedInWorkers()

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":messaging"))
    implementation(project(":native"))
    implementation(project(":process-services"))
    implementation(project(":problems-api"))

    testImplementation(testFixtures(project(":core")))
}
