plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Marker class file used to locate the Gradle distribution base directory"

// This lib should not have any dependencies.
tasks.isolatedProjectsIntegTest {
    enabled = false
}
