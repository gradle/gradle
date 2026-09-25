plugins {
    id("java-library")
}

repositories {
    mavenCentral()
}

// tag::detached[]
val detached = configurations.detachedConfiguration(dependencies.create("group:name:1.0"))
detached.resolutionStrategy.disableDependencyVerification()
// end::detached[]
