plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Public API for extending the build cache"

gradleModule {
    targetRuntimes {
        usedInClient = true
        usedInDaemon = true
        usedInWorkers = true
    }
}

dependencies {
    implementation(libs.jspecify)

    integTestImplementation(projects.logging)
    integTestDistributionRuntimeOnly(projects.distributionsCore)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
