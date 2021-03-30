plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "A set of generic data structures."

dependencies {
    implementation(project(":base-services"))
}
