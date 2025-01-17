plugins {
    id("gradlebuild.distribution.implementation-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Tools to work with functional code, including data structures"

dependencies {
    api(libs.jsr305)
    api(projects.stdlibJavaExtensions)

    implementation(libs.guava)
    implementation(libs.fastutil)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
