plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Logging API consumed by the Develocity plugin"

gradlebuildJava.usedInWorkers()

dependencies {
    api(project(":logging-api"))

    implementation(project(":build-operations"))
    implementation(project(":base-annotations"))
    implementation(libs.jsr305)
}
