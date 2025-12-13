plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Logging API consumed by the Develocity plugin"

gradleModule {
    targetRuntimes {
        usedInWorkers = true
    }
}

dependencies {
    api(projects.buildOperations)
    api(projects.loggingApi)
    api(projects.stdlibJavaExtensions)

    api(libs.jspecify)
}

errorprone {
    nullawayEnabled = true
}
