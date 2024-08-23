plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Implementation of the service registry framework"

gradlebuildJava.usedInWorkers()

dependencies {
    api(projects.serviceLookup)
    api(projects.serviceProvider)
    api(projects.stdlibJavaExtensions)

    api(libs.jsr305)

    implementation(projects.concurrent)

    implementation(libs.inject)
}

tasks.isolatedProjectsIntegTest {
    enabled = false
}
