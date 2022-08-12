plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Gradle Enterprise plugin dependencies that also need to be exposed to workers"

gradlebuildJava.usedInWorkers()

dependencies {
    implementation(project(":base-annotations"))
    implementation(libs.jsr305)
}
