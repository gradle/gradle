plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Utilities for parsing command line arguments"

gradleModule {
    usedInWrapper = true
    usesIncompatibleDependencies = true // For test dependencies
}

