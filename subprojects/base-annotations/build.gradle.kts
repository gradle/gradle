plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Common shared annotations"

gradlebuildJava.usedInWorkers()

dependencies {
    api(libs.jsr305)
    api(libs.jetbrainsAnnotations)
}
