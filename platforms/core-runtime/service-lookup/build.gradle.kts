plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Internal API to dynamically lookup services provided by Gradle modules"

gradlebuildJava.usedInWorkers()

dependencies {
    api(projects.stdlibJavaExtensions)

    api(libs.jsr305)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
