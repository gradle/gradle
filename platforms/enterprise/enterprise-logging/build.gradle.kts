plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Logging API consumed by the Develocity plugin"

gradlebuildJava.usedInWorkers()

dependencies {
    api(projects.buildOperations)
    api(projects.loggingApi)
    api(projects.stdlibJavaExtensions)

    api(libs.jsr305)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
