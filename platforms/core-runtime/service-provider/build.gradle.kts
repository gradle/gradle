plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Internal API to declare services provided by Gradle modules"

gradlebuildJava.usedInWorkers()

dependencies {
    api(projects.serviceLookup)
    api(projects.stdlibJavaExtensions)

    api(libs.jsr305)
    api(libs.errorProneAnnotations)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
