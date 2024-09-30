plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Build operations consumed by the Develocity plugin"

dependencies {
    api(projects.buildOperations)

    api(libs.jsr305)

    implementation(projects.stdlibJavaExtensions)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
