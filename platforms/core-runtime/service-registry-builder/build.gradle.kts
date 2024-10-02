plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Internal API for composing service registries"

gradlebuildJava.usedInWorkers()

dependencies {
    api(projects.serviceLookup)
    api(projects.serviceProvider)
    api(projects.stdlibJavaExtensions)

    implementation(projects.serviceRegistryImpl)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
