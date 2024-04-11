plugins {
    id("gradlebuild.distribution.implementation-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Build operations events"

gradlebuildJava.usedInWorkers()

dependencies {
    api(libs.jsr305)
    api(project(":base-annotations"))
    api(project(":build-operations"))
    api(project(":problems-api"))
}
