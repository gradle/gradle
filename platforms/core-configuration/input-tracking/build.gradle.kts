plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Configuration input discovery code"

dependencies {
    implementation(project(":base-annotations"))
    implementation(libs.guava)

    testImplementation(libs.guava)
}
