plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Logging API consumed by the Develocity plugin"

gradlebuildJava.usedInWorkers()

dependencies {
    api(project(":logging-api"))
    api(project(":build-operations"))

    api(libs.jsr305)
}
