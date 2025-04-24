plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Utilities for parsing command line arguments"

gradlebuildJava {
    usedForStartup() // Used in the wrapper
    usesIncompatibleDependencies = true // For test dependencies
}

