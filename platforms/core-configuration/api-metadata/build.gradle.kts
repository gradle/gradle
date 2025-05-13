plugins {
    id("gradlebuild.distribution.implementation-java")
    id("gradlebuild.api-metadata")
}

description = "Generated metadata about Gradle API needed by Kotlin DSL"

gradleModule {
    entryPoint = true

    targetRuntimes {
        // Runtimes do not really matter. This project has no java code.
        usedInClient = true
        usedInDaemon = true
        usedInWorkers = true
    }
}
