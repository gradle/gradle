plugins {
    id("gradlebuild.distribution.implementation-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Tools to work with functional code, including data structures"

dependencies {
    api(libs.jsr305)

    implementation(projects.stdlibJavaExtensions)
    implementation(libs.guava)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
