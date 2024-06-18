plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Logging API consumed by the Develocity plugin"

gradlebuildJava.usedInWorkers()

dependencies {
    api(project(":build-operations"))
    api(project(":logging-api"))
    api(project(":stdlib-java-extensions"))

    api(libs.jsr305)
}
