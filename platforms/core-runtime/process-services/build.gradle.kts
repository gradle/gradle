plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Process execution abstractions."

gradleModule {
    targetRuntimes {
        usedInClient = true
        usedInDaemon = true
        usedInWorkers = true
    }
}

dependencies {
    api(projects.concurrent)
    api(projects.baseServices)
    api(projects.coreApi)

    api(libs.jspecify)

    testImplementation(testFixtures(projects.core))

    integTestDistributionRuntimeOnly(projects.distributionsCore)
}

packageCycles {
    excludePatterns.add("org/gradle/process/internal/**")
}
