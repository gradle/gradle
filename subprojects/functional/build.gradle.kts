plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Tools to work with functional code, including data structures"

dependencies {
    implementation(project(":base-annotations"))
}
