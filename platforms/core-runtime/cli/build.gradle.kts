plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Utilities for parsing command line arguments"

gradleModule {
    targetRuntimes {
        usedInClient = true
        usedInDaemon = true
        usedInWorkers = true
    }
}

jvmCompile {
    usesIncompatibleDependencies = true // For test dependencies
}

