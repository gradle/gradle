plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Internal API for composing service registries"

gradleModule {
    targetRuntimes {
        usedInClient = true
        usedInDaemon = true
        usedInWorkers = true
    }
}

dependencies {
    api(projects.serviceLookup)
    api(projects.serviceProvider)
    api(projects.stdlibJavaExtensions)

    implementation(projects.serviceRegistryImpl)
}
