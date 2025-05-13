plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Logging API consumed by the Develocity plugin"

gradleModule {
    targetRuntimes {
        usedInClient = true
        usedInDaemon = true
        usedInWorkers = true
    }
}

dependencies {
    api(projects.buildOperations)
    api(projects.loggingApi)
    api(projects.stdlibJavaExtensions)

    api(libs.jspecify)
}
