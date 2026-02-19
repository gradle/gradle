plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implementations for TAPI native models"

dependencies {
    api(projects.ideModelImpls)
    api(projects.toolingApi)
}

gradleModule {
    computedRuntimes {
    }
}

