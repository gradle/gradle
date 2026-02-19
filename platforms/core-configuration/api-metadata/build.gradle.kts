plugins {
    id("gradlebuild.distribution.implementation-java")
    id("gradlebuild.api-metadata")
}

description = "Generated metadata about Gradle API needed by Kotlin DSL"

gradleModule {
    requiredRuntimes {
        client = true
        daemon = true
        worker = true
    }
    computedRuntimes {
    }
}

errorprone {
    nullawayEnabled = true
}
