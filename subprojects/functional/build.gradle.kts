plugins {
    id("gradlebuild.distribution.implementation-java")
    id("gradlebuild.publish-public-libraries")
    id("gradlebuild.jmh")
}

description = "Tools to work with functional code, including data structures"

dependencies {
    implementation(project(":base-annotations"))
    implementation(libs.capsule)
}

jmh.includes.set(listOf("AtomicHashSetBenchmark"))
