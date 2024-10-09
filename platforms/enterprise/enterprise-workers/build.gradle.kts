plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Develocity plugin dependencies that also need to be exposed to workers"

gradlebuildJava.usedInWorkers()

dependencies {
    api(libs.jsr305)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
