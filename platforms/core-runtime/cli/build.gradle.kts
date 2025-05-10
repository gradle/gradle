plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Utilities for parsing command line arguments"

gradleModule {
    targetRuntimes {
        usedInWrapper = true
    }
}

jvmCompile {
    usesIncompatibleDependencies = true // For test dependencies
}

