plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Configuration input discovery code"

gradleModule {
    targetRuntimes {
        usedInClient = true
        usedInDaemon = true
        usedInWorkers = true
    }
}

dependencies {
    api(libs.jspecify)
    api(libs.guava)

    integTestDistributionRuntimeOnly(projects.distributionsCore)
}
