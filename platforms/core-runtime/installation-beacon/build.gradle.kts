plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Marker class file used to locate the Gradle distribution base directory"

gradleModule {
    entryPoint = true

    targetRuntimes {
        usedInClient = true
        usedInDaemon = true
        usedInWorkers = true
    }
}

// Installation beacon should not be part of the public API
// TODO Find a way to not register this and the task instead
configurations.remove(configurations.apiStubElements.get())

// This lib should not have any dependencies.
