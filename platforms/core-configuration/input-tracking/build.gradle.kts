plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Configuration input discovery code"

dependencies {
    api(libs.jsr305)
    api(libs.guava)

    implementation(projects.stdlibJavaExtensions)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
