plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Contains classes for instrumentation reporting, e.g. bytecode upgrades. " +
    "Note: For Configuration cache reporting see configuration-cache/input-tracking project."

gradleModule {
    targetRuntimes {
        usedInClient = true
        usedInDaemon = true
        usedInWorkers = true
    }
}

dependencies {
    api(projects.internalInstrumentationApi)
    api(projects.stdlibJavaExtensions)

    api(libs.jspecify)

    integTestDistributionRuntimeOnly(projects.distributionsJvm)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
